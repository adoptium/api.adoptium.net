# 8. Release snapshot update semantics and failure isolation

Date: 2026-10-22

Last reviewed: 2026-10-23

## Status

Accepted

## Context

Rolling early-access (EA) releases can replace assets without changing the
release timestamp or asset count. One upstream GitHub release can also map to
multiple API releases, split by the versions recorded in asset metadata.
Refreshing only matching split IDs can leave obsolete splits behind, while
accepting partially mapped assets can publish an incomplete EA.

The update mechanism must balance freshness, completeness, GitHub request volume
and implementation complexity. A single failing EA must not prevent unrelated
releases from updating.

This decision documents the current release update mechanism. It does not
introduce the per-release database persistence or communications channel proposed
in [ADR 6](0006-update-rework.md).

## Assumptions and scope

The following assumptions bound this PR's implementation complexity. They are
not guarantees about every possible upstream change.

| Assumption | Consequence or limitation |
| --- | --- |
| Historical EAs are not expected to be modified. | Poll the latest EA and recently published EAs rather than repeatedly fetching the full EA history. An observed timestamp or asset-count change can still select an older EA, but special recovery for historical edits is outside scope. |
| An established feature version is not expected to disappear or lose all its published releases. | Removing an entire feature version, including when full-build EA omissions leave it empty, is outside scope. Do not add MongoDB cleanup for absent or explicitly empty feature versions solely to support this case. |
| All relevant metadata will not disappear from a previously metadata-based release. | Do not persist historical metadata presence to distinguish this case from a genuinely metadata-free legacy release. Individual missing or invalid required sidecars remain supported failure scenarios. |
| Genuinely metadata-free legacy releases remain valid inputs. | Preserve release-name and filename fallback, including for legacy Semeru releases; absence of all metadata is not by itself a failure. |
| A successfully enumerated repository summary is authoritative for release membership. | An absent upstream ID permits removal of its stored splits. Retrieval or pagination failure must not be treated as a complete empty or truncated summary. |
| The version supplier identifies supported feature versions that may have no stored releases. | Incremental discovery visits supported versions as well as stored versions. Recovery of a completely absent version depends on that version being supplied; the updater does not discover arbitrary Java versions independently. |
| GitHub asset IDs and supplied digests provide useful cache identity. | Reuse asset content under that identity; replacing the ID or changing the digest causes a miss. Without either field, fetch on demand rather than assume a URL identifies immutable content. |
| Asset listings and required sidecar mapping are the supported completeness checks. | Do not download JDK archives to prove availability or verify archive bytes. A successful refresh is not an end-to-end download test of every published binary. |
| The separate `Release.source` link is outside checksum-exclusion enforcement. | A source archive excluded from `binaries` for a checksum disagreement may remain linked through `source.link`, including across release splits. This is an explicitly accepted limitation, not a defect requiring a fix in this PR. |
| Transient failures can be retried by normal future selection. | Do not add a persisted retry queue or per-release deadline. Network retries can delay a cycle, and a failed historical EA is not guaranteed another attempt every cycle. |

In particular, an older EA edited across the full-update age cutoff is not a
supported recovery case. Full retrieval selects using upstream timestamps,
whereas publication removes old data using stored timestamps. If that historical
EA is selected upstream but fails mapping, its stored splits can remain outside
the removal filter. This PR deliberately does not add selected-ID tracking solely
to handle that unexpected historical-edit scenario.

Whole-feature-version disappearance is likewise unsupported. Production MongoDB
publication updates the feature versions present in the replacement catalogue;
it does not clear previous records for an absent or explicitly empty version.
If a rebuild omits a version's last release, old records can therefore remain and
reappear on a database reload. This is a known limitation outside the supported
scenarios, not a request to implement whole-version deletion. Incremental
discovery of supported versions missing from the in-memory catalogue remains in
place, but does not guarantee durable removal of an entire feature version.

The failure outcomes below are explicit policy choices, not assumptions that
failures cannot occur: prefer a stale complete EA on incremental failure, omit
a failed EA during a full rebuild, and reject incomplete binary snapshots except
for the explicit per-artifact checksum exclusions described below.
GA failures and repository-wide listing failures still abort a full-build attempt;
EA failure isolation does not relax those boundaries.

Version-parsing failures retain this branch's mapper-lifetime blacklist. This
differs from `upstream/main`, where these mapping failures are retryable. It is
an explicit branch policy, not an assumption that malformed releases cannot be
corrected upstream or a promise that they will recover automatically.

## Decision

### 1. Replace complete upstream snapshots, not individual binaries

The unit of incremental replacement is one upstream GitHub release, including
all API version splits produced from it.

- A complete success replaces every previously stored split for that upstream
  ID with the newly mapped splits.
- An empty successful result removes the previous splits. This is not a failed
  refresh.
- An incremental retryable failure retains every previous split. During a full
  rebuild, a failed EA is instead omitted, including any previous splits.
  A failed new EA remains unpublished.
- Never merge successfully refreshed binaries with old binaries from a failed
  mapping of the same upstream release.
- Compare exact normalized upstream IDs, excluding the API split suffix.
  Similar string prefixes do not identify the same upstream release.

For example, if the Windows metadata cannot be read for an EA containing Linux,
Windows and macOS binaries, retain the previous complete EA during an incremental
update, but omit the entire EA during a full rebuild. Do not publish just the new
Linux and macOS entries. Other upstream releases can still update.

The updater reads asset listings, metadata and checksum sidecars; it does not
download JDK archives to establish snapshot completeness.

### 2. Bound incremental EA discovery

Discover feature versions from the union of the stored catalogue and the version
supplier's supported versions. For a supported version absent from the catalogue,
start discovery with an empty feature release. This allows incremental recovery
when a full rebuild omits that version's only EA, without retaining old splits
or waiting for another full rebuild.

Incremental updates are scheduled with a six-minute delay after the previous
incremental execution finishes. All selectors respect the upload grace period:
the release must be more than ten minutes old.

Select releases when they are:

- Newly discovered.
- Changed according to the upstream release update timestamp.
- Changed according to the asset count, when stored count information exists.
- Explicitly selected for refresh by release name.
- Published within the last seven days.
- The latest published EA known to the updater for a vendor, repository and
  Java feature version, even when its timestamp and asset count are unchanged.

Recent edits also keep GA releases eligible under the existing seven-day
refresh rule. Recent edits alone do not repeatedly select the entire historical
EA catalogue after those edits have been recorded.

Deduplicate overlapping selectors by upstream ID before fetching. A selected
release is attempted at most once per cycle, even if it fails or maps to an
empty result. Newly mapped EAs participate in determining the latest EA, so
discovering a newer EA need not also refresh its predecessor.

Retryable means eligible for a later refresh when selected again. There is no
separate persisted retry queue or guarantee that every failed historical EA is
retried every cycle. The latest EA normally remains eligible for the next cycle.

### 3. Isolate incremental release failures

| Outcome or failure | Incremental behaviour |
| --- | --- |
| Complete mapped snapshot | Replace all stored splits for that upstream ID. |
| Successful empty snapshot, including policy filtering | Remove all stored splits for that upstream ID. |
| Upstream ID absent from a complete repository summary | Remove its stored splits. |
| Release fetch returns `null` | Retain the previous snapshot and remain eligible for selection. |
| Release fetch or mapping throws an ordinary exception | Log the failure, retain the previous snapshot, and continue with other releases. |
| Required metadata is missing, unreadable, invalid, or fails retrieval | Reject that upstream snapshot as retryable; retain all its previous splits. |
| A selected supported binary fails mapping, other than an explicit checksum disagreement | Reject that upstream snapshot as retryable; do not publish the remaining binaries. |
| GitHub's binary SHA-256 disagrees with metadata or a checksum sidecar | Log an error and omit only that binary; publish the other successfully mapped binaries. |
| Explicit non-retryable mapping result | Retain existing splits and suppress further incremental attempts through the builder's exclusion set. |
| Repository-summary retrieval or pagination fails | Abandon the incremental calculation without publishing it; do not prune from an incomplete summary. |
| Coroutine cancellation at the per-release boundary | Propagate cancellation rather than continuing as if it were an ordinary EA failure. |
| Database checksum changes before publication | Do not overwrite the newer database snapshot; reload it instead. |

The per-release failure boundary is in the incremental builder. Failure to
obtain the repository summary is different: there is no trustworthy basis for
discovery and deletion, so the calculation cannot safely proceed.

Fetches and their network retries can delay a cycle. Failure isolation does not
imply parallel requests or a per-release execution deadline.

### 4. Use one selected-binary set for mapping and completeness

Select supported assets before grouping and mapping them. Each selected binary
must map successfully or be explicitly excluded for a checksum disagreement for
the snapshot to succeed. Other mapping failures still reject the snapshot.
Supported assets include archives and SBOMs, subject to the mapper's existing
exclusions.

Once metadata is used, every supported non-source release asset must have its
required metadata, including SBOM metadata. Metadata sidecars themselves are not
binaries. Installer, unsupported-asset and excluded-asset metadata is not required
for binary grouping.

Release-grouping metadata association uses prefix matching, unlike upstream's
exact sidecar-name matching. Installer metadata is loaded separately and prefers
an exact full-filename match before legacy naming fallbacks; it does not
participate in version grouping.

Source packages retain their special treatment: source archives without metadata
can be exposed as source packages without being selected as binaries. Source
metadata is still read when present and applicable.

Genuinely metadata-free legacy releases, including legacy Semeru releases,
continue to use release-name and filename parsing.

**All relevant metadata disappearing from a previously metadata-based release
is outside the supported failure scenarios.** Do not add historical metadata
tracking or another recovery mechanism to distinguish that hypothetical case
from a genuine legacy release.

Missing optional checksum data does not by itself reject a release. Prefer a
valid GitHub SHA-256 asset digest, then the existing metadata/checksum-sidecar
fallbacks, subject to the agreement checks in section 7. Do not download
archives to calculate their digests.

### 5. Keep policy exclusions separate from parsing failures

Known release-policy exclusions are evaluated against the current mapped
snapshot, not cached indefinitely by upstream ID. In particular,
`jdk-11.0.13'+8` remains excluded for EAs in both full and incremental updates.
The same EA-only repository filter includes `jdk17u-2022-05-27-19-32-beta`.
This retains the branch's post-mapping EA-only scope; it differs from
`upstream/main`, which rejects both upstream names before mapping, including GAs.

If filtering produces no releases, it is a successful empty snapshot and removes
old incremental splits.

Checksum-disagreement exclusions are also evaluated on each selected refresh,
not permanently blacklisted. A later refresh can restore the binary once its
checksums agree.

Version-parsing failures are non-retryable in this implementation. The
Adopt release mapper remembers an unparsable upstream ID for the lifetime of
that mapper. Correcting its upstream name or metadata does not clear that set;
restarting the updater clears it. Full builds clear the builder's incremental
exclusion set, but not the mapper's parsing-failure set.

This parsing behaviour is a retained branch policy, not shared upstream
behaviour or a promise that parsing errors are recoverable. Aligning it with
upstream's retryable behaviour requires a separate policy decision.

### 6. Isolate EA failures during full builds

Full updates normally run daily and rebuild the data selected by the existing
vendor, release-type and age filters. Data outside those filters is preserved.
The persisted omission behaviour below assumes the feature version retains at
least one release; whole-feature-version disappearance is outside scope as
described under [Assumptions and scope](#assumptions-and-scope).

- For an upstream GitHub prerelease (EA), a retryable mapping failure or ordinary
  mapping exception omits the entire EA and continues rebuilding other releases.
  Its previously published splits are not carried forward. A failed new EA also
  stays unpublished.
- Failure to finish one EA's asset pagination has the same effect. The listing
  remains explicitly incomplete (`hasNextPage=true`), is never mapped as a
  complete snapshot, and causes omission of that EA.
- GA retryable mapping failures, mapping exceptions and asset-pagination
  failures still abort the full-build attempt.
- Repository-wide release-list retrieval or pagination failures still abort:
  there is no trustworthy complete list on which to base replacement and
  deletion. These are not failures of an individually identified EA.
- Coroutine cancellation propagates through the per-release failure boundaries.
- Non-retryable mapping results are omitted, as are releases rejected by policy.
- A successful EA refresh replaces every previous split; a successful empty
  result removes them. Failed EAs and policy exclusions are both absent from the
  selected full-rebuild result, although their reasons for omission differ.
- The full-update statistics guard rejects a result whose recorded download
  total decreases for a feature version after allowing for previously published
  EAs now absent from the rebuild. Those omitted EAs must not indirectly block
  publication through their missing download counts. Losses from retained EAs
  and GAs are not given this allowance.

Aborting a build before publication leaves the existing database catalogue in
place. The scheduler can continue with other update attempts; this does not
guarantee rollback of a database failure after publication has begun.

Do not pass prior EA splits through the full mapping path, or add partial
full-build persistence, per-binary fallback or a separate recovery framework.
The normal publication step replaces data covered by the full-update filters,
so failed EAs selected by those filters disappear. Data outside those filters
remains preserved.

### 7. Preserve asset freshness and download accounting

Metadata/checksum asset cache keys include the download URL and available GitHub
asset ID/digest. Replacing an asset ID or changing its digest causes a cache miss
even when timestamps and asset counts remain unchanged. Without either identity
field, fetch on demand rather than trusting a URL-only cached entry.

When a digest is supplied for a downloaded sidecar, validate the downloaded bytes
before using or caching them. An exhausted retrieval/validation failure makes
required metadata unavailable and follows the snapshot failure rules above.

When GitHub supplies a valid SHA-256 digest for a binary, compare it with any
nonempty metadata SHA-256 and any retrieved checksum-sidecar value. Fetch an
available checksum sidecar for this comparison even when GitHub or metadata
already provides a checksum. Compare hexadecimal values case-insensitively;
checksum sidecars may contain a bare hash or a hash followed by a filename.
Prefer the exact `<asset-name>.sha256.txt` sidecar before legacy basename
aliases, independently of asset order. Legacy aliases remain supported when no
exact sidecar exists. Cancellation during sidecar retrieval propagates through
the asset-cache and checksum-mapping boundaries; it is not treated as an
unavailable optional checksum.

If either value disagrees with GitHub, log an error identifying the asset and
the conflicting checksums, and exclude only that binary. For an installer
disagreement, exclude the installer but retain its otherwise valid archive.
Installer metadata is read from its matching metadata asset, using the same
asset-aware cache as other sidecars, and its SHA-256 participates in the check
alongside the checksum sidecar. Installer metadata does not determine archive
grouping or make installer sidecars required for archive completeness.
If an installer has no metadata asset, continue with GitHub/sidecar checks.
If its listed metadata cannot be retrieved or parsed, log an error and omit
only the installer. Without a valid GitHub digest, installer metadata also
provides the preferred checksum before falling back to a checksum sidecar.
This is an intentional exception to all-or-nothing mapping completeness, in
both incremental and full updates. Do not retain the binary's old copy during
a successful incremental replacement, and do not reject unrelated binaries or
version splits solely because of the disagreement.

Checksum exclusion does not suppress the separate `Release.source` field. If a
metadata-bearing source archive is excluded from `binaries`, its source link can
still be published on otherwise healthy releases. This behaviour is explicitly
accepted for this PR; removing source links across splits is outside scope.

If GitHub supplies no valid binary SHA-256, retain metadata-then-sidecar fallback.
Missing or unavailable optional checksum data does not by itself exclude a
binary. Validation of a sidecar's own downloaded bytes is separate from checking
that the hash written inside it agrees with GitHub's digest for the binary.

Cache retention is thirty days since demand access, enforced asynchronously by
MongoDB's TTL index on `lastAccessed`, not by an application eviction loop.
Background HTTP validation does not extend demand retention; old entries without
usage history receive one retention-period grace. Cache writes remain
asynchronous; this decision does not introduce a durable-cache-write prerequisite
for publishing a release.

Release download counts include installer and other eligible ungrouped-asset
downloads exactly once. Attribute ungrouped downloads to the lowest-version
split, independently of GitHub asset order. Metadata-free fallback counts the
same eligible assets without duplicating those totals.
Checksum exclusions do not erase historical upstream download counts. A version
split whose binaries are all excluded can therefore remain as an internal
release with no binaries and its existing download attribution; it exposes no
binary download through the binary-filtered API results.

## Consequences

An incremental update may temporarily expose a stale complete EA while required
data is unavailable. A full rebuild may instead remove that EA until a later
successful discovery or rebuild. Both are preferable to publishing a partial or
mixed snapshot. Unrelated releases remain free to update.

Keeping the upstream ID alongside each successful refresh is necessary even
when the refresh contains no splits. Explicit success, retryable failure and
non-retryable failure must remain distinguishable, but no new outcome hierarchy,
persisted retry state or historical-metadata state machine is required.

An unchanged old EA that is not the latest is not guaranteed asset-only refreshes
on every incremental cycle. It can still be selected by an explicit request,
release edit, asset-count change or a full update's filters.

## Behaviour comparison with upstream/main

Re-evaluated on 2026-10-23 against the local `upstream/main` reference at commit
`d9836348`. The compared branch includes that commit via merge commit `32afbfd2`,
plus subsequent checksum-lookup, cancellation and cache API cleanup changes. This
replaces the earlier comparison against `77a4d8b0`; it is a fixed-baseline
comparison, not a claim about later changes to either branch.

| Area | Upstream at `d9836348` | Current code |
| --- | --- | --- |
| EA failure during a full update | An EA mapping/required-metadata failure or asset-pagination exception aborts that full-build attempt. | Log and omit the entire failed EA, including previous splits, while rebuilding other releases. GA failures still abort. |
| Full-update download-count guard | Reject a feature-version download-total decrease without allowing for omitted EAs. | Allow the previous download counts of EAs now absent from the rebuild. Retained-EA and GA losses receive no allowance. |
| Latest EA polling | An unchanged EA outside the seven-day refresh window is not necessarily fetched. | Select the latest known EA per vendor, repository and feature version even when its timestamp and asset count are unchanged. Existing upload-grace and exclusion rules still apply. |
| Feature-version discovery | Incremental updates visit only versions in the stored catalogue. | Visit the union of stored and supported versions, allowing an EA-only version omitted by a full rebuild to recover incrementally. |
| Older, recently edited EAs | Repeatedly refresh them while their edit timestamp remains within seven days. | An actual timestamp change still triggers refresh, but recent edits alone no longer repeatedly select older, already-known EAs. Recent publication still does. |
| Non-retryable incremental failures | Remove their previous splits. No builder exclusion set prevents subsequent selection. | Retain previous splits and suppress further incremental attempts until a full build clears the builder exclusion set. |
| Unparsable release versions | Mapping failure is retryable by default; there is no mapper-lifetime parsing blacklist. | Remember the upstream ID as non-retryable for the mapper's lifetime. Correcting the upstream release does not clear that memory; restarting the updater does. |
| Metadata/checksum cache freshness | Use URL-based caching. Replacing an asset at the same URL can reuse cached content until validation refreshes it. | Key asset reads by URL plus GitHub asset ID/digest. Identity changes cause misses; assets without either identity field are fetched on demand. |
| Checksums | Use metadata SHA-256, then checksum sidecars. | Compare a valid GitHub SHA-256 with available metadata/sidecar checksums and exclude only the affected artifact on disagreement. Otherwise prefer GitHub, then the existing fallbacks. Downloaded sidecars with supplied digests are validated before use/caching. |
| Installer metadata | Do not load installer metadata for validation; use its checksum sidecar for the installer checksum. | Load matching installer metadata through the asset-aware cache and include its SHA-256 in agreement checks. Missing metadata assets are allowed; listed metadata that cannot be read or parsed excludes only the installer, not its archive. |
| Cache writes | Wait for the cache write before returning fetched content. | Write asynchronously; release publication does not wait for cache persistence. |
| Known tag exclusions | Reject `jdk-11.0.13'+8` and `jdk17u-2022-05-27-19-32-beta` before mapping, including GA releases. | Filter successfully mapped EA results only. An empty filtered result removes old incremental splits; GA releases are not excluded by these rules. |

The merged upstream baseline already implements the following protections; they
remain shared behaviour, not differences introduced by this work:

- Complete upstream-snapshot replacement across all version splits.
- Removal of old splits after a successful empty refresh.
- Incremental retention on retryable failures, null fetches and ordinary
  per-release exceptions, while other releases continue.
- Rejection of incomplete binary mappings.
- Rejection of incomplete repository/asset pagination, missing page data and
  missing, blank or non-advancing continuation cursors. An explicitly absent
  repository is treated as empty only on the initial request.
- The ten-minute upload grace and thirty-day demand-based cache retention.
- Stable attribution of installer and other eligible ungrouped downloads to
  the lowest-version split exactly once, independent of asset order.

Scheduling, the database-checksum publication guard and full-update age/vendor
filters are also unchanged. The accepted limitations under
[Assumptions and scope](#assumptions-and-scope) still apply; the merge does not add
historical-EA recovery or whole-feature-version database cleanup.

Additional implementation differences from this baseline are:

- Release-grouping metadata association uses prefix matching rather than exact
  sidecar filenames. Installer metadata lookup separately prefers an exact
  full-filename match before legacy naming fallbacks.
- Metadata-bearing source archives can enter the selected binary set rather
  than always being source-only. Metadata-free sources remain outside that set.
- The Adopt mapper and full-build repository mapping explicitly reject asset
  listings still marked `hasNextPage=true`, adding a completeness check beyond
  the shared pagination guards. An EA whose asset pagination failed reaches
  this check and is omitted; an equivalent GA failure still aborts the build.
- Failure results default to non-retryable unless explicitly marked otherwise.
  Required-metadata and ordinary mapping failures in the Adopt mapper are
  explicitly retryable.
- Checksum-sidecar parsing accepts a bare hash and general whitespace separators.
  Upstream only returns a checksum when splitting on literal spaces produces
  multiple tokens.
- Checksum lookup prefers an exact asset filename before legacy aliases;
  upstream selects the first matching name in asset order. Cancellation
  propagates through asset-cache fetches and checksum retrieval rather than
  being retried or converted into an unavailable checksum at those boundaries.
- The internal cache-write API defaults to a background write (`accessedAt=null`);
  demand HTTP fetches explicitly pass their access time. Upstream defaults to a
  demand write. The unused manual eviction API present upstream has been removed;
  production expiration in both versions relies on MongoDB's TTL index. These
  API differences do not change the shared thirty-day demand-retention policy.
- GraphQL decoding mismatches are logged and returned as `null` by the query
  helper, then rejected as missing page data by the paginated reader. Upstream
  throws a decoding exception directly; neither path accepts a partial listing.

The checksum agreement and installer-metadata checks above are part of the
implementation inspected, not pending follow-up work. They do not suppress the
separate `Release.source` link or erase historical download counts, as documented
in section 7.

The principal operational trade-off is more reliable polling of the latest EA,
less repeated polling of historical EAs, stale complete EAs on incremental
failures, and absent EAs on full-build failures. The mapping differences above
are separate implementation changes, not additional failure-policy decisions.

## Implementation references

- [Incremental selection and replacement](../../adoptium-updater-parent/adoptium-api-v3-updater/src/main/kotlin/net/adoptium/api/v3/AdoptReposBuilder.kt)
- [Repository mapping and shared exclusions](../../adoptium-updater-parent/adoptium-api-v3-updater/src/main/kotlin/net/adoptium/api/v3/AdoptRepository.kt)
- [Metadata grouping and binary completeness](../../adoptium-updater-parent/adoptium-mappers-parent/adopt-mappers/src/main/kotlin/net/adoptium/api/v3/mapping/adopt/AdoptReleaseMapper.kt)
- [Scheduling and publication](../../adoptium-updater-parent/adoptium-api-v3-updater/src/main/kotlin/net/adoptium/api/v3/V3Updater.kt)
