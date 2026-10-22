package net.kamradtfamily.priorauth.rules;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CriterionResult {
    private String criterionId;
    private String description;
    private DocumentationStatus status;   // DOCUMENTED | PARTIAL | MISSING
    private String detail;                // human-readable explanation
    private List<String> evidenceRefs;    // FHIR resource references like "Procedure/123"
}
