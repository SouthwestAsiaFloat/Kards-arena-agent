import cv2


def resize_to_fixed_width(image, target_width=1440):
    h, w = image.shape[:2]
    if w == 0:
        raise ValueError("Input image width is 0, unable to resize.")
    scale = target_width / w
    new_h = int(h * scale)
    return cv2.resize(image, (target_width, new_h))


def clamp(value, low, high):
    return max(low, min(value, high))


def clamp_bbox(x1, y1, x2, y2, shape):
    h, w = shape[:2]
    x1 = clamp(x1, 0, w)
    y1 = clamp(y1, 0, h)
    x2 = clamp(x2, 0, w)
    y2 = clamp(y2, 0, h)
    return x1, y1, x2, y2


def crop_bbox_roi(image, bbox):
    x1, y1, x2, y2 = bbox
    return image[y1:y2, x1:x2]


def bbox_iou(box1, box2):
    """
    box: (x, y, w, h)
    """
    x1, y1, w1, h1 = box1
    x2, y2, w2, h2 = box2

    ax1, ay1, ax2, ay2 = x1, y1, x1 + w1, y1 + h1
    bx1, by1, bx2, by2 = x2, y2, x2 + w2, y2 + h2

    inter_x1 = max(ax1, bx1)
    inter_y1 = max(ay1, by1)
    inter_x2 = min(ax2, bx2)
    inter_y2 = min(ay2, by2)

    inter_w = max(0, inter_x2 - inter_x1)
    inter_h = max(0, inter_y2 - inter_y1)
    inter_area = inter_w * inter_h

    area1 = w1 * h1
    area2 = w2 * h2
    union = area1 + area2 - inter_area

    if union == 0:
        return 0.0

    return inter_area / union


def deduplicate_boxes(boxes, iou_threshold=0.6):
    boxes = sorted(boxes, key=lambda b: b[2] * b[3], reverse=True)
    result = []

    for box in boxes:
        keep = True
        for chosen in result:
            if bbox_iou(box, chosen) > iou_threshold:
                keep = False
                break
        if keep:
            result.append(box)

    return result


def filter_card_candidates(contours, img_shape):
    h_img, w_img = img_shape[:2]
    img_area = h_img * w_img
    candidates = []

    for cnt in contours:
        x, y, w, h = cv2.boundingRect(cnt)

        if w == 0 or h == 0:
            continue

        area = w * h
        ratio = h / w

        if area < 20000:
            continue

        if not (1.2 < ratio < 2.8):
            continue

        if area > img_area * 0.4:
            continue

        candidates.append((x, y, w, h))

    candidates = deduplicate_boxes(candidates, iou_threshold=0.6)
    candidates = sorted(candidates, key=lambda c: c[0])
    return candidates


def pick_best_three(candidates):
    if len(candidates) < 3:
        return None

    best_triplet = None
    best_score = float("inf")

    n = len(candidates)
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                triplet = [candidates[i], candidates[j], candidates[k]]
                triplet = sorted(triplet, key=lambda c: c[0])

                ys = [c[1] for c in triplet]
                hs = [c[3] for c in triplet]
                ws = [c[2] for c in triplet]

                y_spread = max(ys) - min(ys)
                h_spread = max(hs) - min(hs)
                w_spread = max(ws) - min(ws)

                score = y_spread * 2 + h_spread * 1.5 + w_spread

                if score < best_score:
                    best_score = score
                    best_triplet = triplet

    if best_triplet is not None:
        return best_triplet

    return [candidates[0], candidates[len(candidates) // 2], candidates[-1]]


def build_overall_bbox(cards, img_shape, pad_ratio_x=0.05, pad_ratio_y=0.05):
    x1 = min(c[0] for c in cards)
    y1 = min(c[1] for c in cards)
    x2 = max(c[0] + c[2] for c in cards)
    y2 = max(c[1] + c[3] for c in cards)

    pad_x = int((x2 - x1) * pad_ratio_x)
    pad_y = int((y2 - y1) * pad_ratio_y)

    x1 -= pad_x
    y1 -= pad_y
    x2 += pad_x
    y2 += pad_y

    return clamp_bbox(x1, y1, x2, y2, img_shape)


def build_count_bbox(card_bbox, img_shape):
    x, y, w, h = card_bbox

    rx1 = x + int(w * 0.42)
    rx2 = x + int(w * 0.78)
    ry1 = y + int(h * 1.00)
    ry2 = y + int(h * 1.18)

    return clamp_bbox(rx1, ry1, rx2, ry2, img_shape)


def build_cost_bbox(card_bbox, img_shape):
    x, y, w, h = card_bbox
    return clamp_bbox(
        x + int(w * 0.00),
        y + int(h * 0.00),
        x + int(w * 0.24),
        y + int(h * 0.20),
        img_shape,
    )


def build_title_bbox(card_bbox, img_shape):
    x, y, w, h = card_bbox
    return clamp_bbox(
        x + int(w * 0.14),
        y + int(h * 0.00),
        x + int(w * 0.92),
        y + int(h * 0.22),
        img_shape,
    )


def build_body_bbox(card_bbox, img_shape):
    x, y, w, h = card_bbox
    return clamp_bbox(
        x + int(w * 0.06),
        y + int(h * 0.72),
        x + int(w * 0.95),
        y + int(h * 0.99),
        img_shape,
    )


def build_card_subregions(card_bbox, img_shape):
    return {
        "cost_bbox": build_cost_bbox(card_bbox, img_shape),
        "title_bbox": build_title_bbox(card_bbox, img_shape),
        "body_bbox": build_body_bbox(card_bbox, img_shape),
    }


def detect_card_layout(image):
    """
    Return a normalized layout payload:
    {
        "image": resized image,
        "candidates": [(x, y, w, h), ...],
        "cards": [(x, y, w, h), ...],
        "overall_bbox": (x1, y1, x2, y2) or None,
        "count_bboxes": [(x1, y1, x2, y2), ...],
        "card_subregions": [
            {
                "cost_bbox": (...),
                "title_bbox": (...),
                "body_bbox": (...)
            },
            ...
        ]
    }
    """
    img = resize_to_fixed_width(image, 1440)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)

    contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    candidates = filter_card_candidates(contours, img.shape)
    cards = pick_best_three(candidates)

    if cards is None:
        return {
            "image": img,
            "candidates": candidates,
            "cards": [],
            "overall_bbox": None,
            "count_bboxes": [],
            "card_subregions": [],
        }

    overall_bbox = build_overall_bbox(cards, img.shape)
    count_bboxes = [build_count_bbox(card, img.shape) for card in cards]
    card_subregions = [build_card_subregions(card, img.shape) for card in cards]

    return {
        "image": img,
        "candidates": candidates,
        "cards": cards,
        "overall_bbox": overall_bbox,
        "count_bboxes": count_bboxes,
        "card_subregions": card_subregions,
    }


def detect_card_region(image):
    result = detect_card_layout(image)
    return result["image"], result["candidates"], result["overall_bbox"]


def crop_count_rois(image, count_bboxes):
    return [crop_bbox_roi(image, bbox) for bbox in count_bboxes]


def debug_draw(image_path, output_path="debug_result.jpg", save_count_rois=False):
    image = cv2.imread(image_path)
    if image is None:
        raise ValueError(f"Unable to read image: {image_path}")

    result = detect_card_layout(image)
    img = result["image"]
    candidates = result["candidates"]
    cards = result["cards"]
    region = result["overall_bbox"]
    count_bboxes = result["count_bboxes"]
    card_subregions = result["card_subregions"]

    debug_img = img.copy()

    for x, y, w, h in candidates:
        cv2.rectangle(debug_img, (x, y), (x + w, y + h), (0, 255, 0), 2)

    for x, y, w, h in cards:
        cv2.rectangle(debug_img, (x, y), (x + w, y + h), (255, 0, 0), 3)

    if region is not None:
        x1, y1, x2, y2 = region
        cv2.rectangle(debug_img, (x1, y1), (x2, y2), (0, 0, 255), 4)

    for idx, (x1, y1, x2, y2) in enumerate(count_bboxes):
        cv2.rectangle(debug_img, (x1, y1), (x2, y2), (0, 255, 255), 2)
        cv2.putText(
            debug_img,
            f"count_{idx}",
            (x1, max(0, y1 - 10)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (0, 255, 255),
            2,
        )

    for subregions in card_subregions:
        for color, key in [((255, 128, 0), "cost_bbox"), ((255, 0, 255), "title_bbox"), ((128, 255, 0), "body_bbox")]:
            x1, y1, x2, y2 = subregions[key]
            cv2.rectangle(debug_img, (x1, y1), (x2, y2), color, 2)

    cv2.imwrite(output_path, debug_img)

    print(f"Debug image saved to {output_path}")
    print(f"Candidate boxes: {len(candidates)}")
    print(f"Selected cards: {len(cards)}")
    print(f"Overall region: {region}")
    print(f"Cards: {cards}")
    print(f"Count boxes: {count_bboxes}")
    print(f"Card subregions: {card_subregions}")

    if save_count_rois:
        rois = crop_count_rois(img, count_bboxes)
        for i, roi in enumerate(rois):
            if roi.size > 0:
                roi_path = f"count_roi_{i}.jpg"
                cv2.imwrite(roi_path, roi)
                print(f"Saved count ROI to {roi_path}")


if __name__ == "__main__":
    debug_draw("ssb.png", "debug_result.jpg", save_count_rois=True)
