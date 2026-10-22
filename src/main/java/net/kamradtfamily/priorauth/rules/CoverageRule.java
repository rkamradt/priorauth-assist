package net.kamradtfamily.priorauth.rules;

import lombok.Data;

import java.util.List;

@Data
public class CoverageRule {
    private String id;
    private String version;
    private String service;               // plain name for matching
    private List<String> serviceCodes;    // CPT/HCPCS codes
    private boolean requiresPriorAuth;
    private String plainText;
    private String requiredDocuments;     // multiline string
    private List<RuleCriterion> criteria;
}
