import re

from core.layout_parser import crop_bbox_roi, detect_card_layout, crop_count_rois


def split_cards(image, card_bboxes):
    cards = []
    for x, y, w, h in card_bboxes:
        cards.append(image[y:y + h, x:x + w])
    return cards


def join_ocr_texts(texts):
    if not texts:
        return ""
    return "\n".join(t.strip() for t in texts if t and t.strip())


def dedupe_preserve_order(texts):
    result = []
    seen = set()

    for text in texts:
        cleaned = (text or "").strip()
        if not cleaned or cleaned in seen:
            continue
        seen.add(cleaned)
        result.append(cleaned)

    return result


def looks_like_stat_noise(text):
    normalized = (text or "").strip().lower().replace(" ", "")
    if not normalized:
        return True

    if re.fullmatch(r"\d+[k]?", normalized):
        return True

    if re.fullmatch(r"[x脳]?\d", normalized):
        return True

    return False


def split_segments_by_role(segments, card_shape):
    height, width = card_shape[:2]
    grouped = {
        "title": [],
        "body": [],
        "cost": [],
        "misc": [],
    }

    for segment in segments:
        text = segment.text.strip()
        if not text:
            continue

        x1, y1, x2, y2 = segment.bbox
        center_x = (x1 + x2) / 2
        center_y = (y1 + y2) / 2

        if center_y <= height * 0.22 and center_x <= width * 0.28:
            grouped["cost"].append(text)
            continue

        if center_y <= height * 0.22:
            grouped["title"].append(text)
            continue

        if center_y >= height * 0.60 and looks_like_stat_noise(text):
            grouped["misc"].append(text)
            continue

        grouped["body"].append(text)

    return {key: dedupe_preserve_order(value) for key, value in grouped.items()}


def simple_count_parse(text):
    if not text:
        return 1

    text = text.lower().replace(" ", "")

    match = re.search(r"(\d)[x脳]", text)
    if match:
        return int(match.group(1))

    match = re.search(r"[x脳](\d)", text)
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
    result = detect_card_layout(image)

    img = result["image"]
    card_bboxes = result["cards"]
    count_bboxes = result["count_bboxes"]
    card_subregions = result.get("card_subregions", [])

    if not card_bboxes:
        return {"error": "未检测到卡牌"}

    cards = split_cards(img, card_bboxes)
    count_rois = crop_count_rois(img, count_bboxes)

    outputs = []

    for i, card_img in enumerate(cards):
        count_img = count_rois[i]
        subregions = card_subregions[i] if i < len(card_subregions) else {}

        full_segments = ocr_runner.recognize_segments(card_img)
        grouped_segments = split_segments_by_role(full_segments, card_img.shape)
        full_texts = [segment.text for segment in full_segments]

        title_texts = grouped_segments["title"]
        if not title_texts and subregions.get("title_bbox") is not None:
            title_roi = crop_bbox_roi(img, subregions["title_bbox"])
            title_texts = dedupe_preserve_order(ocr_runner.recognize_texts(title_roi))

        body_texts = grouped_segments["body"]
        if not body_texts and subregions.get("body_bbox") is not None:
            body_roi = crop_bbox_roi(img, subregions["body_bbox"])
            body_texts = dedupe_preserve_order(ocr_runner.recognize_texts(body_roi))

        cost_texts = []
        if subregions.get("cost_bbox") is not None:
            cost_roi = crop_bbox_roi(img, subregions["cost_bbox"])
            cost_texts = dedupe_preserve_order(ocr_runner.recognize_texts(cost_roi))
        if not cost_texts:
            cost_texts = grouped_segments["cost"]

        count_texts = dedupe_preserve_order(ocr_runner.recognize_texts(count_img))
        count_text = join_ocr_texts(count_texts)
        count = simple_count_parse(count_text)

        raw_texts = dedupe_preserve_order(title_texts + body_texts)
        if not raw_texts:
            raw_texts = dedupe_preserve_order(full_texts)

        outputs.append({
            "raw_texts": raw_texts,
            "raw_text": join_ocr_texts(raw_texts),
            "full_raw_texts": dedupe_preserve_order(full_texts),
            "full_raw_text": join_ocr_texts(full_texts),
            "name_raw_texts": title_texts,
            "name_raw_text": join_ocr_texts(title_texts),
            "body_raw_texts": body_texts,
            "body_raw_text": join_ocr_texts(body_texts),
            "cost_raw_texts": cost_texts,
            "cost_raw_text": join_ocr_texts(cost_texts),
            "count_raw_texts": count_texts,
            "count_raw_text": count_text,
            "count": count,
        })

    return {"cards": outputs}
