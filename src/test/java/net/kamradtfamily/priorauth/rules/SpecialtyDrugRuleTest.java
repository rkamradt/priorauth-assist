package net.kamradtfamily.priorauth.rules;

import net.kamradtfamily.priorauth.fhir.FhirService;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialtyDrugRuleTest {

    @Mock
    private FhirService fhirService;

    @InjectMocks
    private RuleEvaluator ruleEvaluator;

    private CoverageRule specialtyDrugRule;

    @BeforeEach
    void setUp() throws Exception {
        RuleLoader loader = new RuleLoader();
        Map<String, CoverageRule> rules = loader.loadAll();
        specialtyDrugRule = rules.get("specialty-drug-humira");
        assertThat(specialtyDrugRule).as("specialty-drug-humira rule must be loadable").isNotNull();
    }

    @Test
    void testStepTherapyMet() {
        // two prior DMARD MedicationRequests + RA condition -> all DOCUMENTED
        when(fhirService.getProcedures(anyString())).thenReturn(List.of());
        when(fhirService.getObservations(anyString())).thenReturn(List.of());
        when(fhirService.getConditions(anyString())).thenReturn(List.of(
            buildCondition("http://hl7.org/fhir/sid/icd-10-cm", "M05.79",
                "Rheumatoid arthritis with rheumatoid factor")
        ));
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of(
            buildMedicationRequest("http://www.nlm.nih.gov/research/umls/rxnorm", "41493", "Methotrexate"),
            buildMedicationRequest("http://www.nlm.nih.gov/research/umls/rxnorm", "5521", "Hydroxychloroquine")
        ));

        List<CriterionResult> results = ruleEvaluator.evaluate(specialtyDrugRule, "drug-step-met-001");

        CriterionResult raResult = findById(results, "ra_diagnosis");
        assertThat(raResult.getStatus())
            .as("RA diagnosis should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        CriterionResult dmard1Result = findById(results, "prior_dmard_1");
        assertThat(dmard1Result.getStatus())
            .as("First DMARD should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        CriterionResult dmard2Result = findById(results, "prior_dmard_2");
        assertThat(dmard2Result.getStatus())
            .as("Second DMARD should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);
    }

    @Test
    void testStepTherapyMissing() {
        // no prior DMARDs -> step therapy MISSING
        when(fhirService.getProcedures(anyString())).thenReturn(List.of());
        when(fhirService.getObservations(anyString())).thenReturn(List.of());
        when(fhirService.getConditions(anyString())).thenReturn(List.of(
            buildCondition("http://hl7.org/fhir/sid/icd-10-cm", "M05.79",
                "Rheumatoid arthritis with rheumatoid factor")
        ));
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());

        List<CriterionResult> results = ruleEvaluator.evaluate(specialtyDrugRule, "drug-step-missing-001");

        CriterionResult dmard1Result = findById(results, "prior_dmard_1");
        assertThat(dmard1Result.getStatus())
            .as("No DMARDs should result in MISSING")
            .isEqualTo(DocumentationStatus.MISSING);

        CriterionResult dmard2Result = findById(results, "prior_dmard_2");
        assertThat(dmard2Result.getStatus())
            .as("No DMARDs should result in MISSING")
            .isEqualTo(DocumentationStatus.MISSING);
    }

    // --- helpers ---

    private Condition buildCondition(String system, String code, String display) {
        Condition condition = new Condition();
        condition.setId("cond-" + code.replace(".", "-") + "-001");
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

    private MedicationRequest buildMedicationRequest(String system, String code, String display) {
        MedicationRequest med = new MedicationRequest();
        med.setId("med-" + code + "-001");
        med.setStatus(MedicationRequest.MedicationRequestStatus.COMPLETED);
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding(new Coding().setSystem(system).setCode(code).setDisplay(display));
        med.setMedication(cc);
        return med;
    }

    private CriterionResult findById(List<CriterionResult> results, String id) {
        return results.stream()
            .filter(r -> id.equals(r.getCriterionId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Criterion not found: " + id));
    }
}
