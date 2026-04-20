"""
OCR text matching helpers.
"""
import re

from rapidfuzz import fuzz, process

from core.card_parser import normalize_text


def clean_raw_texts(raw_texts: list[str]) -> list[str]:
    cleaned = []

    for text in raw_texts:
        text = text.strip()
        if not text:
            continue

        if text.isdigit():
            continue

        if re.fullmatch(r"[0-9]+[kK]?[0-9]*'?|[kK]", text):
            continue

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


def filter_db_by_cost_with_fallback(db: list[dict], cost: int | None) -> list[dict]:
    if cost is None:
        return db

    filtered = [card for card in db if card.get("cost") == cost]
    if not filtered:
        return db

    return filtered


def search_cards(query: str, db: list[dict]):
    if not query or not db:
        return None

    query = normalize_text(query)
    choices = [build_search_text(card) for card in db]

    result = process.extractOne(query, choices, scorer=fuzz.WRatio)
    if result is None:
        return None

    matched_text, score, index = result
    return {
        "card": db[index],
        "matched_text": matched_text,
        "score": score,
    }


def build_query_from_ocr_card(ocr_card: dict) -> str:
    name_texts = clean_raw_texts(ocr_card.get("name_raw_texts", []) or [])
    body_texts = clean_raw_texts(ocr_card.get("body_raw_texts", []) or [])
    raw_texts = clean_raw_texts(ocr_card.get("raw_texts", []) or [])

    prioritized = []
    prioritized.extend(name_texts)
    prioritized.extend(name_texts)
    prioritized.extend(body_texts[:2])

    if not prioritized:
        prioritized = raw_texts

    query = " ".join(prioritized).strip()
    return normalize_text(query)


def match_ocr_result(ocr_result: dict, db: list[dict]):
    results = []
    ocr_cards = ocr_result.get("cards", [])

    for ocr_card in ocr_cards:
        query = build_query_from_ocr_card(ocr_card)
        filtered_db = filter_db_by_cost_with_fallback(db, ocr_card.get("cost"))
        match_result = search_cards(query, filtered_db)

        results.append({
            "ocr_card": ocr_card,
            "query": query,
            "match_result": match_result
        })

    return results


def simplify_match_results(match_results: list[dict]) -> list[dict]:
    simplified = []

    for item in match_results:
        match = item.get("match_result")
        ocr_card = item.get("ocr_card", {})

        if not match:
            continue

        card = match.get("card", {})

        simplified.append({
            "id": card.get("id"),
            "seq_id": card.get("seq_id"),
            "name": card.get("name"),
            "nation": card.get("nation"),
            "cost": card.get("cost"),
            "attack": card.get("attack"),
            "defense": card.get("defense"),
            "keywords": card.get("keywords"),
            "description": card.get("description"),
            "type": card.get("type"),
            "count": ocr_card.get("count", 1)
        })

    return simplified
