package net.kamradtfamily.priorauth.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FhirService {

    private final IGenericClient fhirClient;

    public List<Patient> searchPatients(String query) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(Patient.class)
                .where(Patient.NAME.matches().value(query))
                .returnBundle(Bundle.class)
                .execute();
            return extractResources(bundle, Patient.class);
        } catch (ResourceNotFoundException e) {
            log.debug("No patients found for query: {}", query);
            return List.of();
        }
    }

    public Patient getPatient(String id) {
        try {
            return fhirClient.read()
                .resource(Patient.class)
                .withId(id)
                .execute();
        } catch (ResourceNotFoundException e) {
            log.warn("Patient not found: {}", id);
            return null;
        }
    }

    public List<Condition> getConditions(String patientId) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(Condition.class)
                .where(Condition.PATIENT.hasId(patientId))
                .where(Condition.CLINICAL_STATUS.exactly().code("active"))
                .returnBundle(Bundle.class)
                .execute();
            return extractResources(bundle, Condition.class);
        } catch (ResourceNotFoundException e) {
            log.debug("No conditions found for patient: {}", patientId);
            return List.of();
        }
    }

    public List<Observation> getObservations(String patientId) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(Observation.class)
                .where(Observation.PATIENT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute();
            return extractResources(bundle, Observation.class);
        } catch (ResourceNotFoundException e) {
            log.debug("No observations found for patient: {}", patientId);
            return List.of();
        }
    }

    public List<Procedure> getProcedures(String patientId) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(Procedure.class)
                .where(Procedure.PATIENT.hasId(patientId))
                .where(Procedure.STATUS.exactly().code("completed"))
                .returnBundle(Bundle.class)
                .execute();
            return extractResources(bundle, Procedure.class);
        } catch (ResourceNotFoundException e) {
            log.debug("No procedures found for patient: {}", patientId);
            return List.of();
        }
    }

    public List<MedicationRequest> getMedicationRequests(String patientId) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(MedicationRequest.class)
                .where(MedicationRequest.PATIENT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute();
            return extractResources(bundle, MedicationRequest.class);
        } catch (ResourceNotFoundException e) {
            log.debug("No medication requests found for patient: {}", patientId);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> extractResources(Bundle bundle, Class<T> resourceType) {
        List<T> results = new ArrayList<>();
        if (bundle == null || bundle.getEntry() == null) {
            return results;
        }
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() != null && resourceType.isInstance(entry.getResource())) {
                results.add((T) entry.getResource());
            }
        }
        return results;
    }
}
