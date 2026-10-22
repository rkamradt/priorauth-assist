package net.kamradtfamily.priorauth.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.sdk.McpSyncServerExchange;
import io.modelcontextprotocol.sdk.types.CallToolResult;
import io.modelcontextprotocol.sdk.types.TextContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kamradtfamily.priorauth.audit.AuditLogger;
import net.kamradtfamily.priorauth.fhir.FhirService;
import net.kamradtfamily.priorauth.rules.CoverageRule;
import net.kamradtfamily.priorauth.rules.CriterionResult;
import net.kamradtfamily.priorauth.rules.DocumentationStatus;
import net.kamradtfamily.priorauth.rules.RuleEvaluator;
import net.kamradtfamily.priorauth.rules.RuleLoader;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolHandler {

    private final FhirService fhirService;
    private final RuleLoader ruleLoader;
    private final RuleEvaluator ruleEvaluator;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public CallToolResult handleFindPatient(McpSyncServerExchange exchange, Map<String, Object> args) {
        auditLogger.log("find_patient", args, callerContext(exchange));
        String query = (String) args.get("query");
        try {
            List<Patient> patients = fhirService.searchPatients(query);
            ArrayNode arr = objectMapper.createArrayNode();
            for (Patient p : patients) {
                ObjectNode node = arr.addObject();
                node.put("id", p.getIdElement().getIdPart());
                node.put("name", formatName(p));
                node.put("birthDate", p.getBirthDateElement().getValueAsString());
                node.put("gender", p.getGender() != null ? p.getGender().toCode() : "unknown");
            }
            return success(arr.toString());
        } catch (Exception e) {
            log.error("find_patient failed", e);
            return error("find_patient failed: " + e.getMessage());
        }
    }

    public CallToolResult handleRequirementsFor(McpSyncServerExchange exchange, Map<String, Object> args) {
        auditLogger.log("requirements_for", args, callerContext(exchange));
        String service = (String) args.get("service");
        try {
            CoverageRule rule = ruleLoader.findByService(service);
            if (rule == null) {
                return error("No coverage rule found for service: " + service);
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("id", rule.getId());
            result.put("version", rule.getVersion());
            result.put("service", rule.getService());
            result.put("requiresPriorAuth", rule.isRequiresPriorAuth());
            result.put("plainText", rule.getPlainText());
            ArrayNode criteriaArr = result.putArray("criteria");
            if (rule.getCriteria() != null) {
                for (var criterion : rule.getCriteria()) {
                    ObjectNode c = criteriaArr.addObject();
                    c.put("id", criterion.getId());
                    c.put("type", criterion.getType());
                    c.put("description", criterion.getDescription());
                }
            }
            return success(result.toString());
        } catch (Exception e) {
            log.error("requirements_for failed", e);
            return error("requirements_for failed: " + e.getMessage());
        }
    }

    public CallToolResult handlePrefillDocumentation(McpSyncServerExchange exchange, Map<String, Object> args) {
        auditLogger.log("prefill_documentation", args, callerContext(exchange));
        String patientId = (String) args.get("patient_id");
        String service = (String) args.get("service");
        try {
            CoverageRule rule = ruleLoader.findByService(service);
            if (rule == null) {
                return error("No coverage rule found for service: " + service);
            }
            List<CriterionResult> results = ruleEvaluator.evaluate(rule, patientId);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("patientId", patientId);
            result.put("service", rule.getService());
            result.put("ruleVersion", rule.getVersion());
            ArrayNode items = result.putArray("items");
            for (CriterionResult cr : results) {
                ObjectNode item = items.addObject();
                item.put("criterionId", cr.getCriterionId());
                item.put("description", cr.getDescription());
                item.put("status", cr.getStatus().name());
                item.put("detail", cr.getDetail());
                ArrayNode refs = item.putArray("evidenceRefs");
                if (cr.getEvidenceRefs() != null) {
                    cr.getEvidenceRefs().forEach(refs::add);
                }
            }
            return success(result.toString());
        } catch (Exception e) {
            log.error("prefill_documentation failed", e);
            return error("prefill_documentation failed: " + e.getMessage());
        }
    }

    public CallToolResult handleMissingItems(McpSyncServerExchange exchange, Map<String, Object> args) {
        auditLogger.log("missing_items", args, callerContext(exchange));
        String patientId = (String) args.get("patient_id");
        String service = (String) args.get("service");
        try {
            CoverageRule rule = ruleLoader.findByService(service);
            if (rule == null) {
                return error("No coverage rule found for service: " + service);
            }
            List<CriterionResult> results = ruleEvaluator.evaluate(rule, patientId);
            StringBuilder sb = new StringBuilder();
            sb.append("## Missing / Incomplete Documentation\n\n");
            sb.append("**Patient:** ").append(patientId).append("  \n");
            sb.append("**Service:** ").append(rule.getService()).append("\n\n");

            List<CriterionResult> missing = results.stream()
                .filter(r -> r.getStatus() != DocumentationStatus.DOCUMENTED)
                .toList();

            if (missing.isEmpty()) {
                sb.append("All documentation criteria are met.\n");
            } else {
                for (CriterionResult cr : missing) {
                    String marker = cr.getStatus() == DocumentationStatus.PARTIAL ? "- [ ] (PARTIAL)" : "- [ ]";
                    sb.append(marker).append(" **").append(cr.getDescription()).append("**\n");
                    sb.append("  - ").append(cr.getDetail()).append("\n");
                }
            }
            return success(sb.toString());
        } catch (Exception e) {
            log.error("missing_items failed", e);
            return error("missing_items failed: " + e.getMessage());
        }
    }

    public CallToolResult handleBuildRequest(McpSyncServerExchange exchange, Map<String, Object> args) {
        auditLogger.log("build_request", args, callerContext(exchange));
        String patientId = (String) args.get("patient_id");
        String service = (String) args.get("service");
        try {
            CoverageRule rule = ruleLoader.findByService(service);
            if (rule == null) {
                return error("No coverage rule found for service: " + service);
            }
            List<CriterionResult> results = ruleEvaluator.evaluate(rule, patientId);
            ObjectNode claim = buildDraftClaim(patientId, rule, results);
            return success(claim.toPrettyString());
        } catch (Exception e) {
            log.error("build_request failed", e);
            return error("build_request failed: " + e.getMessage());
        }
    }

    private ObjectNode buildDraftClaim(String patientId, CoverageRule rule, List<CriterionResult> results) {
        ObjectNode claim = objectMapper.createObjectNode();
        claim.put("resourceType", "Claim");
        claim.put("status", "draft");
        claim.put("use", "preauthorization");
        claim.put("note", "DRAFT — for human review only. Not submitted.");

        ObjectNode patient = claim.putObject("patient");
        patient.put("reference", "Patient/" + patientId);

        ArrayNode diagnoses = claim.putArray("diagnosis");
        ObjectNode diag = diagnoses.addObject();
        diag.put("sequence", 1);
        ObjectNode diagCode = diag.putObject("diagnosisCodeableConcept");
        diagCode.put("text", rule.getService());

        ArrayNode procedures = claim.putArray("procedure");
        if (rule.getServiceCodes() != null) {
            int seq = 1;
            for (String code : rule.getServiceCodes()) {
                ObjectNode proc = procedures.addObject();
                proc.put("sequence", seq++);
                ObjectNode procCode = proc.putObject("procedureCodeableConcept");
                ArrayNode codings = procCode.putArray("coding");
                ObjectNode coding = codings.addObject();
                coding.put("system", "http://www.ama-assn.org/go/cpt");
                coding.put("code", code);
            }
        }

        ArrayNode supportingInfo = claim.putArray("supportingInfo");
        int seq = 1;
        for (CriterionResult cr : results) {
            if (cr.getStatus() == DocumentationStatus.DOCUMENTED) {
                ObjectNode si = supportingInfo.addObject();
                si.put("sequence", seq++);
                ObjectNode category = si.putObject("category");
                category.put("text", cr.getDescription());
                si.put("valueString", cr.getDetail());
                ArrayNode refs = si.putArray("evidenceRefs");
                if (cr.getEvidenceRefs() != null) {
                    cr.getEvidenceRefs().forEach(refs::add);
                }
            }
        }

        return claim;
    }

    private String formatName(Patient patient) {
        if (patient.getName() == null || patient.getName().isEmpty()) {
            return "Unknown";
        }
        HumanName name = patient.getName().get(0);
        StringBuilder sb = new StringBuilder();
        if (name.getFamily() != null) sb.append(name.getFamily());
        if (!name.getGiven().isEmpty()) {
            sb.append(", ").append(name.getGiven().get(0).getValue());
        }
        return sb.toString();
    }

    private String callerContext(McpSyncServerExchange exchange) {
        if (exchange == null) return "unknown";
        try {
            var info = exchange.getClientInfo();
            if (info != null) {
                return info.name() + "/" + info.version();
            }
        } catch (Exception ignored) {
            // exchange API varies by SDK version; fall through
        }
        return "unknown";
    }

    private CallToolResult success(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false);
    }

    private CallToolResult error(String message) {
        return new CallToolResult(List.of(new TextContent(message)), true);
    }
}
