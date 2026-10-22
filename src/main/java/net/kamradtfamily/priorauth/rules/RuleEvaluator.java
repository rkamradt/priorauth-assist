package net.kamradtfamily.priorauth.rules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kamradtfamily.priorauth.fhir.FhirService;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {

    private final FhirService fhirService;

    public List<CriterionResult> evaluate(CoverageRule rule, String patientId) {
        List<CriterionResult> results = new ArrayList<>();
        if (rule.getCriteria() == null) {
            return results;
        }
        for (RuleCriterion criterion : rule.getCriteria()) {
            results.add(evaluateCriterion(criterion, patientId));
        }
        return results;
    }

    private CriterionResult evaluateCriterion(RuleCriterion criterion, String patientId) {
        return switch (criterion.getType()) {
            case "observation_count"   -> evaluateObservationCount(criterion, patientId);
            case "condition_present"   -> evaluateConditionPresent(criterion, patientId);
            case "observation_value"   -> evaluateObservationValue(criterion, patientId);
            case "procedure_present"   -> evaluateProcedurePresent(criterion, patientId);
            case "procedure_count"     -> evaluateProcedureCount(criterion, patientId);
            case "medication_present"  -> evaluateMedicationPresent(criterion, patientId);
            default -> new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.MISSING, "Unknown criterion type: " + criterion.getType(), List.of());
        };
    }

    private CriterionResult evaluateObservationCount(RuleCriterion criterion, String patientId) {
        List<Observation> observations = fhirService.getObservations(patientId);
        LocalDate cutoff = criterion.getLookbackDays() != null
            ? LocalDate.now().minusDays(criterion.getLookbackDays())
            : LocalDate.MIN;

        List<String> refs = new ArrayList<>();
        for (Observation obs : observations) {
            if (matchesCode(obs.getCode(), criterion) && isWithinLookback(extractObservationDate(obs), cutoff)) {
                refs.add("Observation/" + obs.getIdElement().getIdPart());
            }
        }

        int count = refs.size();
        int required = criterion.getMinOccurrences() != null ? criterion.getMinOccurrences() : 1;

        if (count >= required) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED,
                count + " observations found (required: " + required + ")", refs);
        } else if (count > 0) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.PARTIAL,
                count + " observations found (required: " + required + ")", refs);
        } else {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.MISSING, "No matching observations found", List.of());
        }
    }

    private CriterionResult evaluateConditionPresent(RuleCriterion criterion, String patientId) {
        List<Condition> conditions = fhirService.getConditions(patientId);
        List<String> refs = new ArrayList<>();
        for (Condition cond : conditions) {
            if (matchesCode(cond.getCode(), criterion)) {
                refs.add("Condition/" + cond.getIdElement().getIdPart());
            }
        }
        if (!refs.isEmpty()) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED, "Condition documented", refs);
        }
        return new CriterionResult(criterion.getId(), criterion.getDescription(),
            DocumentationStatus.MISSING, "Required condition not found in active conditions", List.of());
    }

    private CriterionResult evaluateObservationValue(RuleCriterion criterion, String patientId) {
        List<Observation> observations = fhirService.getObservations(patientId);
        String loincCode = criterion.getLoincCode() != null ? criterion.getLoincCode() : criterion.getCode();

        Observation mostRecent = null;
        for (Observation obs : observations) {
            if (hasLoincCode(obs, loincCode)) {
                if (mostRecent == null || isMoreRecent(obs, mostRecent)) {
                    mostRecent = obs;
                }
            }
        }

        if (mostRecent == null) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.MISSING, "No observation with code " + loincCode + " found", List.of());
        }

        double value = extractDecimalValue(mostRecent);
        String ref = "Observation/" + mostRecent.getIdElement().getIdPart();
        boolean meetsMin = criterion.getMinValue() == null || value >= criterion.getMinValue();
        boolean meetsMax = criterion.getMaxValue() == null || value <= criterion.getMaxValue();

        if (meetsMin && meetsMax) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED, "Value " + value + " meets criteria", List.of(ref));
        }
        return new CriterionResult(criterion.getId(), criterion.getDescription(),
            DocumentationStatus.MISSING, "Value " + value + " does not meet criteria", List.of(ref));
    }

    private CriterionResult evaluateProcedurePresent(RuleCriterion criterion, String patientId) {
        List<Procedure> procedures = fhirService.getProcedures(patientId);
        List<String> refs = new ArrayList<>();
        for (Procedure proc : procedures) {
            if (matchesCode(proc.getCode(), criterion)) {
                refs.add("Procedure/" + proc.getIdElement().getIdPart());
            }
        }
        if (!refs.isEmpty()) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED, "Procedure documented", refs);
        }
        return new CriterionResult(criterion.getId(), criterion.getDescription(),
            DocumentationStatus.MISSING, "Required procedure not found", List.of());
    }

    private CriterionResult evaluateProcedureCount(RuleCriterion criterion, String patientId) {
        List<Procedure> procedures = fhirService.getProcedures(patientId);
        LocalDate cutoff = criterion.getLookbackDays() != null
            ? LocalDate.now().minusDays(criterion.getLookbackDays())
            : LocalDate.MIN;

        List<String> refs = new ArrayList<>();
        for (Procedure proc : procedures) {
            if (matchesCode(proc.getCode(), criterion) &&
                    isWithinLookback(extractProcedureDate(proc), cutoff)) {
                refs.add("Procedure/" + proc.getIdElement().getIdPart());
            }
        }

        int count = refs.size();
        int required = criterion.getMinOccurrences() != null ? criterion.getMinOccurrences() : 1;

        if (count >= required) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED,
                count + " procedures found (required: " + required + ")", refs);
        } else if (count > 0) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.PARTIAL,
                count + " procedures found (required: " + required + ")", refs);
        } else {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.MISSING, "No matching procedures found", List.of());
        }
    }

    private Date extractProcedureDate(Procedure proc) {
        try {
            if (proc.hasPerformedDateTimeType()) {
                return proc.getPerformedDateTimeType().getValue();
            }
        } catch (Exception e) {
            log.debug("Could not extract date from procedure {}", proc.getId());
        }
        return null;
    }

    private CriterionResult evaluateMedicationPresent(RuleCriterion criterion, String patientId) {
        List<MedicationRequest> medications = fhirService.getMedicationRequests(patientId);
        List<String> refs = new ArrayList<>();
        for (MedicationRequest med : medications) {
            if (med.getMedicationCodeableConcept() != null &&
                matchesCode(med.getMedicationCodeableConcept(), criterion)) {
                refs.add("MedicationRequest/" + med.getIdElement().getIdPart());
            }
        }

        int count = refs.size();
        int required = criterion.getMinOccurrences() != null ? criterion.getMinOccurrences() : 1;

        if (count >= required) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.DOCUMENTED, count + " matching medications found", refs);
        } else if (count > 0) {
            return new CriterionResult(criterion.getId(), criterion.getDescription(),
                DocumentationStatus.PARTIAL, count + " of " + required + " required medications found", refs);
        }
        return new CriterionResult(criterion.getId(), criterion.getDescription(),
            DocumentationStatus.MISSING, "Required medication not found", List.of());
    }

    private boolean matchesCode(org.hl7.fhir.r4.model.CodeableConcept concept, RuleCriterion criterion) {
        if (concept == null) return false;
        for (org.hl7.fhir.r4.model.Coding coding : concept.getCoding()) {
            if (matchesCoding(coding, criterion)) return true;
        }
        return false;
    }

    private boolean matchesCoding(org.hl7.fhir.r4.model.Coding coding, RuleCriterion criterion) {
        String system = criterion.getCodeSystem();
        String code = criterion.getCode();
        List<String> codes = criterion.getCodes();

        boolean systemMatches = system == null || system.equals(coding.getSystem());
        if (!systemMatches) return false;

        if (code != null && code.equals(coding.getCode())) return true;
        if (codes != null && codes.contains(coding.getCode())) return true;
        return false;
    }

    private boolean hasLoincCode(Observation obs, String loincCode) {
        if (obs.getCode() == null) return false;
        return obs.getCode().getCoding().stream()
            .anyMatch(c -> loincCode.equals(c.getCode()) ||
                ("http://loinc.org".equals(c.getSystem()) && loincCode.equals(c.getCode())));
    }

    private boolean isWithinLookback(Date date, LocalDate cutoff) {
        if (date == null) return true;
        LocalDate obsDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return !obsDate.isBefore(cutoff);
    }

    private boolean isMoreRecent(Observation a, Observation b) {
        Date dateA = extractObservationDate(a);
        Date dateB = extractObservationDate(b);
        if (dateA == null) return false;
        if (dateB == null) return true;
        return dateA.after(dateB);
    }

    private double extractDecimalValue(Observation obs) {
        try {
            if (obs.hasValueQuantity()) {
                return obs.getValueQuantity().getValue().doubleValue();
            }
            if (obs.hasValueIntegerType()) {
                return obs.getValueIntegerType().getValue();
            }
        } catch (Exception e) {
            log.debug("Could not extract numeric value from observation {}", obs.getId());
        }
        return 0.0;
    }

    private Date extractObservationDate(Observation obs) {
        try {
            if (obs.hasEffectiveDateTimeType()) {
                return obs.getEffectiveDateTimeType().getValue();
            }
        } catch (Exception e) {
            log.debug("Could not extract date from observation {}", obs.getId());
        }
        return null;
    }
}
