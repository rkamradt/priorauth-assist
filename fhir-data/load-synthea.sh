#!/usr/bin/env bash
set -euo pipefail

FHIR_BASE="${FHIR_BASE_URL:-http://localhost:8080/fhir}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INPUT_DIR="${SYNTHEA_OUTPUT_DIR:-$SCRIPT_DIR/synthea/output/fhir}"

if [[ ! -d "$INPUT_DIR" ]]; then
    echo "ERROR: Synthea output directory not found: $INPUT_DIR"
    echo "Run Synthea first, or set SYNTHEA_OUTPUT_DIR to the correct path."
    exit 1
fi

shopt -s nullglob
bundle_files=("$INPUT_DIR"/*.json)
shopt -u nullglob

if [[ ${#bundle_files[@]} -eq 0 ]]; then
    echo "ERROR: No JSON files found in $INPUT_DIR"
    exit 1
fi

echo "Loading ${#bundle_files[@]} Synthea bundles to FHIR server: $FHIR_BASE"
echo "  Source: $INPUT_DIR"

success=0
failure=0

for bundle_file in "${bundle_files[@]}"; do
    name=$(basename "$bundle_file")
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        --data-binary "@$bundle_file" \
        "$FHIR_BASE")
    if [[ "$response" =~ ^2 ]]; then
        echo "  OK $name -> HTTP $response"
        ((success++))
    else
        echo "  FAILED $name -> HTTP $response"
        ((failure++))
    fi
done

echo ""
echo "Done. Loaded: $success  Failed: $failure"
if [[ $failure -gt 0 ]]; then
    exit 1
fi
