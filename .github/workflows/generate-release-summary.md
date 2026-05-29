---
name: Generate Release Summary
description: Generate an AI-written summary of changes between releases for the PR description.
on:
   workflow_call:
      inputs:
         release_version:
            description: "The version being released"
            required: true
            type: string
         prev_tag:
            description: "The previous release tag to compare against"
            required: true
            type: string
---

# Generate Release Summary

You are writing a release summary for version ${{ inputs.release_version }} of the Adoptium API. The previous release was ${{ inputs.prev_tag }}.

## Steps

1. **Get the commit log since the last release:**
   ```bash
   git log ${{ inputs.prev_tag }}..HEAD --pretty=format:"%h %s (%aN)" --no-merges
   ```

2. **Get the diff stats to understand scope:**
   ```bash
   git diff --stat ${{ inputs.prev_tag }}..HEAD
   ```

3. **Read any changed files that look significant** (e.g. new features, API changes, model changes). Focus on:
   - `adoptium-frontend-parent/adoptium-api-v3-frontend/` — API endpoint changes
   - `adoptium-models-parent/` — data model changes
   - `adoptium-updater-parent/` — updater/data pipeline changes
   - `adoptium-api-v3-persistence/` — database changes
   - `pom.xml` files — dependency updates

4. **Write a concise, human-readable release summary** structured as:
   - **Headline**: One sentence describing the most important change in this release
   - **Highlights**: 3-5 bullet points covering the most notable changes (not a raw commit list — synthesise related commits into meaningful descriptions)
   - **Dependencies**: Any notable dependency updates, summarised (don't list every single bump)
   - **Contributors**: List the contributors to this release

   Write it in markdown. Be concise and focus on what matters to users and operators of the API. Don't just repeat commit messages — explain what changed and why it matters.

5. **Save the summary to a file:**
   ```bash
   cat > release-summary.md << 'EOF'
   <your generated summary>
   EOF
   ```

