package com.southwestasiafloat.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SynergyResult {
    private String Id;
    private String cardName;
    private double synergyScore;
    private String comment;
    private Integer count;

    public char[] getComment() {
        return comment.toCharArray();
    }

    public double getSynergyBonus() {
        return synergyScore;
    }
}
