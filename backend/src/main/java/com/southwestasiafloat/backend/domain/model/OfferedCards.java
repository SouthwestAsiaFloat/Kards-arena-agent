package com.southwestasiafloat.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OfferedCards {
    private Card card1;
    private Card card2;
    private Card card3;

    public Card[] getCards() {
        return new Card[]{card1, card2, card3};
    }

    // 提供一个方法，直接用 List<Card> 来设置卡牌
    public void setCards(List<Card> offer1) {
        if (offer1.size() != 3) {
            throw new IllegalArgumentException("OfferedCards must contain exactly 3 cards.");
        }
        this.card1 = offer1.get(0);
        this.card2 = offer1.get(1);
        this.card3 = offer1.get(2);
    }
}

