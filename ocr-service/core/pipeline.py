
from core.layout_parser import detect_card_layout, crop_count_rois
import re


def split_cards(image, card_bboxes):
    """
    按 bbox 裁出三张卡
    """
    cards = []
    for x, y, w, h in card_bboxes:
        card_img = image[y:y + h, x:x + w]
        cards.append(card_img)
    return cards


def join_ocr_texts(texts):
    """
    OCRRunner.recognize_texts 返回 List[str]
    这里统一拼接成一个字符串，方便后续处理
    """
    if not texts:
        return ""
    return "\n".join(t.strip() for t in texts if t and t.strip())


def simple_count_parse(text):
    """
    更稳的数量解析：
    优先识别 2x / 3x / 4x 这种模式
    """
    if not text:
        return 1

    text = text.lower().replace(" ", "")

    match = re.search(r'(\d)[x×]', text)
    if match:
        return int(match.group(1))

    match = re.search(r'[x×](\d)', text)
    if match:
        return int(match.group(1))

    if len(text) <= 3:
        if "2" in text:
            return 2
        if "3" in text:
            return 3
        if "4" in text:
            return 4

    return 1


def process_image(image, ocr_runner):
    """
    整体流程：
    image -> layout -> 单卡 -> OCR -> count -> 输出
    """
    result = detect_card_layout(image)

    img = result["image"]
    card_bboxes = result["cards"]
    count_bboxes = result["count_bboxes"]

    if not card_bboxes:
        return {"error": "未检测到卡牌"}

    cards = split_cards(img, card_bboxes)
    count_rois = crop_count_rois(img, count_bboxes)

    outputs = []

    for i in range(len(cards)):
        card_img = cards[i]
        count_img = count_rois[i]

        # OCR 主体
        card_texts = ocr_runner.recognize_texts(card_img)
        card_text = join_ocr_texts(card_texts)

        # OCR 数量
        count_texts = ocr_runner.recognize_texts(count_img)
        count_text = join_ocr_texts(count_texts)

        count = simple_count_parse(count_text)

        outputs.append({
            "raw_texts": card_texts,   # 原始 list，后面调试很好用
            "raw_text": card_text,     # 拼接后的文本
            "count_raw_texts": count_texts,
            "count_raw_text": count_text,
            "count": count
        })

    return {
        "cards": outputs
    }