# Prior Auth Assist — 5-Minute Demo Script

A walkthrough of the three core demo scenarios. Estimated time: 5 minutes.

---

## Setup

Before starting:

1. FHIR server is running and demo patients are loaded (run `bash fhir-data/load-data.sh`)
2. Claude Desktop (or Claude Code) is configured with the MCP server (see `claude_desktop_config.json`)
3. The MCP server is running — either via `java -jar` locally or via the cluster with `kubectl port-forward`

Confirm connectivity by asking Claude: "What MCP tools are available?" — you should see the five priorauth tools listed.

---

## Scenario 1: "Anna has 7 weeks of PT" — knee-complete

Anna Kowalski is the happy path. She has everything the insurance company needs: a knee diagnosis, more than 6 weeks of PT (12+ visits), and a recent X-ray. This scenario demonstrates a complete prior auth package.

**Questions to ask Claude, in order:**

1. "Find Anna Kowalski in the system."
2. "Does she need prior auth for a knee MRI?"
3. "Prefill the prior auth documentation for Anna's knee MRI."
4. "What's missing from Anna's prior auth request?"
5. "Build a draft prior auth request for Anna."

**Expected behavior:**

- `find_patient` returns Anna's demographics and FHIR ID.
- `requirements_for` lists the three required items: diagnosis, PT (12 visits / 6 weeks), and X-ray within 12 months.
- `prefill_documentation` shows all three items as present: diagnosis M17.11, 14 PT visits over 7 weeks, X-ray from 3 months ago.
- `missing_items` returns an empty checklist — nothing is missing.
- `build_request` returns a complete FHIR Claim bundle (JSON) with all supporting references populated. Claude summarizes it for the clinician.

---

## Scenario 2: "Marcus only has 4 weeks of PT" — knee-partial

Marcus Webb has the right diagnosis and an X-ray, but his PT course is incomplete — only 8 visits over 4 weeks, short of the 12 visits / 6 weeks required. This scenario demonstrates the gap-detection workflow.

**Questions to ask Claude, in order:**

1. "Find Marcus Webb."
2. "Fill out the knee MRI prior auth for Marcus."
3. "What does Marcus's office need to gather?"

**Expected behavior:**

- `prefill_documentation` shows: diagnosis present (M17.12), X-ray present, PT = partial (8 of 12 required visits completed).
- `missing_items` returns a specific checklist:
  - [ ] Physical therapy: need 4 more visits (currently 8, require 12 minimum)
  - [ ] PT notes for visits 5–8 (clinical documentation to support visit count)
- Claude explains what the office needs to do before resubmitting — it does not speculate about whether the payer might make an exception.

---

## Scenario 3: "Carol needs a lumbar MRI but hasn't started PT" — lumbar-missing

Carol Simmons has a lumbar diagnosis with radiculopathy symptoms but has not started physical therapy at all. This is the most incomplete scenario — PT is entirely absent from her record.

**Questions to ask Claude, in order:**

1. "What are the requirements for a lumbar spine MRI?"
2. "Check Carol Simmons's documentation for lumbar MRI."

**Expected behavior:**

- `requirements_for` lists lumbar MRI criteria: diagnosis (M54.x or M51.x), minimum 6 weeks PT (12 visits), neurological symptoms documented, X-ray within 12 months.
- `prefill_documentation` shows: diagnosis present (M54.42), neurological symptoms documented, X-ray = missing, PT = missing (zero visits found).
- Claude presents the two gaps clearly and explains that PT authorization is usually a prerequisite step — the office should schedule PT before resubmitting for the MRI.

---

## Guardrail Demo

After any of the above scenarios, ask:

> "Based on this, should the insurance company approve the request?"

**Expected behavior:**

Claude declines to make a coverage determination. It explains that the tool documents whether clinical requirements have been met, but the coverage decision belongs to the payer. Claude can restate the documented requirements and what was found in the record, but will not infer approval likelihood or advise the clinician to expect approval.

This is the key safety boundary: the MCP server uses deterministic rule evaluation — the model explains the rules but cannot override them or make probabilistic coverage judgments.

---

## Wrap-Up Talking Points

- All FHIR queries are read-only GET calls — the server never modifies patient data.
- Every tool call is logged with timestamp, tool name, patient ID, and caller context for audit trail purposes.
- The `build_request` output is a draft FHIR Claim bundle — it requires clinician review and submission through the payer portal; the tool does not submit it.
- The coverage rules are loaded from `coverage-rules/` at startup and can be updated without redeploying the server (hot-reload via Spring's `@ConfigurationProperties`).
