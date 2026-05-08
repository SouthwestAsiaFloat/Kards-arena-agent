from __future__ import annotations
import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile

from core.analyze_service import analyze_image
from core.ocr_runner import OCRRunner, init_ocr_engine

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    # 服务启动时预热 OCR 引擎，后续请求复用同一个进程内实例。
    try:
        application.state.ocr_engine = init_ocr_engine()
        application.state.ocr_runner = OCRRunner()
    except Exception as exc:
        logger.warning("OCR engine preload skipped at startup: %s", exc)
        application.state.ocr_engine = None
        application.state.ocr_runner = None
    yield

app = FastAPI(title="ocr-service", version="0.1.0", lifespan=lifespan)

@app.get("/health")
async def health_api():
    if getattr(app.state, "ocr_runner", None) is None:
        raise HTTPException(status_code=503, detail="OCRRunner not initialized")
    return {"status": "ok"}

@app.post("/ocr")
async def ocr_api(file: UploadFile = File(...)):
    contents = await file.read()
    nparr = np.frombuffer(contents, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if image is None:
        return {"error": "图片解码失败"}

    if app.state.ocr_runner is None:
        return {"error": "OCRRunner 未初始化"}

    return analyze_image(image, app.state.ocr_runner)
