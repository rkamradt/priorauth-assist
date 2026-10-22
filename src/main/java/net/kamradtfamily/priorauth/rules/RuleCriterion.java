package net.kamradtfamily.priorauth.rules;

import lombok.Data;

import java.util.List;

@Data
public class RuleCriterion {
    private String id;
    private String type;           // observation_count | condition_present | observation_value |
                                   // procedure_present | procedure_count | medication_present
    private String description;
    private String codeSystem;
    private String code;
    private List<String> codes;    // alternative multi-code list
    private Integer minOccurrences;
    private Integer lookbackDays;
    private Double minValue;
    private Double maxValue;
    private String loincCode;
}
