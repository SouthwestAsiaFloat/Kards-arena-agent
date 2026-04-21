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
RESULT_QUEUE = os.getenv("OCR_RESULT_QUEUE", "arena.ocr.results")
REQUEST_ROUTING_KEY = os.getenv("OCR_REQUEST_ROUTING_KEY", "ocr.request")
RESULT_ROUTING_KEY = os.getenv("OCR_RESULT_ROUTING_KEY", "ocr.result")


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
    )


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
    except Exception as exc:
        logger.exception("OCR job %s failed", job_id or "<unknown>")
        publish_result(
            channel,
            {
                "jobId": job_id,
                "success": False,
                "resultJson": None,
                "errorMessage": str(exc),
            },
        )
    finally:
        channel.basic_ack(delivery_tag=method.delivery_tag)


def main():
    init_ocr_engine()

    connection = pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))
    channel = connection.channel()
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)
    channel.queue_declare(queue=REQUEST_QUEUE, durable=True)
    channel.queue_declare(queue=RESULT_QUEUE, durable=True)
    channel.queue_bind(exchange=EXCHANGE, queue=REQUEST_QUEUE, routing_key=REQUEST_ROUTING_KEY)
    channel.queue_bind(exchange=EXCHANGE, queue=RESULT_QUEUE, routing_key=RESULT_ROUTING_KEY)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=REQUEST_QUEUE, on_message_callback=handle_message)

    logger.info("OCR RabbitMQ worker started. queue=%s", REQUEST_QUEUE)
    channel.start_consuming()


if __name__ == "__main__":
    main()
