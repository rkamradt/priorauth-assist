# Generating Realistic Patient Data with Synthea

The five hand-crafted bundles in `demo-patients/` cover specific prior-auth scenarios
for demo and unit-testing purposes. For load testing or broader coverage, use Synthea
to generate realistic synthetic FHIR R4 patient populations.

---

## 1. Download Synthea

Download the latest pre-built JAR from the Synthea GitHub releases page:

```
https://github.com/synthetichealth/synthea/releases/latest
```

Download the file named `synthea-with-dependencies.jar`. Save it to a local directory,
for example `fhir-data/synthea/`.

```bash
mkdir -p fhir-data/synthea
cd fhir-data/synthea
curl -L -o synthea-with-dependencies.jar \
  https://github.com/synthetichealth/synthea/releases/latest/download/synthea-with-dependencies.jar
```

Requires Java 11 or later. Check with `java -version`.

---

## 2. Generate Patients

Run Synthea to generate approximately 50 patients in FHIR R4 format:

```bash
java -jar synthea/synthea-with-dependencies.jar \
  -p 50 \
  --exporter.fhir.export=true \
  --exporter.fhir.transaction_bundle=true
```

Key flags:
- `-p 50` — generate 50 patients
- `--exporter.fhir.export=true` — write FHIR R4 bundles (enabled by default in recent versions; include explicitly for clarity)
- `--exporter.fhir.transaction_bundle=true` — write transaction bundles compatible with the HAPI FHIR server's batch endpoint

To target a specific state for more realistic demographics:

```bash
java -jar synthea/synthea-with-dependencies.jar Maine -p 50 \
  --exporter.fhir.export=true \
  --exporter.fhir.transaction_bundle=true
```

---

## 3. Locate the Output

Synthea writes FHIR bundles to:

```
output/fhir/
```

relative to the directory where you ran the command. Each file is one patient bundle
named like `Aaron697_Stokes453_....json`, plus a `hospitalInformation*.json` and
`practitionerInformation*.json`. The `load-synthea.sh` script handles all `.json` files
in the directory, including those metadata bundles.

If you ran the command from `fhir-data/synthea/`, the output is at:

```
fhir-data/synthea/output/fhir/
```

which is the default path expected by `load-synthea.sh`.

---

## 4. Load into the FHIR Server

Use the provided `load-synthea.sh` script:

```bash
# Default: reads from fhir-data/synthea/output/fhir/
FHIR_BASE_URL=http://localhost:8080/fhir bash fhir-data/load-synthea.sh

# Or specify a custom output directory
FHIR_BASE_URL=http://localhost:8080/fhir \
SYNTHEA_OUTPUT_DIR=/path/to/synthea/output/fhir \
bash fhir-data/load-synthea.sh
```

The script POSTs each bundle to the FHIR `$FHIR_BASE_URL` root endpoint (the transaction
bundle endpoint). It reports per-file HTTP status and exits non-zero if any load fails.

---

## 5. .gitignore Note

Generated Synthea output is excluded from version control because the bundles are large
(often several MB per patient). The `.gitignore` at the repo root contains:

```
fhir-data/synthea/output/
```

Do not commit Synthea-generated bundles. The hand-crafted bundles in `demo-patients/`
are intentionally small and are checked in.

---

## Further Configuration

Synthea supports extensive configuration for disease prevalence, age distributions,
time ranges, and more. See the full documentation:

- GitHub: https://github.com/synthetichealth/synthea
- Wiki: https://github.com/synthetichealth/synthea/wiki
- Configuration options: https://github.com/synthetichealth/synthea/wiki/Common-Configuration

To generate patients with a higher likelihood of musculoskeletal or sleep conditions
(relevant to the prior-auth rules in this project), consider using Synthea's
`--prevalenceDataFile` or module-level configuration.
