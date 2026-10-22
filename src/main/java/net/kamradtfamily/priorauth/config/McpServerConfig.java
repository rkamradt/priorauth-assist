package net.kamradtfamily.priorauth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
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
    public WebFluxSseServerTransportProvider webFluxSseServerTransport(ObjectMapper objectMapper) {
        if ("stdio".equalsIgnoreCase(transport)) {
            return null;
        }
        return new WebFluxSseServerTransportProvider(objectMapper, "/mcp");
    }

    /**
     * MCP server — wired to the appropriate transport depending on ${priorauth.mcp.transport}.
     */
    @Bean
    public McpSyncServer mcpSyncServer(
            ToolHandler toolHandler,
            ObjectMapper objectMapper,
            WebFluxSseServerTransportProvider webFluxSseServerTransport) {

        List<McpServerFeatures.SyncToolSpecification> tools = buildTools(toolHandler, objectMapper);

        McpSchema.ServerCapabilities caps = McpSchema.ServerCapabilities.builder()
            .tools(false)
            .build();

        if ("stdio".equalsIgnoreCase(transport)) {
            var stdioTransport = new StdioServerTransportProvider();
            return McpServer.sync(stdioTransport)
                .serverInfo("priorauth-assist", "1.0.0")
                .capabilities(caps)
                .tools(tools)
                .build();
        }

        return McpServer.sync(webFluxSseServerTransport)
            .serverInfo("priorauth-assist", "1.0.0")
            .capabilities(caps)
            .tools(tools)
            .build();
    }

    /**
     * Expose the WebFlux router so Spring WebFlux picks up the SSE and message endpoints.
     */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebFluxSseServerTransportProvider webFluxSseServerTransport) {
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
                new McpSchema.Tool("find_patient",
                    "Search for a patient by name or FHIR id." + DISCLAIMER,
                    singleParamSchema("query", "Patient name or FHIR id")),
                toolHandler::handleFindPatient
            ),
            new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("requirements_for",
                    "Return the coverage criteria for a service or procedure." + DISCLAIMER,
                    singleParamSchema("service", "Plain service name, e.g. 'knee MRI'")),
                toolHandler::handleRequirementsFor
            ),
            new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("prefill_documentation",
                    "Evaluate each prior-auth criterion for a patient and service, " +
                    "returning status for each." + DISCLAIMER,
                    patientServiceSchema()),
                toolHandler::handlePrefillDocumentation
            ),
            new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("missing_items",
                    "Return a markdown checklist of MISSING and PARTIAL documentation items." + DISCLAIMER,
                    patientServiceSchema()),
                toolHandler::handleMissingItems
            ),
            new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("build_request",
                    "Assemble a draft FHIR Claim with supporting info for documented criteria." + DISCLAIMER,
                    patientServiceSchema()),
                toolHandler::handleBuildRequest
            )
        );
    }

    private String singleParamSchema(String paramName, String description) {
        return """
            {
              "type": "object",
              "properties": {
                "%s": { "type": "string", "description": "%s" }
              },
              "required": ["%s"]
            }
            """.formatted(paramName, description, paramName);
    }

    private String patientServiceSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "patient_id": { "type": "string", "description": "FHIR Patient id" },
                "service":    { "type": "string", "description": "Plain service name, e.g. 'knee MRI'" }
              },
              "required": ["patient_id", "service"]
            }
            """;
    }
}
