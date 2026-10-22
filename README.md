# priorauth-assist

A Spring Boot 3.4 / Java 21 MCP server that helps clinicians prepare prior-authorization paperwork by querying FHIR R4 patient data and evaluating coverage requirements.

> **Note:** This is a demo project for portfolio purposes. It demonstrates FHIR R4 integration, MCP protocol implementation, and healthcare workflow automation. Live demo: [priorauth.kamradtfamily.net](https://priorauth.kamradtfamily.net)

---

## What This Demonstrates

- **FHIR R4**: Uses the HAPI FHIR client to query a live HAPI FHIR server for Patient, Condition, Observation, Procedure, and MedicationRequest resources. All queries are GET-only — the server never writes to clinical data.
- **MCP Protocol**: Implements five tools using the official MCP Java SDK with both stdio (Claude Desktop) and streamable HTTP (in-cluster) transports.
- **Da Vinci CRD/DTR/PAS**: Conceptually maps to the HL7 Da Vinci use cases — `requirements_for` ≈ CRD (coverage requirements discovery), `prefill_documentation` ≈ DTR (documentation templates and rules), `build_request` ≈ PAS (prior authorization support).
- **Human-in-the-Loop AI**: Claude explains and summarizes; all coverage decisions are made by humans. The `build_request` tool creates a draft that a clinician reviews before submitting.
- **Deterministic evaluation**: Coverage criteria are evaluated by deterministic code. The model can explain the criteria but cannot infer or override them.
- **Audit trail**: Every tool call is logged with timestamp, tool name, patient ID, and caller context.
- **Read-only safety**: All FHIR operations are GET-only; the server never writes to clinical data.

---

## Architecture Diagram

```
Claude (Desktop/Code)          Claude API connector
     |  stdio                       |  HTTPS (Cloudflare Access)
     v                              v
MCP Server (Spring Boot)  <---------  priorauth.kamradtfamily.net
     |
     |  FHIR REST (internal)
     v
HAPI FHIR R4 Server (hapiproject/hapi:v7.4.0)
     |
     |  JDBC
     v
PostgreSQL (shared cluster DB)
```

---

## Coverage Rules

| ID | Service | CPT/HCPCS | Key Requirements |
|----|---------|-----------|-----------------|
| KNEE-MRI-001 | Knee MRI | 73721 | ICD-10 diagnosis (M17.x or S83.x), minimum 6 weeks conservative PT (12 visits), X-ray within 12 months showing no surgical indication |
| LUMBAR-MRI-001 | Lumbar Spine MRI | 72148 | ICD-10 diagnosis (M54.x or M51.x), minimum 6 weeks conservative PT (12 visits), neurological symptoms documented, X-ray within 12 months |
| SHOULDER-MRI-001 | Shoulder MRI | 73221 | ICD-10 diagnosis (M75.x or S40.x), minimum 4 weeks conservative PT (8 visits), X-ray within 12 months |
| PT-AUTH-001 | Extended Physical Therapy | 97110 | Initial PT authorization (8 visits) included; extension requires functional outcome measures, documented progress, and clinical notes every 10 visits |
| INJECT-001 | Joint/Spine Injection | 64483/27096 | Failed conservative therapy documented (PT + NSAIDs), appropriate diagnosis, imaging support, no contraindications |

---

## MCP Tools

| Tool | Arguments | Returns |
|------|-----------|---------|
| `find_patient` | `name` (string) | Patient demographics and FHIR ID |
| `requirements_for` | `service_code` (CPT/HCPCS string) | Coverage rule object with all required documentation criteria |
| `prefill_documentation` | `patient_id`, `service_code` | Structured documentation summary: what is present in the patient record vs. what is missing |
| `missing_items` | `patient_id`, `service_code` | Checklist of items the office still needs to gather before the request will be complete |
| `build_request` | `patient_id`, `service_code` | Draft FHIR Claim bundle (JSON) representing the prior auth request, ready for clinician review |

---

## Local Development

### Step 1: Start FHIR Server

```bash
docker run -d --name hapi-fhir \
  -p 8080:8080 \
  -e spring.datasource.url=jdbc:h2:file:/data/hapi \
  -e spring.jpa.hibernate.ddl-auto=update \
  hapiproject/hapi:v7.4.0
```

Wait for the server to be ready (check `http://localhost:8080/fhir/metadata`). Initial startup takes about 60 seconds.

### Step 2: Load Demo Patients

```bash
FHIR_BASE_URL=http://localhost:8080/fhir bash fhir-data/load-data.sh
```

This loads three demo patients with varying levels of documentation completeness:
- **Anna Kowalski** — knee MRI, fully documented (7 weeks PT, X-ray present)
- **Marcus Webb** — knee MRI, partial documentation (4 weeks PT, no X-ray)
- **Carol Simmons** — lumbar MRI, missing PT entirely

### Step 3: Build and Run MCP Server (HTTP Mode)

```bash
mvn package -DskipTests
FHIR_BASE_URL=http://localhost:8080/fhir java -jar target/priorauth-assist-*.jar
```

The MCP server listens on `http://localhost:8080/mcp` in HTTP transport mode.

### Step 4: Run as stdio MCP (for Claude Desktop)

See `claude_desktop_config.json` in the repo root. Merge the `mcpServers` block into your `~/Library/Application Support/Claude/claude_desktop_config.json`.

---

## Cluster Deployment

1. **Create PostgreSQL database:**
   ```sql
   CREATE DATABASE priorauth_fhir;
   CREATE USER priorauth_fhir WITH PASSWORD 'your-secure-password';
   GRANT ALL ON SCHEMA public TO priorauth_fhir;
   ```

2. **Copy helm charts** from `helm/` to `rkamradt-helm-charts/`:
   ```
   helm/priorauth-fhir/    -> rkamradt-helm-charts/priorauth-fhir/
   helm/priorauth-assist/  -> rkamradt-helm-charts/priorauth-assist/
   ```

3. **Add entries to `rkamradt-helm-charts/apps/values.yaml`:**
   ```yaml
   - name: priorauth-fhir
     namespace: priorauth-fhir
     path: priorauth-fhir
   - name: priorauth-assist
     namespace: priorauth-assist
     path: priorauth-assist
   ```

4. **Create secrets in their namespaces:**
   ```bash
   kubectl create secret generic priorauth-fhir-secret \
     -n priorauth-fhir \
     --from-literal=DB_PASSWORD='your-secure-password'

   kubectl create secret generic priorauth-secret \
     -n priorauth-assist \
     --from-literal=CF_ACCESS_CLIENT_ID='your-cf-client-id' \
     --from-literal=CF_ACCESS_CLIENT_SECRET='your-cf-client-secret'
   ```

5. **Add Cloudflare route** in Zero Trust dashboard:
   Networks → Tunnels → [tunnel] → Published application routes → Add a route:
   `priorauth.kamradtfamily.net` → `http://priorauth-assist.priorauth-assist:8080`

6. **Set `HELM_CHARTS_PAT` secret** on this repo (use the PyNaCl script in DEPLOYING.md), then push to trigger GitHub Actions.

7. **Copy `ghcr-secret`** into the `priorauth-assist` namespace after ArgoCD creates it:
   ```bash
   kubectl get secret ghcr-secret -n default -o yaml \
     | sed 's/namespace: default/namespace: priorauth-assist/' \
     | kubectl apply -f -
   ```

8. **Load demo data** using kubectl port-forward to the cluster FHIR server:
   ```bash
   kubectl port-forward -n priorauth-fhir svc/priorauth-fhir 8081:8080
   FHIR_BASE_URL=http://localhost:8081/fhir bash fhir-data/load-data.sh
   ```

---

## Generating Synthea Data

See [fhir-data/README-synthea.md](fhir-data/README-synthea.md) for instructions on generating realistic synthetic patient data using [Synthea](https://github.com/synthetichealth/synthea) and loading it into the FHIR server.

---

## Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires running FHIR server with demo data loaded)
FHIR_BASE_URL=http://localhost:8080/fhir mvn verify -Pintegration
```
