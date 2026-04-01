package com.southwestasiafloat.backend.domain.service;

import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;



@SpringBootTest
public class CardEvaluationServiceTest {

    @Autowired
    private CardEvaluationService cardEvaluationService;

    @Test
    void shouldEvaluateThreeExampleCards() {
        Card card1 = new Card();
        card1.setName("哈利法克斯 B Mk I");
        card1.setCount(1);

        Card card2 = new Card();
        card2.setName("USS 约克城号");
        card2.setCount(1);

        Card card3 = new Card();
        card3.setName("红魔空降步兵团");
        card3.setCount(3);

        OfferedCards offeredCards = new OfferedCards();
        offeredCards.setCard1(card1);
        offeredCards.setCard2(card2);
        offeredCards.setCard3(card3);

        List<CardEvaluationResult> results = cardEvaluationService.evaluate(offeredCards);
        System.out.println("=== Evaluation Results ===");
        System.out.println(results);

    }
}