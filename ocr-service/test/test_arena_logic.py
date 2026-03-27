import numpy as np

from app.card_parser import build_card_result, parse_cost, parse_name
from app.layout_parser import split_arena_cards


def test_split_arena_cards_returns_three_slots():
    image = np.zeros((900, 1500, 3), dtype=np.uint8)
    cards = split_arena_cards(image, expected_columns=3)

    assert len(cards) == 3
    assert [c.slot for c in cards] == [0, 1, 2]
    assert all(c.cost_region.size > 0 for c in cards)
    assert all(c.name_region.size > 0 for c in cards)
    assert all(c.name_region_fallback is not None and c.name_region_fallback.size > 0 for c in cards)


def test_parse_cost_and_name_minimal():
    cost = parse_cost(["费用4", "abc"])
    name = parse_name(["部署：给友军+1", "闪击", "4"])
    card = build_card_result(1, name, cost)

    assert card == {"slot": 1, "name": "闪击", "cost": 4}

