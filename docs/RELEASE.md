# Production Release Process

This document describes the production release workflow for the Adoptium API
and the GitHub Actions workflows that orchestrate it. The
[staging checker](../adoptium-api-v3-staging-checker/README.md) is a key part
of this process and acts as a release gate.

## Release flow overview

```mermaid
flowchart TD
    Trigger([workflow_dispatch:<br/>Production Release — Execute]):::trigger

    subgraph Execute["production-release-execute.yml"]
        direction TB
        Release["<b>release</b> job<br/>• mvn release:prepare<br/>• read release.properties<br/>• create branch release/X.Y.Z<br/>• mvn release:perform<br/>• push release/X.Y.Z"]:::job
        PRMain["<b>pull-request-to-main</b><br/>open PR: release/X.Y.Z → main"]:::job
        PRProd["<b>pull-request-to-prod</b><br/>open PR: release/X.Y.Z → production"]:::job
        Notify["<b>notify</b><br/>post run summary"]:::job
        Release --> PRMain
        Release --> PRProd
        PRMain --> Notify
        PRProd --> Notify
    end

    Trigger --> Release

    PRMain -. on PR open .-> ReviewMain{{"Human review<br/>+ merge to main"}}:::review
    PRProd -. on PR open .-> StagingPR[["staging-verification.md<br/>(AI staging vs live checks)"]]:::wf
    PRProd -. on PR open .-> BranchChecker[["production-branch-checker.md<br/>(AI risk analysis +<br/>endpoint diff +<br/>release-summary comment)"]]:::wf

    StagingPR --> ReviewProd{{"Human review<br/>+ merge to production"}}:::review
    BranchChecker --> ReviewProd

    ReviewMain --> PostMerge["<b>production-release-post-merge.yml</b><br/>• tag vX.Y.Z<br/>• push tag"]:::job
    ReviewProd --> Deploy[["Production deploy<br/>(image build +<br/>k8s rollout of frontend & updater)"]]:::deploy

    PostMerge --> Tag([Tag vX.Y.Z on main]):::trigger

    classDef trigger fill:#1f6feb,stroke:#0b3d91,color:#fff
    classDef job fill:#2da44e,stroke:#1a7f37,color:#fff
    classDef wf fill:#8957e5,stroke:#5a32a3,color:#fff
    classDef review fill:#bf8700,stroke:#7d5700,color:#fff
    classDef deploy fill:#cf222e,stroke:#82071e,color:#fff
```

## Workflows

### Production Release Execute (`.github/workflows/production-release-execute.yml`)

The orchestrating release workflow, triggered manually via `workflow_dispatch`.
It:

1. Runs `mvn release:prepare`, letting Maven derive the release and next
   development versions from the current POM.
2. Reads the chosen versions back out of `release.properties`.
3. Creates a `release/X.Y.Z` branch, runs `mvn release:perform`, and pushes
   the branch.
4. Opens two PRs in parallel:
   - `release/X.Y.Z` → `main`
   - `release/X.Y.Z` → `production`
5. Posts a summary to the workflow run.

### Staging Verification (`.github/workflows/staging-verification.md`)

An AI-powered agentic workflow that builds and runs the staging checker as
part of the production release gate. It:

1. Builds the staging checker using the `staging-checker` and `adoptium` Maven profiles.
2. Runs the checker against staging vs live.
3. Uses AI judgement to determine whether any differences are expected
   intentional changes (e.g. new releases added to staging ahead of going
   live) or unexpected breakage.
4. Approves the release if differences are acceptable, or creates a
   release-blocker issue if not.

### Production Branch Checker (`.github/workflows/production-branch-checker.md`)

An AI-powered PR review agent for `main` → `production` pull requests. It
performs live endpoint comparison between staging and production as part of
its risk analysis, and produces the release-summary commentary on the PR,
using the same endpoint patterns defined in the staging checker.

### Production Release Post Merge (`.github/workflows/production-release-post-merge.yml`)

Triggered when a `release/X.Y.Z` PR is merged into `main`. It tags the
resulting commit `vX.Y.Z` and pushes the tag.
