package net.kamradtfamily.priorauth.rules;

import net.kamradtfamily.priorauth.fhir.FhirService;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SleepStudyRuleTest {

    @Mock
    private FhirService fhirService;

    @InjectMocks
    private RuleEvaluator ruleEvaluator;

    private CoverageRule sleepStudyRule;

    @BeforeEach
    void setUp() throws Exception {
        RuleLoader loader = new RuleLoader();
        Map<String, CoverageRule> rules = loader.loadAll();
        sleepStudyRule = rules.get("sleep-study");
        assertThat(sleepStudyRule).as("sleep-study rule must be loadable").isNotNull();
    }

    @Test
    void testCompleteDocumentation() {
        // Epworth obs 14 + BMI obs 34 + snoring condition -> all DOCUMENTED
        when(fhirService.getProcedures(anyString())).thenReturn(List.of());
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());
        when(fhirService.getObservations(anyString())).thenReturn(List.of(
            buildObservation("89243-0", 14.0),   // Epworth score
            buildObservation("39156-5", 34.0)    // BMI
        ));
        when(fhirService.getConditions(anyString())).thenReturn(List.of(
            buildCondition("http://snomed.info/sct", "72863001", "Snoring")
        ));

        List<CriterionResult> results = ruleEvaluator.evaluate(sleepStudyRule, "sleep-complete-001");

        CriterionResult epworthResult = findById(results, "epworth_score");
        assertThat(epworthResult.getStatus())
            .as("Epworth 14 should be DOCUMENTED (min 10)")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        CriterionResult bmiResult = findById(results, "bmi_documented");
        assertThat(bmiResult.getStatus())
            .as("BMI within 6 months should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        CriterionResult symptomResult = findById(results, "sleep_symptom");
        assertThat(symptomResult.getStatus())
            .as("Snoring condition should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);
    }

    @Test
    void testLowEpworth() {
        // Epworth obs 7 -> Epworth MISSING (threshold is 10)
        when(fhirService.getProcedures(anyString())).thenReturn(List.of());
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());
        when(fhirService.getObservations(anyString())).thenReturn(List.of(
            buildObservation("89243-0", 7.0),    // Epworth score - too low
            buildObservation("39156-5", 30.0)    // BMI present
        ));
        when(fhirService.getConditions(anyString())).thenReturn(List.of(
            buildCondition("http://snomed.info/sct", "72863001", "Snoring")
        ));

        List<CriterionResult> results = ruleEvaluator.evaluate(sleepStudyRule, "sleep-low-001");

        CriterionResult epworthResult = findById(results, "epworth_score");
        assertThat(epworthResult.getStatus())
            .as("Epworth score 7 should be MISSING (threshold is 10)")
            .isEqualTo(DocumentationStatus.MISSING);

        CriterionResult bmiResult = findById(results, "bmi_documented");
        assertThat(bmiResult.getStatus())
            .as("BMI present should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);
    }

    // --- helpers ---

    private Observation buildObservation(String loincCode, double value) {
        Observation obs = new Observation();
        obs.setId("obs-" + loincCode + "-001");
        obs.setStatus(Observation.ObservationStatus.FINAL);
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding()
            .setSystem("http://loinc.org")
            .setCode(loincCode));
        obs.setCode(code);
        obs.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
        Quantity qty = new Quantity();
        qty.setValue(value);
        obs.setValue(qty);
        return obs;
    }

    private Condition buildCondition(String system, String code, String display) {
        Condition condition = new Condition();
        condition.setId("cond-" + code + "-001");
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding(new Coding().setSystem(system).setCode(code).setDisplay(display));
        condition.setCode(cc);
        CodeableConcept clinicalStatus = new CodeableConcept();
        clinicalStatus.addCoding(new Coding()
            .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
            .setCode("active"));
        condition.setClinicalStatus(clinicalStatus);
        return condition;
    }

    private CriterionResult findById(List<CriterionResult> results, String id) {
        return results.stream()
            .filter(r -> id.equals(r.getCriterionId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Criterion not found: " + id));
    }
}
