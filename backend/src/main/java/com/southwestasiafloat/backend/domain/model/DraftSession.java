package com.southwestasiafloat.backend.domain.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 已选卡组列表
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftSession {
    private String sessionId;
    private int pickNo;
    private List<Card> pickedCards;

}

