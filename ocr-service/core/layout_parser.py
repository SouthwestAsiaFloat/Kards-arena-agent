# ocr-service/core/layout_parser.py
import cv2


def resize_to_fixed_width(image, target_width=1440):
    h, w = image.shape[:2]
    if w == 0:
        raise ValueError("输入图片宽度为0，无法缩放")
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
    """
    去掉高度重叠的重复框，保留面积更大的
    """
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
    """
    从轮廓中过滤出“像卡牌的矩形”
    """
    h_img, w_img = img_shape[:2]
    img_area = h_img * w_img
    candidates = []

    for cnt in contours:
        x, y, w, h = cv2.boundingRect(cnt)

        if w == 0 or h == 0:
            continue

        area = w * h
        ratio = h / w

        # 过滤太小的东西
        if area < 20000:
            continue

        # 卡牌大致是竖长方形
        if not (1.2 < ratio < 2.8):
            continue

        # 过滤掉过大的整体区域（比如整块UI）
        if area > img_area * 0.4:
            continue

        candidates.append((x, y, w, h))

    # 去重
    candidates = deduplicate_boxes(candidates, iou_threshold=0.6)

    # 按 x 排序，方便后面选左中右
    candidates = sorted(candidates, key=lambda c: c[0])

    return candidates


def pick_best_three(candidates):
    """
    从候选框中选出最可能的三张卡
    当前策略：
    1. 候选至少3个
    2. 优先找高度接近、y位置接近的三个框
    3. 如果找不到，就退化成最左 / 中 / 右
    """
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

                # 分数越小越好：希望三张卡顶部接近，高度接近，宽度接近
                score = y_spread * 2 + h_spread * 1.5 + w_spread

                if score < best_score:
                    best_score = score
                    best_triplet = triplet

    if best_triplet is not None:
        return best_triplet

    # fallback
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
    """
    为单张卡构造数量区域（用于检测 2x 这种标记）
    注意：数量区域通常在卡牌下方偏右，所以 y 会略微超出卡牌底边
    """
    x, y, w, h = card_bbox

    rx1 = x + int(w * 0.42)
    rx2 = x + int(w * 0.78)

    ry1 = y + int(h * 1.00)
    ry2 = y + int(h * 1.18)

    return clamp_bbox(rx1, ry1, rx2, ry2, img_shape)


def detect_card_layout(image):
    """
    返回统一结构：
    {
        "image": 缩放后的图,
        "candidates": [(x, y, w, h), ...],
        "cards": [(x, y, w, h), ...],              # 最终选中的三张卡
        "overall_bbox": (x1, y1, x2, y2) or None,  # 整体区域
        "count_bboxes": [(x1, y1, x2, y2), ...]    # 每张卡的数量区域
    }
    """
    img = resize_to_fixed_width(image, 1440)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)

    contours, _ = cv2.findContours(
        edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )

    candidates = filter_card_candidates(contours, img.shape)
    cards = pick_best_three(candidates)

    if cards is None:
        return {
            "image": img,
            "candidates": candidates,
            "cards": [],
            "overall_bbox": None,
            "count_bboxes": [],
        }

    overall_bbox = build_overall_bbox(cards, img.shape)
    count_bboxes = [build_count_bbox(card, img.shape) for card in cards]

    return {
        "image": img,
        "candidates": candidates,
        "cards": cards,
        "overall_bbox": overall_bbox,
        "count_bboxes": count_bboxes,
    }


def detect_card_region(image):
    """
    兼容你之前的接口：
    return img, candidates, region

    但内部已经改成新的 detect_card_layout
    """
    result = detect_card_layout(image)
    return result["image"], result["candidates"], result["overall_bbox"]


def crop_count_rois(image, count_bboxes):
    """
    按 count_bboxes 裁出数量检测小图
    """
    rois = []
    for x1, y1, x2, y2 in count_bboxes:
        roi = image[y1:y2, x1:x2]
        rois.append(roi)
    return rois


def debug_draw(image_path, output_path="debug_result.jpg", save_count_rois=False):
    image = cv2.imread(image_path)
    if image is None:
        raise ValueError(f"无法读取图片: {image_path}")

    result = detect_card_layout(image)
    img = result["image"]
    candidates = result["candidates"]
    cards = result["cards"]
    region = result["overall_bbox"]
    count_bboxes = result["count_bboxes"]

    debug_img = img.copy()

    # 1. 画所有候选框：绿色细框
    for x, y, w, h in candidates:
        cv2.rectangle(debug_img, (x, y), (x + w, y + h), (0, 255, 0), 2)

    # 2. 画最终选中的三张卡：蓝色中框
    for x, y, w, h in cards:
        cv2.rectangle(debug_img, (x, y), (x + w, y + h), (255, 0, 0), 3)

    # 3. 画整体区域：红色粗框
    if region is not None:
        x1, y1, x2, y2 = region
        cv2.rectangle(debug_img, (x1, y1), (x2, y2), (0, 0, 255), 4)

    # 4. 画数量区域：黄色框
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

    cv2.imwrite(output_path, debug_img)

    print(f"调试图已保存到: {output_path}")
    print(f"候选框数量: {len(candidates)}")
    print(f"最终卡牌数量: {len(cards)}")
    print(f"整体区域: {region}")
    print(f"三张卡: {cards}")
    print(f"数量区域: {count_bboxes}")

    if save_count_rois:
        rois = crop_count_rois(img, count_bboxes)
        for i, roi in enumerate(rois):
            if roi.size > 0:
                roi_path = f"count_roi_{i}.jpg"
                cv2.imwrite(roi_path, roi)
                print(f"数量ROI已保存: {roi_path}")


if __name__ == "__main__":
    debug_draw("ssb.png", "debug_result.jpg", save_count_rois=True)