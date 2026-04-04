package com.southwestasiafloat.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Card {
    private String id;
    private String seq_id;
    private String name;
    private String nation;
    private Integer cost;
    private Integer attack;
    private Integer defense;
    private List<String> keywords;
    private String description;
    private String type; // unit/order
    private Integer count; // 这一抓的数量
}

