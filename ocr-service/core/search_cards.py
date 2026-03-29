"""
OCR 文本检索模块。
"""
import re
from rapidfuzz import process, fuzz

from core.card_parser import normalize_text


def clean_raw_texts(raw_texts: list[str]) -> list[str]:
    cleaned = []

    for text in raw_texts:
        text = text.strip()
        if not text:
            continue

        # 去掉纯数字
        if text.isdigit():
            continue

        # 去掉类似 3K / 3K2 / 2' / 5k 这种费用噪音
        if re.fullmatch(r"[0-9]+[kK]?[0-9]*'?|[kK]", text):
            continue

        # 去掉单个字符
        if len(text) == 1:
            continue

        cleaned.append(text)

    return cleaned


def build_search_text(card: dict) -> str:
    name = card.get("name", "")
    description = card.get("description", "")
    keywords = " ".join(card.get("keywords", []))
    text = f"{name} {name} {keywords} {description}".strip()
    return normalize_text(text)

# 这是一个带回退的cost筛选， 如果ocr识别cost错误， 将会全库搜索
def filter_db_by_cost_with_fallback(db: list[dict], cost: int | None) -> list[dict]:
    if cost is None:
        return db

    filtered = [card for card in db if card.get("cost") == cost]

    # 如果按 cost 过滤后为空，就回退到全库
    if not filtered:
        return db

    return filtered


def search_cards(query: str, db: list[dict], threshold: float = 80):
    if not query or not db:
        return None

    query = normalize_text(query)
    choices = [build_search_text(card) for card in db]

    result = process.extractOne(
        query,
        choices,
        scorer=fuzz.WRatio
    )

    if result is None:
        return None

    matched_text, score, index = result

    if score < threshold:
        return None

    return {
        "card": db[index],
        "matched_text": matched_text,
        "score": score
    }


def search_cards_topk(query: str, db: list[dict], limit: int = 3):
    if not query or not db:
        return []

    query = normalize_text(query)
    choices = [build_search_text(card) for card in db]

    results = process.extract(
        query,
        choices,
        scorer=fuzz.WRatio,
        limit=limit
    )

    output = []
    for matched_text, score, index in results:
        output.append({
            "card": db[index],
            "matched_text": matched_text,
            "score": score
        })

    return output


def build_query_from_ocr_card(ocr_card: dict) -> str:
    raw_texts = ocr_card.get("raw_texts", [])
    raw_texts = clean_raw_texts(raw_texts)
    query = " ".join(raw_texts).strip()
    return normalize_text(query)


def match_ocr_result(ocr_result: dict, db: list[dict], threshold: float = 80):
    results = []
    ocr_cards = ocr_result.get("cards", [])

    for ocr_card in ocr_cards:
        query = build_query_from_ocr_card(ocr_card)

        # 不筛选了
        cost = ocr_card.get("cost")
        candidate_db = db

        match_result = search_cards(query, candidate_db, threshold=threshold)
        topk = search_cards_topk(query, candidate_db, limit=3)

        results.append({
            "ocr_card": ocr_card,
            "query": query,
            "candidate_count": len(candidate_db),
            "match_result": match_result,
            "topk": topk
        })

    return results