#!/usr/bin/env bash
set -euo pipefail

FHIR_BASE="${FHIR_BASE_URL:-http://localhost:8080/fhir}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Loading demo patients to FHIR server: $FHIR_BASE"

for bundle_file in "$SCRIPT_DIR/demo-patients/"*.json; do
    name=$(basename "$bundle_file")
    echo "  POSTing $name ..."
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        --data-binary "@$bundle_file" \
        "$FHIR_BASE")
    if [[ "$response" =~ ^2 ]]; then
        echo "    OK $name -> HTTP $response"
    else
        echo "    FAILED $name -> HTTP $response"
        exit 1
    fi
done

echo "All demo patients loaded successfully."
