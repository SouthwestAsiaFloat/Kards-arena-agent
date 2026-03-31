package com.southwestasiafloat.backend.domain.service;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DeckState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// 目前已抓卡组状态分析
public class DeckStateAnalyzer {

    public DeckState analyze(List<Card> pickedCards) {

        DeckState state = new DeckState();

        Map<Integer, Integer> costCurve = new HashMap<>();

        int unitCount = 0;
        int orderCount = 0;

        int early = 0;
        int mid = 0;
        int late = 0;

        for (Card card : pickedCards) {

            int cost = card.getCost();

            // 费用曲线统计
            costCurve.put(cost, costCurve.getOrDefault(cost, 0) + card.getCount());

            // 类型统计
            if ("unit".equalsIgnoreCase(card.getType())) {
                unitCount = unitCount + card.getCount();
            } else {
                orderCount = orderCount + card.getCount();
            }

            // 阶段划分
            if (cost <= 3) {
                early += card.getCount();
            } else if (cost <= 6) {
                mid += card.getCount();
            } else {
                late += card.getCount();
            }
        }

        // 填充 state
        state.setCostCurve(costCurve);
        state.setUnitCount(unitCount);
        state.setOrderCount(orderCount);
        state.setTotalCards(pickedCards.size());

        state.setEarlyCount(early);
        state.setMidCount(mid);
        state.setLateCount(late);

        // 标签分析
        state.setTags(generateTags(state));

        return state;
    }

    private List<String> generateTags(DeckState state) {

        List<String> tags = new ArrayList<>();

        // 费用结构
        if (state.getEarlyCount() >= state.getLateCount() + 3) {
            tags.add("early-heavy"); // 前期强
        }

        if (state.getLateCount() >= 3) {
            tags.add("late-game"); // 后期能力
        }

        // 类型结构
        if (state.getUnitCount() > state.getOrderCount() * 2) {
            tags.add("unit-heavy"); // 单位多
        }

        if (state.getOrderCount() > state.getUnitCount()) {
            tags.add("control"); // 偏控制
        }

        // 缺失检测
        if (state.getEarlyCount() <= 2) {
            tags.add("lack-early");
        }

        if (state.getLateCount() == 0) {
            tags.add("lack-late");
        }

        return tags;
    }
}
