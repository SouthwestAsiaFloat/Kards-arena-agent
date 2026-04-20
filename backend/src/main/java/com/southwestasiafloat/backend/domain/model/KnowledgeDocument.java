package com.southwestasiafloat.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private String id;
    private String collection;
    private String title;
    private String type;
    private String stability;
    private String sourceUrl;
    private String sourceTitle;
    private String summary;
    private String content;
    private List<String> tags = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
