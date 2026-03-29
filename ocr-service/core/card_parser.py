import re
from typing import Any, Dict, List, Optional


# 一些明显不是卡名的文本
NON_NAME_EXACT = {
    "k", "+", "十", "H", "?", "？"
}

# 常见关键词：如果一行里有这些，更可能是描述，不是卡名
DESC_HINTS = [
    "部署", "阵亡", "命令", "行动", "受到", "造成", "获得",
    "敌方", "友方", "总部", "单位", "步兵", "坦克", "飞机",
    "伤害", "花费", "抽", "召唤", "消灭", "若有", "每有",
    "直到", "回合", "防御", "攻击", "支援", "闪击", "烟幕"
]

# 单独作为卡名时可能成立的关键词
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

    # 1. 去首尾空格
    text = text.strip()

    # 2. 全部转小写（对英文有用）
    text = text.lower()

    # 3. 去掉所有空白（关键🔥）
    text = re.sub(r"\s+", "", text)

    # 4. 统一中文标点
    text = text.replace("：", ":")
    text = text.replace("，", ",")
    text = text.replace("。", ".")
    text = text.replace("；", ";")

    # 5. 去掉引号
    text = text.replace("“", "").replace("”", "")
    text = text.replace("‘", "").replace("’", "")

    return text


def looks_like_description(line: str) -> bool:
    if not line:
        return True

    # 太长往往是描述
    if len(line) >= 12:
        return True

    # 出现描述关键词
    for hint in DESC_HINTS:
        if hint in line and line not in NAME_ALLOW_SINGLE:
            return True

    # 明显是句子
    if any(p in line for p in ["。", ",", ":", "；"]):
        return True

    return False


def clean_name_line(line: str) -> str:
    line = normalize_text(line)

    # 去掉两端孤立符号
    line = line.strip("+-?？|[]【】{}<>")

    # 常见 OCR 噪声清理
    if line in NON_NAME_EXACT:
        return ""

    return line.strip()


def score_name_candidate(line: str) -> int:
    """
    分数越高越像卡名
    """
    score = 0

    if not line:
        return -999

    # 纯数字基本不可能是卡名
    if is_pure_number(line):
        return -999

    # 单字符通常不是卡名
    if len(line) == 1 and line not in NAME_ALLOW_SINGLE:
        return -999

    # 精确噪声
    if line in NON_NAME_EXACT:
        return -999

    # 太长像描述
    if len(line) > 16:
        score -= 5
    else:
        score += 2

    # 包含中文通常更像卡名
    if re.search(r"[\u4e00-\u9fff]", line):
        score += 4

    # 含英文/数字/括号，也可能是正经卡名，如 35(t) 坦克
    if re.search(r"[A-Za-z0-9()]", line):
        score += 2

    # 描述味太重则减分
    if looks_like_description(line):
        score -= 6

    # 短而像标题，加分
    if 2 <= len(line) <= 10:
        score += 3

    return score


def extract_cost(raw_texts: List[str]) -> tuple[Optional[int], Optional[str]]:
    """
    从 OCR 前几行中尽量提取 cost
    返回: (cost, cost_raw)
    """
    if not raw_texts:
        return None, None

    # 只看前几项，通常费用在最顶部附近
    head_lines = [normalize_text(x) for x in raw_texts[:4]]

    # 优先级1：纯数字，且通常比较小
    for line in head_lines:
        if is_pure_number(line):
            value = int(line)
            if 0 <= value <= 20:
                return value, line

    # 优先级2：形如 2K / 2k / 1K1 / 2K1
    for line in head_lines:
        match = re.match(r"(\d+)\s*[Kk]?", line)
        if match:
            try:
                value = int(match.group(1))
                if 0 <= value <= 20:
                    return value, line
            except ValueError:
                pass

    # 优先级3：取首个数字
    for line in head_lines:
        value = extract_first_digit(line)
        if value is not None and 0 <= value <= 20:
            return value, line

    return None, None


def merge_adjacent_name_lines(candidates: List[str]) -> List[str]:
    """
    处理像：
    ["35(t)", "坦克"] -> ["35(t) 坦克", "35(t)", "坦克"]
    给组合一个机会
    """
    results = list(candidates)

    for i in range(len(candidates) - 1):
        a = candidates[i]
        b = candidates[i + 1]

        if not a or not b:
            continue

        # 两行都不像描述，尝试拼接
        if not looks_like_description(a) and not looks_like_description(b):
            merged = f"{a} {b}".strip()
            results.append(merged)

    return results


def extract_name(raw_texts: List[str]) -> tuple[Optional[str], Optional[str]]:
    """
    从 raw_texts 中提取最可能的卡名
    返回: (name, name_raw)
    """
    if not raw_texts:
        return None, None

    lines = [clean_name_line(x) for x in raw_texts]
    lines = [x for x in lines if x]

    if not lines:
        return None, None

    # 去掉明显的费用/噪声
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
    best_score = -10**9

    for line in candidates:
        score = score_name_candidate(line)
        if score > best_score:
            best_score = score
            best_line = line

    if not best_line:
        return None, None

    return best_line, best_line


def parse_single_card(card_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    输入当前 pipeline 的单卡结果，例如：
    {
      "raw_texts": [...],
      "raw_text": "...",
      "count_raw_texts": [...],
      "count_raw_text": "...",
      "count": 2
    }
    """
    raw_texts = card_data.get("raw_texts", []) or []
    raw_text = card_data.get("raw_text", "") or ""
    count = card_data.get("count", 1)

    name, name_raw = extract_name(raw_texts)
    cost, cost_raw = extract_cost(raw_texts)

    return {
        "name": name,
        "name_raw": name_raw,
        "cost": cost,
        "cost_raw": cost_raw,
        "count": count,
        "raw_texts": raw_texts,
        "raw_text": raw_text,
        "count_raw_texts": card_data.get("count_raw_texts", []) or [],
        "count_raw_text": card_data.get("count_raw_text", "") or "",
    }


def parse_cards(ocr_result: Dict[str, Any]) -> Dict[str, Any]:
    """
    输入 process_image 的整体输出：
    {
      "cards": [
        {...},
        {...},
        {...}
      ]
    }
    """
    cards = ocr_result.get("cards", []) or []
    parsed_cards = [parse_single_card(card) for card in cards]
    return {"cards": parsed_cards}