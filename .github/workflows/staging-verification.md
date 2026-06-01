---
name: Production Release — Staging Verification
description: >
  Runs automatically on PRs to prod-agent-test. Verifies staging matches live
  using AI judgement to determine if differences are expected intentional changes
  or unexpected breakage.
on:
  pull_request:
    branches: [prod-agent-test]

permissions:
  contents: read
  pull-requests: read

features:
  copilot-requests: true

safe-outputs:
  add-comment:
    hide-older-comments: true
  allowed-domains:
    - "*.adoptium.net"

checkout:
  fetch-depth: 0

concurrency:
  group: staging-verification-${{ github.ref }}
  cancel-in-progress: true

timeout-minutes: 30
---

# Staging vs Live Verification

You are verifying that the staging API is ready for a production release. This PR merges a release branch into `prod-agent-test`.

## Context

The staging API may have **intentional differences** from live — these are the new changes being released. Your job is to distinguish between:
- **Expected differences**: New versions added, updated metadata for recent releases, new endpoints for a feature being released, version bumps in available releases
- **Unexpected breakage**: Missing releases that should still be present, HTTP errors, empty responses where data should exist, removed JDK versions that weren't EOL'd, schema/format changes that look unintentional

## Steps

1. **Build the staging checker:**
   ```bash
   ./mvnw package -Pstaging-checker,adoptium -pl adoptium-api-v3-staging-checker -am -DskipTests
   ```

2. **Run the staging-live checker and capture output:**
   ```bash
   java -cp adoptium-api-v3-staging-checker/target/adoptium-api-v3-staging-checker-*-jar-with-dependencies.jar \
     net.adoptium.api.v3.checker.StagingLiveChecker adoptium 2>&1 | tee staging-check-output.txt
   ```

3. **Analyse the results.** Review the checker output carefully:
   - If all checks pass (exit code 0): The release is safe to proceed.
   - If checks fail: Examine each failed URL and determine whether the difference is an **expected intentional change** (e.g. a new release was added to staging ahead of going live) or **unexpected breakage** (e.g. data missing, errors, regressions).

4. **Make your decision and post a PR comment:**
   - If all differences are clearly intentional changes that align with the release, post a comment approving the merge with your reasoning:
     ```
     ## ✅ Staging Verification Passed

     **N URLs checked, N passed, N had expected differences.**

     ### Expected differences:
     - (list each difference and why it's intentional)

     ### Recommendation: Safe to merge
     ```
   - If any differences look like unexpected breakage or you are unsure, post a comment flagging the issue:
     ```
     ## ❌ Staging Verification Failed

     **N URLs checked, N passed, N had unexpected differences.**

     ### Unexpected differences:
     - (list each unexpected difference and why it's concerning)

     ### Recommendation: Do NOT merge — investigate the above issues first
     ```

5. **Report your findings clearly:**
   - How many URLs were checked
   - How many passed vs failed
   - For each failure: whether you judged it as intentional or unexpected, and why
