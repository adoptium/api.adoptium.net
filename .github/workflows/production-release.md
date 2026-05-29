---
name: Production Release
description: Run a full production release with staging verification, Maven release, and promotion to production
triggers:
  - workflow_dispatch
inputs:
  release_version:
    description: "Release version (e.g. 3.5.0)"
    required: true
  development_version:
    description: "Next development version (e.g. 3.6.0-SNAPSHOT)"
    required: true
---

# Production Release

Perform a production release of the Adoptium API.

## Steps

1. **Verify staging matches live.** Build and run the staging-live checker to confirm there are no unexpected differences between the staging and live APIs. Run:
   ```bash
   ./mvnw package -Pstaging-checker,adoptium -pl adoptium-api-v3-staging-checker -am -DskipTests
   java -cp adoptium-api-v3-staging-checker/target/adoptium-api-v3-staging-checker-*-jar-with-dependencies.jar \
     net.adoptium.api.v3.checker.StagingLiveChecker adoptium
   ```
   If the checker reports any failures, stop the release and report which URLs have differences. Do not proceed until staging and live are in sync.

2. **Create a release branch and run Maven release.** Create a new branch called `release/<release_version>`. Then run `mvn release:prepare` and `mvn release:perform` to produce the release commits:
   ```bash
   git checkout -b release/${{ inputs.release_version }}
   ./mvnw release:prepare -B \
     -DreleaseVersion=${{ inputs.release_version }} \
     -DdevelopmentVersion=${{ inputs.development_version }} \
     -Dtag=v${{ inputs.release_version }} \
     -DpushChanges=false \
     -DlocalCheckout=true
   ./mvnw release:perform -B -DlocalCheckout=true
   ```
   Push the release branch to origin. If Maven release fails, report the error output and stop.

3. **Create a pull request for the release commits into main.** Open a pull request from `release/<release_version>` into `main`. The PR title should be "Release v<release_version>" and the body should summarize that staging-live checks passed and the release was prepared successfully.

4. **Wait for the release PR to be merged.** Monitor the pull request until it is merged into main. If it is closed without merging, stop and report that the release was aborted.

5. **Create a tag on the release commit.** Once the PR is merged, create an annotated git tag `v<release_version>` on the merge commit in main and push it to origin.

6. **Create a pull request from main into production.** Open a pull request from `main` into the `production` branch. The PR title should be "Deploy v<release_version> to production" and the body should reference the release tag and the original release PR number.

