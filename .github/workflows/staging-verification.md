---
name: Production Release — Staging Verification
description: Verify staging matches live before a production release. Uses AI judgement to determine if any differences are expected intentional changes or unexpected breakage.
on:
  workflow_dispatch:
    inputs:
      release_version:
        description: "Release version (e.g. 3.5.0)"
        required: true
      development_version:
        description: "Next development version (e.g. 3.6.0-SNAPSHOT)"
        required: true
permissions:
  contents: read
  actions: read
---

# Staging vs Live Verification

You are verifying that the staging API is ready for a production release of version ${{ inputs.release_version }}.

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
  - If all checks pass (exit code 0): Mark the release as approved in your final report with a concise justification.
   - If checks fail: Examine each failed URL and determine whether the difference is an **expected intentional change** (e.g. a new release was added to staging ahead of going live) or **unexpected breakage** (e.g. data missing, errors, regressions).

4. **Make your decision:**
   - If all differences are clearly intentional changes that align with what version ${{ inputs.release_version }} is releasing, **approve** and include a `staging_check_summary` section in your final report for downstream release automation or a maintainer to act on.
   - If any differences look like unexpected breakage or you are unsure, **do not approve**. Use the safe output issue creation tool (`create_issue`) to open a release-blocker issue titled `Release blocker: staging-live differences for v${{ inputs.release_version }}` with your detailed findings.

5. **Report your findings.** Provide a clear summary of:
   - How many URLs were checked
   - How many passed vs failed
   - For each failure: whether you judged it as intentional or unexpected, and why

