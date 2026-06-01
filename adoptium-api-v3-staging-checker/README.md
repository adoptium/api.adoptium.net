# Staging Live Checker

Compares responses from the staging API against the live API to verify they are in sync.

## Build

From the project root (the staging checker requires the `staging-checker` and `adoptium` profiles):

```bash
./mvnw package -Pstaging-checker,adoptium -pl adoptium-api-v3-staging-checker -am -DskipTests
```

## Run

```bash
java -cp adoptium-api-v3-staging-checker/target/adoptium-api-v3-staging-checker-*-jar-with-dependencies.jar \
  net.adoptium.api.v3.checker.StagingLiveChecker
```

## Usage

```
Usage: StagingLiveChecker [vendor] [options]

Vendor (first positional argument):
  adoptium       Use Adoptium staging/live URLs (default)
  adoptopenjdk   Use AdoptOpenJDK staging/live URLs

Options:
  --staging-url <url>    Override the staging API base URL
  --live-url <url>       Override the live API base URL
  --versions <v1,v2,...>  Comma-separated list of Java versions to check
                         (default: 8,11,17,21,22,23,24,25)
  --help, -h             Show this help message and exit
```

## Examples

Check Adoptium with default versions:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker
```

Check AdoptOpenJDK:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker adoptopenjdk
```

Check specific versions only:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker adoptium --versions 11,17,21
```

Use custom staging and live URLs:

```bash
java -cp ...jar net.adoptium.api.v3.checker.StagingLiveChecker \
  --staging-url https://my-staging.example.com \
  --live-url https://my-live.example.com
```

## Exit Codes

- `0` — All URL checks passed.
- `1` — One or more URL checks failed. Failed URLs are listed in the summary output.

## GitHub Actions Workflows

The staging checker is integrated into the production release process via several workflows in `.github/workflows/`:

### Staging Verification (`.github/workflows/staging-verification.md`)

An AI-powered agentic workflow that builds and runs this checker as part of the production release gate. It:

1. Builds the staging checker using the `staging-checker` Maven profile
2. Runs the checker against staging vs live
3. Uses AI judgement to determine whether any differences are expected intentional changes (e.g. new releases added to staging ahead of going live) or unexpected breakage
4. Approves the release if differences are acceptable, or creates a release-blocker issue if not

Triggered via `workflow_dispatch` with `release_version` and `development_version` inputs.

### Production Release Execute (`.github/workflows/production-release-execute.yml`)

The orchestrating release workflow that:

1. Derives the release version from the Maven POM
2. **Runs staging verification** (calls `staging-verification.md`) as a required prerequisite
3. Performs the Maven release (`release:prepare` + `release:perform`)
4. Creates a PR to `main` with an AI-generated release summary
5. Posts a summary to the workflow run

The staging checker acts as a **release gate** — the Maven release step will not proceed if the staging verification fails.

### Production Branch Checker (`.github/workflows/production-branch-checker.md`)

An AI-powered PR review agent for `main` → `production` pull requests. It performs live endpoint comparison between staging and production as part of its risk analysis, using the same endpoint patterns defined in this checker.

### Generate Release Summary (`.github/workflows/generate-release-summary.md`)

Generates a human-readable summary of changes between releases, used in the release PR description.
