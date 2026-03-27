from __future__ import annotations

import numpy as np

from core.ocr_runner import OCRRunner


class _EngineFromOcr:
    def ocr(self, _image, cls=False):
        return [
            [
                ([[0, 0], [1, 0], [1, 1], [0, 1]], ("闪击", 0.99)),
                ([[0, 0], [1, 0], [1, 1], [0, 1]], ("4", 0.98)),
            ]
        ]

    def predict(self, _image):
        raise AssertionError("predict should not be called when ocr succeeded")


class _EngineBroken:
    def ocr(self, _image, cls=False):
        raise IndexError("tuple index out of range")

    def predict(self, _image):
        raise IndexError("tuple index out of range")


def _make_runner(engine):
    runner = OCRRunner.__new__(OCRRunner)
    runner._engine = engine
    return runner


def test_recognize_texts_from_legacy_ocr_result():
    runner = _make_runner(_EngineFromOcr())
    image = np.zeros((20, 20), dtype=np.uint8)

    texts = runner.recognize_texts(image)

    assert texts == ["闪击", "4"]


def test_recognize_texts_returns_empty_when_engine_raises():
    runner = _make_runner(_EngineBroken())
    image = np.zeros((20, 20), dtype=np.uint8)

    texts = runner.recognize_texts(image)

    assert texts == []

