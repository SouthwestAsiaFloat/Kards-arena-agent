import base64
import json
import logging
import os
from typing import Any

import cv2
import numpy as np
import pika

from core.analyze_service import analyze_image
from core.ocr_runner import init_ocr_engine

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@127.0.0.1:5672/%2F")
EXCHANGE = os.getenv("OCR_MQ_EXCHANGE", "arena.ocr")
REQUEST_QUEUE = os.getenv("OCR_REQUEST_QUEUE", "arena.ocr.requests")
RETRY_QUEUE = os.getenv("OCR_RETRY_QUEUE", "arena.ocr.requests.retry")
DEAD_QUEUE = os.getenv("OCR_DEAD_QUEUE", "arena.ocr.requests.dead")
RESULT_QUEUE = os.getenv("OCR_RESULT_QUEUE", "arena.ocr.results")
REQUEST_ROUTING_KEY = os.getenv("OCR_REQUEST_ROUTING_KEY", "ocr.request")
RETRY_ROUTING_KEY = os.getenv("OCR_RETRY_ROUTING_KEY", "ocr.request.retry")
DEAD_ROUTING_KEY = os.getenv("OCR_DEAD_ROUTING_KEY", "ocr.request.dead")
RESULT_ROUTING_KEY = os.getenv("OCR_RESULT_ROUTING_KEY", "ocr.result")
MAX_RETRIES = int(os.getenv("OCR_WORKER_MAX_RETRIES", "2"))
RETRY_DELAY_SECONDS = float(os.getenv("OCR_WORKER_RETRY_DELAY_SECONDS", "2"))
RETRY_DELAY_MS = max(0, int(RETRY_DELAY_SECONDS * 1000))


def decode_image(image_base64: str):
    image_bytes = base64.b64decode(image_base64)
    nparr = np.frombuffer(image_bytes, np.uint8)
    return cv2.imdecode(nparr, cv2.IMREAD_COLOR)


def publish_result(channel, payload: dict[str, Any]):
    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=RESULT_ROUTING_KEY,
        body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=2,
        ),
        mandatory=True,
    )


def get_retry_count(properties) -> int:
    headers = getattr(properties, "headers", None) or {}
    try:
        return int(headers.get("x-retry-count", 0))
    except (TypeError, ValueError):
        return 0


def publish_retry(channel, properties, body: bytes, retry_count: int):
    headers = dict(getattr(properties, "headers", None) or {})
    headers["x-retry-count"] = retry_count + 1

    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=RETRY_ROUTING_KEY,
        body=body,
        properties=pika.BasicProperties(
            content_type=getattr(properties, "content_type", None) or "application/json",
            delivery_mode=2,
            headers=headers,
        ),
        mandatory=True,
    )


def publish_dead(channel, properties, body: bytes, reason: str):
    headers = dict(getattr(properties, "headers", None) or {})
    headers["x-dead-reason"] = reason

    channel.basic_publish(
        exchange=EXCHANGE,
        routing_key=DEAD_ROUTING_KEY,
        body=body,
        properties=pika.BasicProperties(
            content_type=getattr(properties, "content_type", None) or "application/json",
            delivery_mode=2,
            headers=headers,
        ),
        mandatory=True,
    )


def should_retry(exc: Exception, job_id: str | None) -> bool:
    if job_id is None:
        return False
    return not isinstance(exc, ValueError)


def handle_message(channel, method, properties, body):
    job_id = None
    try:
        payload = json.loads(body.decode("utf-8"))
        job_id = payload.get("jobId")
        if not job_id:
            raise ValueError("Missing jobId")

        image = decode_image(payload.get("imageBase64", ""))
        if image is None:
            raise ValueError("Image decode failed")

        logger.info("Processing OCR job %s", job_id)
        result = analyze_image(image)
        publish_result(
            channel,
            {
                "jobId": job_id,
                "success": True,
                "resultJson": json.dumps(result, ensure_ascii=False),
                "errorMessage": None,
            },
        )
        logger.info("OCR job %s completed", job_id)
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as exc:
        retry_count = get_retry_count(properties)
        if should_retry(exc, job_id) and retry_count < MAX_RETRIES:
            logger.warning(
                "OCR job %s failed, retrying %s/%s: %s",
                job_id,
                retry_count + 1,
                MAX_RETRIES,
                exc,
            )
            try:
                publish_retry(channel, properties, body, retry_count)
                channel.basic_ack(delivery_tag=method.delivery_tag)
            except Exception:
                logger.exception("Failed to republish OCR job %s for retry", job_id)
                channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            return

        if job_id is None:
            logger.exception("Malformed OCR job moved to dead queue")
            try:
                publish_dead(channel, properties, body, str(exc))
                channel.basic_ack(delivery_tag=method.delivery_tag)
            except Exception:
                logger.exception("Failed to publish malformed OCR job to dead queue")
                channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            return

        logger.exception("OCR job %s failed permanently", job_id or "<unknown>")
        try:
            publish_result(
                channel,
                {
                    "jobId": job_id,
                    "success": False,
                    "resultJson": None,
                    "errorMessage": str(exc),
                },
            )
        except Exception:
            logger.exception("Failed to publish OCR failure result for job %s", job_id)
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            return

        channel.basic_ack(delivery_tag=method.delivery_tag)


def main():
    init_ocr_engine()

    connection = pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))
    channel = connection.channel()
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)
    channel.queue_declare(queue=REQUEST_QUEUE, durable=True)
    channel.queue_declare(
        queue=RETRY_QUEUE,
        durable=True,
        arguments={
            "x-message-ttl": RETRY_DELAY_MS,
            "x-dead-letter-exchange": EXCHANGE,
            "x-dead-letter-routing-key": REQUEST_ROUTING_KEY,
        },
    )
    channel.queue_declare(queue=DEAD_QUEUE, durable=True)
    channel.queue_declare(queue=RESULT_QUEUE, durable=True)
    channel.queue_bind(exchange=EXCHANGE, queue=REQUEST_QUEUE, routing_key=REQUEST_ROUTING_KEY)
    channel.queue_bind(exchange=EXCHANGE, queue=RETRY_QUEUE, routing_key=RETRY_ROUTING_KEY)
    channel.queue_bind(exchange=EXCHANGE, queue=DEAD_QUEUE, routing_key=DEAD_ROUTING_KEY)
    channel.queue_bind(exchange=EXCHANGE, queue=RESULT_QUEUE, routing_key=RESULT_ROUTING_KEY)
    channel.confirm_delivery()
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=REQUEST_QUEUE, on_message_callback=handle_message)

    logger.info("OCR RabbitMQ worker started. queue=%s", REQUEST_QUEUE)
    channel.start_consuming()


if __name__ == "__main__":
    main()
