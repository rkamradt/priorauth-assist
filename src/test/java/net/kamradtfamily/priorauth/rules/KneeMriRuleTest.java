package net.kamradtfamily.priorauth.rules;

import net.kamradtfamily.priorauth.fhir.FhirService;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KneeMriRuleTest {

    @Mock
    private FhirService fhirService;

    @InjectMocks
    private RuleEvaluator ruleEvaluator;

    private CoverageRule kneeMriRule;

    @BeforeEach
    void setUp() throws Exception {
        RuleLoader loader = new RuleLoader();
        Map<String, CoverageRule> rules = loader.loadAll();
        kneeMriRule = rules.get("knee-mri");
        assertThat(kneeMriRule).as("knee-mri rule must be loadable").isNotNull();
    }

    @Test
    void testCompleteDocumentation() {
        // 14 PT procedures + X-ray procedure + pain obs 7/10 -> all DOCUMENTED
        when(fhirService.getProcedures(anyString())).thenReturn(buildPtProcedures(14, "knee-complete-001"));
        when(fhirService.getObservations(anyString())).thenReturn(List.of(buildPainObservation(7.0)));
        when(fhirService.getConditions(anyString())).thenReturn(List.of());
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());

        List<CriterionResult> results = ruleEvaluator.evaluate(kneeMriRule, "knee-complete-001");

        assertThat(results).isNotEmpty();
        // PT visits criterion
        CriterionResult ptResult = findById(results, "pt_duration");
        assertThat(ptResult.getStatus())
            .as("14 PT visits should be DOCUMENTED (need 12)")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        // X-ray criterion — provided by the 14-procedure list which includes an X-ray
        // For this scenario the procedures include the X-ray in the list
        CriterionResult xrayResult = findById(results, "prior_xray");
        assertThat(xrayResult.getStatus())
            .as("X-ray should be DOCUMENTED")
            .isEqualTo(DocumentationStatus.DOCUMENTED);

        // Pain score
        CriterionResult painResult = findById(results, "pain_score");
        assertThat(painResult.getStatus())
            .as("Pain score 7 should be DOCUMENTED (min 4)")
            .isEqualTo(DocumentationStatus.DOCUMENTED);
    }

    @Test
    void testPartialDocumentation() {
        // 8 PT procedures + no X-ray -> PT PARTIAL, X-ray MISSING, pain MISSING
        when(fhirService.getProcedures(anyString()))
            .thenReturn(buildPtProceduresOnly(8, "knee-partial-001"));
        when(fhirService.getObservations(anyString())).thenReturn(List.of());
        when(fhirService.getConditions(anyString())).thenReturn(List.of());
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());

        List<CriterionResult> results = ruleEvaluator.evaluate(kneeMriRule, "knee-partial-001");

        CriterionResult ptResult = findById(results, "pt_duration");
        assertThat(ptResult.getStatus())
            .as("8 PT visits should be PARTIAL (need 12)")
            .isEqualTo(DocumentationStatus.PARTIAL);

        CriterionResult xrayResult = findById(results, "prior_xray");
        assertThat(xrayResult.getStatus())
            .as("No X-ray should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);
    }

    @Test
    void testMissingDocumentation() {
        // 0 PT procedures, no X-ray -> all MISSING
        when(fhirService.getProcedures(anyString())).thenReturn(List.of());
        when(fhirService.getObservations(anyString())).thenReturn(List.of());
        when(fhirService.getConditions(anyString())).thenReturn(List.of());
        when(fhirService.getMedicationRequests(anyString())).thenReturn(List.of());

        List<CriterionResult> results = ruleEvaluator.evaluate(kneeMriRule, "knee-missing-001");

        CriterionResult ptResult = findById(results, "pt_duration");
        assertThat(ptResult.getStatus())
            .as("0 PT visits should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);

        CriterionResult xrayResult = findById(results, "prior_xray");
        assertThat(xrayResult.getStatus())
            .as("No X-ray should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);

        CriterionResult painResult = findById(results, "pain_score");
        assertThat(painResult.getStatus())
            .as("No pain obs should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);
    }

    // --- helpers ---

    private List<Procedure> buildPtProcedures(int ptCount, String patientId) {
        List<Procedure> procs = new ArrayList<>(buildPtProceduresOnly(ptCount, patientId));
        procs.add(buildXrayProcedure(patientId));
        return procs;
    }

    private List<Procedure> buildPtProceduresOnly(int count, String patientId) {
        List<Procedure> procs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Procedure proc = new Procedure();
            proc.setId(patientId + "-pt-" + String.format("%03d", i));
            proc.setStatus(Procedure.ProcedureStatus.COMPLETED);
            CodeableConcept code = new CodeableConcept();
            code.addCoding(new Coding()
                .setSystem("http://snomed.info/sct")
                .setCode("229070002")
                .setDisplay("Physical therapy procedure"));
            proc.setCode(code);
            // date within 90 days
            proc.setPerformed(new org.hl7.fhir.r4.model.DateTimeType(
                new Date(System.currentTimeMillis() - (long)(i * 5) * 86400000L)));
            procs.add(proc);
        }
        return procs;
    }

    private Procedure buildXrayProcedure(String patientId) {
        Procedure proc = new Procedure();
        proc.setId(patientId + "-xray-001");
        proc.setStatus(Procedure.ProcedureStatus.COMPLETED);
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding()
            .setSystem("http://loinc.org")
            .setCode("24727-8")
            .setDisplay("XR Knee 2 views"));
        proc.setCode(code);
        proc.setPerformed(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
        return proc;
    }

    private Observation buildPainObservation(double value) {
        Observation obs = new Observation();
        obs.setId("pain-obs-001");
        obs.setStatus(Observation.ObservationStatus.FINAL);
        CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding()
            .setSystem("http://loinc.org")
            .setCode("72514-3")
            .setDisplay("Pain severity - Verbal numeric rating scale"));
        obs.setCode(code);
        obs.setEffective(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
        Quantity qty = new Quantity();
        qty.setValue(value);
        obs.setValue(qty);
        return obs;
    }

    private CriterionResult findById(List<CriterionResult> results, String id) {
        return results.stream()
            .filter(r -> id.equals(r.getCriterionId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Criterion not found: " + id));
    }
}
