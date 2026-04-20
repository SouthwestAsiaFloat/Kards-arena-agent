import re
from typing import Any, Dict, List, Optional


NON_NAME_EXACT = {
    "k", "+", "卡", "h", "?", "："
}

DESC_HINTS = [
    "部署", "阵亡", "命令", "行动", "受到", "造成", "获得",
    "敌方", "友方", "总部", "单位", "步兵", "坦克", "飞机",
    "伤害", "花费", "抽", "召唤", "消灭", "若有", "每有",
    "直到", "回合", "防御", "攻击", "支援", "闪击", "烟幕"
]

NAME_ALLOW_SINGLE = [
    "复仇", "闪击", "烟幕"
]


def is_pure_number(text: str) -> bool:
    return bool(re.fullmatch(r"\d+", text))


def extract_first_digit(text: str) -> Optional[int]:
    match = re.search(r"\d+", text)
    if not match:
        return None
    try:
        return int(match.group())
    except ValueError:
        return None


def normalize_text(text: str) -> str:
    if not text:
        return ""

    text = text.strip().lower()
    text = re.sub(r"\s+", "", text)

    replacements = {
        "：": ":",
        "，": ",",
        "。": ".",
        "；": ";",
        "“": "",
        "”": "",
        "‘": "",
        "’": "",
    }

    for source, target in replacements.items():
        text = text.replace(source, target)

    return text


def looks_like_description(line: str) -> bool:
    if not line:
        return True

    if len(line) >= 12:
        return True

    for hint in DESC_HINTS:
        if hint in line and line not in NAME_ALLOW_SINGLE:
            return True

    if any(p in line for p in [".", ",", ":", ";"]):
        return True

    return False


def clean_name_line(line: str) -> str:
    line = normalize_text(line)
    line = line.strip("+-?：[]【】(){}<>")

    if line in NON_NAME_EXACT:
        return ""

    return line.strip()


def score_name_candidate(line: str) -> int:
    if not line:
        return -999

    if is_pure_number(line):
        return -999

    if len(line) == 1 and line not in NAME_ALLOW_SINGLE:
        return -999

    if line in NON_NAME_EXACT:
        return -999

    score = 0

    if len(line) > 16:
        score -= 5
    else:
        score += 2

    if re.search(r"[\u4e00-\u9fff]", line):
        score += 4

    if re.search(r"[A-Za-z0-9()]", line):
        score += 2

    if looks_like_description(line):
        score -= 6

    if 2 <= len(line) <= 10:
        score += 3

    return score


def extract_cost(raw_texts: List[str]) -> tuple[Optional[int], Optional[str]]:
    if not raw_texts:
        return None, None

    head_lines = [normalize_text(x) for x in raw_texts[:4]]

    for line in head_lines:
        if is_pure_number(line):
            value = int(line)
            if 0 <= value <= 20:
                return value, line

    for line in head_lines:
        match = re.match(r"(\d+)\s*[kK]?", line)
        if match:
            try:
                value = int(match.group(1))
                if 0 <= value <= 20:
                    return value, line
            except ValueError:
                pass

    for line in head_lines:
        value = extract_first_digit(line)
        if value is not None and 0 <= value <= 20:
            return value, line

    return None, None


def merge_adjacent_name_lines(candidates: List[str]) -> List[str]:
    results = list(candidates)

    for i in range(len(candidates) - 1):
        a = candidates[i]
        b = candidates[i + 1]

        if not a or not b:
            continue

        if not looks_like_description(a) and not looks_like_description(b):
            results.append(f"{a} {b}".strip())

    return results


def extract_name(raw_texts: List[str]) -> tuple[Optional[str], Optional[str]]:
    if not raw_texts:
        return None, None

    lines = [clean_name_line(x) for x in raw_texts]
    lines = [x for x in lines if x]

    if not lines:
        return None, None

    filtered = []
    for line in lines:
        if line in NON_NAME_EXACT:
            continue
        if is_pure_number(line):
            continue
        filtered.append(line)

    if not filtered:
        return None, None

    candidates = merge_adjacent_name_lines(filtered)

    best_line = None
    best_score = -10 ** 9

    for line in candidates:
        score = score_name_candidate(line)
        if score > best_score:
            best_score = score
            best_line = line

    if not best_line:
        return None, None

    return best_line, best_line


def parse_single_card(card_data: Dict[str, Any]) -> Dict[str, Any]:
    raw_texts = card_data.get("raw_texts", []) or []
    raw_text = card_data.get("raw_text", "") or ""
    count = card_data.get("count", 1)

    name_source_texts = card_data.get("name_raw_texts", []) or raw_texts
    cost_source_texts = card_data.get("cost_raw_texts", []) or raw_texts
    full_source_texts = card_data.get("full_raw_texts", []) or raw_texts

    name, name_raw = extract_name(name_source_texts)
    if name is None and name_source_texts != raw_texts:
        name, name_raw = extract_name(raw_texts)

    cost, cost_raw = extract_cost(cost_source_texts)
    fallback_cost, fallback_cost_raw = extract_cost(full_source_texts)

    if cost is None:
        cost, cost_raw = fallback_cost, fallback_cost_raw
    elif fallback_cost is not None and fallback_cost != cost:
        first_full_line = normalize_text(full_source_texts[0]) if full_source_texts else ""
        if is_pure_number(first_full_line):
            cost, cost_raw = fallback_cost, fallback_cost_raw

    return {
        "name": name,
        "name_raw": name_raw,
        "cost": cost,
        "cost_raw": cost_raw,
        "count": count,
        "raw_texts": raw_texts,
        "raw_text": raw_text,
        "full_raw_texts": card_data.get("full_raw_texts", []) or [],
        "full_raw_text": card_data.get("full_raw_text", "") or "",
        "name_raw_texts": card_data.get("name_raw_texts", []) or [],
        "name_raw_text": card_data.get("name_raw_text", "") or "",
        "body_raw_texts": card_data.get("body_raw_texts", []) or [],
        "body_raw_text": card_data.get("body_raw_text", "") or "",
        "cost_raw_texts": card_data.get("cost_raw_texts", []) or [],
        "cost_raw_text": card_data.get("cost_raw_text", "") or "",
        "count_raw_texts": card_data.get("count_raw_texts", []) or [],
        "count_raw_text": card_data.get("count_raw_text", "") or "",
    }


def parse_cards(ocr_result: Dict[str, Any]) -> Dict[str, Any]:
    cards = ocr_result.get("cards", []) or []
    parsed_cards = [parse_single_card(card) for card in cards]
    return {"cards": parsed_cards}
