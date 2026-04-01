package com.southwestasiafloat.backend.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 目前已选卡牌列表和当前牌堆状态
@Data
public class DraftSession {

    private String sessionId;

    private List<Card> pickedCards = new ArrayList<>();

    private DeckState deckState;

    private Integer currentPickNo;
}

