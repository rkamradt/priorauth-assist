package net.kamradtfamily.priorauth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.sdk.McpServer;
import io.modelcontextprotocol.sdk.McpServerFeatures;
import io.modelcontextprotocol.sdk.McpSyncServer;
import io.modelcontextprotocol.sdk.server.transport.StdioServerTransport;
import io.modelcontextprotocol.sdk.server.transport.WebFluxSseServerTransport;
import io.modelcontextprotocol.sdk.types.ServerCapabilities;
import io.modelcontextprotocol.sdk.types.Tool;
import net.kamradtfamily.priorauth.tool.ToolHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

@Configuration
public class McpServerConfig {

    private static final String DISCLAIMER =
        " This tool assists documentation only. It makes no coverage determination." +
        " A human reviews and submits.";

    @Value("${priorauth.mcp.transport:http}")
    private String transport;

    /**
     * HTTP transport bean — null in stdio mode so the HTTP server is not started.
     */
    @Bean
    public WebFluxSseServerTransport webFluxSseServerTransport(ObjectMapper objectMapper) {
        if ("stdio".equalsIgnoreCase(transport)) {
            return null;
        }
        return new WebFluxSseServerTransport(objectMapper, "/mcp");
    }

    /**
     * MCP server — wired to the appropriate transport depending on ${priorauth.mcp.transport}.
     */
    @Bean
    public McpSyncServer mcpSyncServer(
            ToolHandler toolHandler,
            ObjectMapper objectMapper,
            WebFluxSseServerTransport webFluxSseServerTransport) {

        List<McpServerFeatures.SyncToolSpecification> tools = buildTools(toolHandler, objectMapper);

        if ("stdio".equalsIgnoreCase(transport)) {
            StdioServerTransport stdioTransport = new StdioServerTransport();
            return McpServer.sync(stdioTransport)
                .serverInfo("priorauth-assist", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(tools)
                .build();
        }

        return McpServer.sync(webFluxSseServerTransport)
            .serverInfo("priorauth-assist", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .tools(tools)
            .build();
    }

    /**
     * Expose the WebFlux router so Spring WebFlux picks up the SSE and message endpoints.
     */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebFluxSseServerTransport webFluxSseServerTransport) {
        if (webFluxSseServerTransport == null) {
            return null;
        }
        return webFluxSseServerTransport.getRouterFunction();
    }

    // ---- tool registrations ----

    private List<McpServerFeatures.SyncToolSpecification> buildTools(
            ToolHandler toolHandler, ObjectMapper objectMapper) {
        return List.of(
            new McpServerFeatures.SyncToolSpecification(
                new Tool("find_patient",
                    "Search for a patient by name or FHIR id." + DISCLAIMER,
                    singleParamSchema(objectMapper, "query", "Patient name or FHIR id")),
                toolHandler::handleFindPatient
            ),
            new McpServerFeatures.SyncToolSpecification(
                new Tool("requirements_for",
                    "Return the coverage criteria for a service or procedure." + DISCLAIMER,
                    singleParamSchema(objectMapper, "service", "Plain service name, e.g. 'knee MRI'")),
                toolHandler::handleRequirementsFor
            ),
            new McpServerFeatures.SyncToolSpecification(
                new Tool("prefill_documentation",
                    "Evaluate each prior-auth criterion for a patient and service, " +
                    "returning status for each." + DISCLAIMER,
                    patientServiceSchema(objectMapper)),
                toolHandler::handlePrefillDocumentation
            ),
            new McpServerFeatures.SyncToolSpecification(
                new Tool("missing_items",
                    "Return a markdown checklist of MISSING and PARTIAL documentation items." + DISCLAIMER,
                    patientServiceSchema(objectMapper)),
                toolHandler::handleMissingItems
            ),
            new McpServerFeatures.SyncToolSpecification(
                new Tool("build_request",
                    "Assemble a draft FHIR Claim with supporting info for documented criteria." + DISCLAIMER,
                    patientServiceSchema(objectMapper)),
                toolHandler::handleBuildRequest
            )
        );
    }

    private ObjectNode singleParamSchema(ObjectMapper mapper, String paramName, String description) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        ObjectNode param = props.putObject(paramName);
        param.put("type", "string");
        param.put("description", description);
        root.putArray("required").add(paramName);
        return root;
    }

    private ObjectNode patientServiceSchema(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        ObjectNode pid = props.putObject("patient_id");
        pid.put("type", "string");
        pid.put("description", "FHIR Patient id");
        ObjectNode svc = props.putObject("service");
        svc.put("type", "string");
        svc.put("description", "Plain service name, e.g. 'knee MRI'");
        root.putArray("required").add("patient_id").add("service");
        return root;
    }
}
