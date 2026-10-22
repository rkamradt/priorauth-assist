package net.kamradtfamily.priorauth.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirClientConfig {

    @Value("${priorauth.fhir.base-url}")
    private String fhirBaseUrl;

    @Bean
    public IGenericClient fhirClient() {
        FhirContext ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setSocketTimeout(30_000);
        return ctx.newRestfulGenericClient(fhirBaseUrl);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
