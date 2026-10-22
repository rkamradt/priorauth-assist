package net.kamradtfamily.priorauth.integration;

import net.kamradtfamily.priorauth.rules.CoverageRule;
import net.kamradtfamily.priorauth.rules.CriterionResult;
import net.kamradtfamily.priorauth.rules.DocumentationStatus;
import net.kamradtfamily.priorauth.rules.RuleEvaluator;
import net.kamradtfamily.priorauth.rules.RuleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests — only run when FHIR_BASE_URL is set and -Pintegration profile is active.
 * Requires a live HAPI FHIR server with demo patients pre-loaded.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "priorauth.mcp.transport=http"
})
@EnabledIfEnvironmentVariable(named = "FHIR_BASE_URL", matches = ".+")
class RunDemoScenariosIT {

    @Autowired
    private RuleEvaluator ruleEvaluator;

    @Autowired
    private RuleLoader ruleLoader;

    private CoverageRule kneeMriRule;
    private CoverageRule lumbarMriRule;
    private CoverageRule sleepStudyRule;
    private CoverageRule specialtyDrugRule;

    @BeforeEach
    void setUp() {
        kneeMriRule = ruleLoader.getRuleCache().get("knee-mri");
        lumbarMriRule = ruleLoader.getRuleCache().get("lumbar-mri");
        sleepStudyRule = ruleLoader.getRuleCache().get("sleep-study");
        specialtyDrugRule = ruleLoader.getRuleCache().get("specialty-drug-humira");

        assertThat(kneeMriRule).isNotNull();
        assertThat(lumbarMriRule).isNotNull();
        assertThat(sleepStudyRule).isNotNull();
        assertThat(specialtyDrugRule).isNotNull();
    }

    @Test
    void scenario1_kneeCompletePatient_allCriteriaDocumented() {
        List<CriterionResult> results = ruleEvaluator.evaluate(kneeMriRule, "knee-complete-001");

        assertAllDocumented(results, "knee-complete-001");
    }

    @Test
    void scenario2_kneePartialPatient_ptPartialImagingMissing() {
        List<CriterionResult> results = ruleEvaluator.evaluate(kneeMriRule, "knee-partial-001");

        CriterionResult ptResult = findById(results, "pt_duration");
        assertThat(ptResult.getStatus())
            .as("knee-partial-001 PT should be PARTIAL (8 visits, need 12)")
            .isEqualTo(DocumentationStatus.PARTIAL);

        CriterionResult xrayResult = findById(results, "prior_xray");
        assertThat(xrayResult.getStatus())
            .as("knee-partial-001 imaging should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);
    }

    @Test
    void scenario3_lumbarMissingPatient_ptMissing() {
        List<CriterionResult> results = ruleEvaluator.evaluate(lumbarMriRule, "lumbar-missing-001");

        CriterionResult ptResult = findById(results, "pt_duration");
        assertThat(ptResult.getStatus())
            .as("lumbar-missing-001 PT should be MISSING")
            .isEqualTo(DocumentationStatus.MISSING);
    }

    @Test
    void scenario4_sleepCompletePatient_allDocumented() {
        List<CriterionResult> results = ruleEvaluator.evaluate(sleepStudyRule, "sleep-complete-001");

        assertAllDocumented(results, "sleep-complete-001");
    }

    @Test
    void scenario5_drugStepMetPatient_allDocumented() {
        List<CriterionResult> results = ruleEvaluator.evaluate(specialtyDrugRule, "drug-step-met-001");

        assertAllDocumented(results, "drug-step-met-001");
    }

    // --- helpers ---

    private void assertAllDocumented(List<CriterionResult> results, String patientId) {
        assertThat(results).isNotEmpty();
        for (CriterionResult result : results) {
            assertThat(result.getStatus())
                .as("Patient %s criterion %s should be DOCUMENTED but was %s: %s",
                    patientId, result.getCriterionId(), result.getStatus(), result.getDetail())
                .isEqualTo(DocumentationStatus.DOCUMENTED);
        }
    }

    private CriterionResult findById(List<CriterionResult> results, String id) {
        return results.stream()
            .filter(r -> id.equals(r.getCriterionId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Criterion not found: " + id));
    }
}
