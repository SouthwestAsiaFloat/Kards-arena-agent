import json
from pathlib import Path

from core.card_parser import parse_cards
from core.ocr_runner import OCRRunner
from core.pipeline import process_image
from core.search_cards import match_ocr_result, simplify_match_results

_card_db = None
_ocr_runner = None


def load_card_db():
    global _card_db
    if _card_db is None:
        data_path = Path(__file__).resolve().parent.parent / "data" / "cards.json"
        with data_path.open("r", encoding="utf-8") as file:
            _card_db = json.load(file)
    return _card_db


def get_ocr_runner():
    global _ocr_runner
    if _ocr_runner is None:
        _ocr_runner = OCRRunner()
    return _ocr_runner


def analyze_image(image, ocr_runner=None):
    runner = ocr_runner or get_ocr_runner()
    ocr_result = process_image(image, runner)
    parsed_result = parse_cards(ocr_result)
    matched_result = match_ocr_result(parsed_result, load_card_db())
    return simplify_match_results(matched_result)
