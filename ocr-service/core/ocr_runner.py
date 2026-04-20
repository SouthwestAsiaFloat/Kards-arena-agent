from __future__ import annotations

from dataclasses import dataclass
import logging
from statistics import median
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


@dataclass(frozen=True)
class OCRTextSegment:
    text: str
    bbox: tuple[int, int, int, int]
    score: float | None = None


def _coerce_score(value: Any) -> float | None:
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _has_content(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, np.ndarray):
        return value.size > 0
    if isinstance(value, (list, tuple, str, bytes, dict, set)):
        return len(value) > 0
    return True


def _first_non_empty(*values: Any) -> Any:
    for value in values:
        if _has_content(value):
            return value
    return None


def _box_to_bbox(box: Any) -> tuple[int, int, int, int] | None:
    if box is None:
        return None

    if isinstance(box, np.ndarray):
        box = box.tolist()

    if not isinstance(box, (list, tuple)) or not box:
        return None

    if len(box) == 4 and all(isinstance(value, (int, float)) for value in box):
        x1, y1, x2, y2 = box
        return int(x1), int(y1), int(x2), int(y2)

    points: list[tuple[float, float]] = []
    for item in box:
        if isinstance(item, np.ndarray):
            item = item.tolist()
        if isinstance(item, (list, tuple)) and len(item) >= 2:
            try:
                points.append((float(item[0]), float(item[1])))
            except (TypeError, ValueError):
                continue

    if not points:
        return None

    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))


def _sort_segments_by_layout(segments: List[OCRTextSegment]) -> List[OCRTextSegment]:
    if len(segments) <= 1:
        return segments

    prelim = sorted(segments, key=lambda segment: (segment.bbox[1], segment.bbox[0]))
    heights = [max(1, segment.bbox[3] - segment.bbox[1]) for segment in prelim]
    row_threshold = max(8, int(median(heights) * 0.6))

    rows: list[dict[str, Any]] = []
    for segment in prelim:
        center_y = (segment.bbox[1] + segment.bbox[3]) / 2
        if not rows:
            rows.append({"center_y": center_y, "segments": [segment]})
            continue

        current_row = rows[-1]
        if abs(center_y - current_row["center_y"]) <= row_threshold:
            current_row["segments"].append(segment)
            row_count = len(current_row["segments"])
            current_row["center_y"] = ((current_row["center_y"] * (row_count - 1)) + center_y) / row_count
        else:
            rows.append({"center_y": center_y, "segments": [segment]})

    ordered: list[OCRTextSegment] = []
    for row in rows:
        ordered.extend(sorted(row["segments"], key=lambda segment: segment.bbox[0]))
    return ordered


def _extract_segments_from_predict_result(result: Any) -> List[OCRTextSegment]:
    segments: List[OCRTextSegment] = []
    if not result:
        return segments

    pages = result if isinstance(result, (list, tuple)) else [result]
    for page in pages:
        if isinstance(page, dict):
            texts = page.get("rec_texts", []) or []
            boxes = _first_non_empty(page.get("rec_boxes"), page.get("rec_polys"), page.get("dt_polys"))
            scores = _first_non_empty(page.get("rec_scores"), [])
        elif hasattr(page, "rec_texts"):
            texts = getattr(page, "rec_texts") or []
            boxes = _first_non_empty(
                getattr(page, "rec_boxes", None),
                getattr(page, "rec_polys", None),
                getattr(page, "dt_polys", None),
            )
            scores = _first_non_empty(getattr(page, "rec_scores", None), [])
        else:
            continue

        if boxes is None:
            boxes = []
        if scores is None:
            scores = []

        for index, raw in enumerate(texts):
            text = str(raw).strip()
            if not text:
                continue

            bbox = _box_to_bbox(boxes[index] if index < len(boxes) else None)
            if bbox is None:
                bbox = (0, index * 100, 1, index * 100 + 1)

            score = _coerce_score(scores[index] if index < len(scores) else None)
            segments.append(OCRTextSegment(text=text, bbox=bbox, score=score))

    return _sort_segments_by_layout(segments)


def _extract_segments_from_ocr_result(result: Any) -> List[OCRTextSegment]:
    segments: List[OCRTextSegment] = []
    if not result:
        return segments

    pages = result if isinstance(result, (list, tuple)) else [result]
    for page in pages:
        if isinstance(page, dict):
            texts = page.get("rec_texts", []) or []
            boxes = _first_non_empty(page.get("rec_boxes"), page.get("rec_polys"), page.get("dt_polys"))
            scores = _first_non_empty(page.get("rec_scores"), [])

            if boxes is None:
                boxes = []
            if scores is None:
                scores = []

            for index, raw in enumerate(texts):
                text = str(raw).strip()
                if not text:
                    continue
                bbox = _box_to_bbox(boxes[index] if index < len(boxes) else None)
                if bbox is None:
                    bbox = (0, index * 100, 1, index * 100 + 1)
                score = _coerce_score(scores[index] if index < len(scores) else None)
                segments.append(OCRTextSegment(text=text, bbox=bbox, score=score))
            continue

        if not isinstance(page, (list, tuple)):
            continue

        for index, line in enumerate(page):
            if not isinstance(line, (list, tuple)) or len(line) < 2:
                continue

            bbox = _box_to_bbox(line[0]) or (0, index * 100, 1, index * 100 + 1)
            second = line[1]

            if isinstance(second, (list, tuple)) and second:
                text = str(second[0]).strip()
                score = _coerce_score(second[1] if len(second) > 1 else None)
            else:
                text = str(second).strip()
                score = None

            if text:
                segments.append(OCRTextSegment(text=text, bbox=bbox, score=score))

    return _sort_segments_by_layout(segments)


def _extract_texts_from_predict_result(result: Any) -> List[str]:
    return [segment.text for segment in _extract_segments_from_predict_result(result)]


def _extract_texts_from_ocr_result(result: Any) -> List[str]:
    return [segment.text for segment in _extract_segments_from_ocr_result(result)]


def _ensure_bgr_image(image: np.ndarray) -> np.ndarray:
    if image.ndim == 2:
        return cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    if image.ndim == 3 and image.shape[2] == 4:
        return cv2.cvtColor(image, cv2.COLOR_BGRA2BGR)
    return image


class OCRRunner:
    """Thin OCR wrapper that reuses a shared PaddleOCR engine."""

    def __init__(self) -> None:
        self._engine = init_ocr_engine()

    def recognize_segments(self, image: np.ndarray) -> List[OCRTextSegment]:
        if image is None or image.size == 0:
            return []

        image = _ensure_bgr_image(image)

        if hasattr(self._engine, "ocr"):
            try:
                result = self._engine.ocr(image, cls=False)
                segments = _extract_segments_from_ocr_result(result)
                if segments:
                    return segments
            except Exception as exc:
                _logger.debug("OCRRunner.ocr() failed, fallback to predict(): %s", exc)

        try:
            result = self._engine.predict(image)
            return _extract_segments_from_predict_result(result)
        except Exception as exc:
            _logger.warning("OCRRunner.predict() failed on region image: %s", exc)
            return []

    def recognize_texts(self, image: np.ndarray) -> List[str]:
        return [segment.text for segment in self.recognize_segments(image)]

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
