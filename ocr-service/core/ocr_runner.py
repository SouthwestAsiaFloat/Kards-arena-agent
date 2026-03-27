from __future__ import annotations

import logging
import threading
from typing import Any, List

import cv2
import numpy as np

_ocr_engine = None
_ocr_engine_lock = threading.Lock()
_logger = logging.getLogger(__name__)


def _is_unknown_arg_error(exc: Exception) -> bool:
    message = str(exc)
    return "Unknown argument" in message or "unexpected keyword argument" in message


def _create_ocr_engine() -> Any:
    try:
        from paddleocr import PaddleOCR
    except Exception as exc:
        raise RuntimeError("PaddleOCR is not installed. Please install paddleocr first.") from exc

    # 固定 CPU 模式，只做不同 PaddleOCR 版本的参数兼容。
    attempts = (
        {"lang": "ch", "device": "cpu"},
        {"lang": "ch", "use_gpu": False},
        {"lang": "ch"},
    )

    last_exc: Exception | None = None
    for kwargs in attempts:
        try:
            return PaddleOCR(**kwargs)
        except TypeError as exc:
            if _is_unknown_arg_error(exc):
                last_exc = exc
                continue
            raise

    if last_exc is not None:
        raise RuntimeError(f"Failed to initialize PaddleOCR with compatible args: {last_exc}") from last_exc
    raise RuntimeError("Failed to initialize PaddleOCR")


def init_ocr_engine() -> Any:
    global _ocr_engine
    if _ocr_engine is not None:
        return _ocr_engine

    with _ocr_engine_lock:
        if _ocr_engine is not None:
            return _ocr_engine

        _ocr_engine = _create_ocr_engine()
        _logger.info("PaddleOCR initialized on CPU")

    return _ocr_engine


def _extract_texts_from_predict_result(result: Any) -> List[str]:
    texts: List[str] = []
    if not result:
        return texts

    pages = result if isinstance(result, (list, tuple)) else [result]
    for page in pages:
        if isinstance(page, dict):
            raw_texts = page.get("rec_texts", []) or []
        elif hasattr(page, "rec_texts"):
            raw_texts = getattr(page, "rec_texts") or []
        else:
            continue
        for raw in raw_texts:
            text = str(raw).strip()
            if text:
                texts.append(text)
    return texts


def _extract_texts_from_ocr_result(result: Any) -> List[str]:
    texts: List[str] = []
    if not result:
        return texts

    pages = result if isinstance(result, (list, tuple)) else [result]
    for page in pages:
        if isinstance(page, dict):
            for raw in page.get("rec_texts", []) or []:
                text = str(raw).strip()
                if text:
                    texts.append(text)
            continue

        if not isinstance(page, (list, tuple)):
            continue

        for line in page:
            if not isinstance(line, (list, tuple)) or len(line) < 2:
                continue
            second = line[1]
            if isinstance(second, (list, tuple)) and second:
                text = str(second[0]).strip()
            else:
                text = str(second).strip()
            if text:
                texts.append(text)

    return texts


def _ensure_bgr_image(image: np.ndarray) -> np.ndarray:
    if image.ndim == 2:
        return cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    if image.ndim == 3 and image.shape[2] == 4:
        return cv2.cvtColor(image, cv2.COLOR_BGRA2BGR)
    return image


class OCRRunner:
    """面向小区域识别的轻量封装，内部复用全局 OCR 单例。"""

    def __init__(self) -> None:
        self._engine = init_ocr_engine()

    def recognize_texts(self, image: np.ndarray) -> List[str]:
        if image is None or image.size == 0:
            return []

        image = _ensure_bgr_image(image)

        # 兼容旧版本 ocr() 返回结构与新版本 predict() 返回结构。
        if hasattr(self._engine, "ocr"):
            try:
                result = self._engine.ocr(image, cls=False)
                texts = _extract_texts_from_ocr_result(result)
                return texts
            except Exception as exc:
                _logger.debug("OCRRunner.ocr() failed, fallback to predict(): %s", exc)

        try:
            result = self._engine.predict(image)
            return _extract_texts_from_predict_result(result)
        except Exception as exc:
            _logger.warning("OCRRunner.predict() failed on region image: %s", exc)
            return []

    @staticmethod
    def preprocess_for_cost(image: np.ndarray) -> np.ndarray:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        gray = cv2.resize(gray, None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC)
        _, binary = cv2.threshold(gray, 160, 255, cv2.THRESH_BINARY)
        return binary

    @staticmethod
    def preprocess_for_name(image: np.ndarray) -> np.ndarray:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        gray = cv2.resize(gray, None, fx=1.5, fy=1.5, interpolation=cv2.INTER_CUBIC)
        return gray
