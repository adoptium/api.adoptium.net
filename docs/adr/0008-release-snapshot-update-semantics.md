# 8. Release snapshot update semantics and failure isolation

Date: 2026-10-22

Last reviewed: 2026-11-11

## Status

Accepted

## Review entry point

The objective of [adoptium/api.adoptium.net#2191](https://github.com/adoptium/api.adoptium.net/pull/2191)
is reliable active-EA refresh and complete upstream-release replacement, using
the existing updater and publication lifecycle. It is not a general recovery
framework. Review against the invariants below and the explicitly accepted
scope, rather than treating every imaginable recovery scenario as a requirement.
The invariants remain obligations to verify, not grounds to dismiss a defect in
a supported scenario.

[Path-scoped Copilot review instructions](../../.github/instructions/release-updates.instructions.md)
repeat the essential contracts and limitations directly in an instruction file;
the longer explanation here is not the only source of review context.
The [upstream comparison](#behaviour-comparison-with-upstreammain) records the
reviewed baseline, and [regression ownership](#regression-ownership) identifies
the suites exercising the boundaries.

## Context

Rolling early-access (EA) releases can replace assets without changing the
release timestamp or asset count. One upstream GitHub release can also map to
multiple API releases, split by the versions recorded in asset metadata.
Refreshing only matching split IDs can leave obsolete splits behind, while
accepting partially mapped assets can publish an incomplete EA.

The update mechanism must balance freshness, completeness, GitHub request volume
and implementation complexity. In normal operation, a single failing EA must not
prevent unrelated releases from updating, subject to the scope below.

This decision documents the current release update mechanism. It does not
introduce the per-release database persistence or communications channel proposed
in [ADR 6](0006-update-rework.md).

## Assumptions and scope

The following assumptions bound this PR's implementation complexity. They are
not guarantees about every possible upstream change.

| Assumption | Consequence or limitation |
| --- | --- |
| Historical EAs are not expected to be modified. | Poll the latest EA and recently published EAs rather than repeatedly fetching the full EA history. An observed timestamp or asset-count change can still select an older EA, but special recovery for historical edits is outside scope. |
| Forced legacy AdoptOpenJDK rebuilding (`UPDATE_ADOPTOPENJDK=true`) is exceptional and not expected in normal operation. | Per-EA failure isolation is not guaranteed during forced legacy rebuilds. Accept that a failed date-classified legacy EA can abort the build rather than introducing a shared classifier solely for this case. |
| An established feature version is not expected to disappear or lose all its published releases. | Removing an entire feature version, including when full-build EA omissions leave it empty, is outside scope. Do not add MongoDB cleanup for absent or explicitly empty feature versions solely to support this case. |
| All relevant metadata will not disappear from a previously metadata-based release. | Do not persist historical metadata presence to distinguish this case from a genuinely metadata-free legacy release. Individual missing or invalid required sidecars remain supported failure scenarios. |
| Genuinely metadata-free legacy releases remain valid inputs. | Preserve release-name and filename fallback, including for legacy Semeru releases; absence of all metadata is not by itself a failure. |
| A successfully enumerated repository summary is authoritative for release membership. | An absent upstream ID permits removal of its stored splits. Retrieval or pagination failure must not be treated as a complete empty or truncated summary. |
| The version supplier identifies supported feature versions that may have no stored releases. | Incremental discovery visits supported versions as well as stored versions. Recovery of a completely absent version depends on that version being supplied; the updater does not discover arbitrary Java versions independently. |
| GitHub asset IDs and supplied digests provide useful cache identity. | Reuse asset content under that identity; replacing the ID or changing the digest causes a miss. Without either field, fetch on demand rather than assume a URL identifies immutable content. |
| Asset listings and required sidecar mapping are the supported completeness checks. | Do not download JDK archives to prove availability or verify archive bytes. A successful refresh is not an end-to-end download test of every published binary. |
| The separate `Release.source` link is outside checksum-exclusion enforcement. | A source archive excluded from `binaries` for a checksum disagreement may remain linked through `source.link`, including across release splits. This is an explicitly accepted limitation, not a defect requiring a fix in this PR. |
| Temporary EA inconsistency after a failed release publication is acceptable. | A publication that fails midway can leave an EA with old splits plus some new ones, and a full `RELEASES_ONLY` rebuild preserves EAs, so the mixed set can be published as complete until the next incremental update replaces every split of that EA (I4). This is an explicitly accepted, short-lived limitation: EAs are less critical than GA releases and self-heal on the next incremental. Do not add a persisted failure flag, an incomplete marker, an `ALL`-mode recovery rebuild or a pointer switch for it. The catalogue stays loadable throughout (I3). GA releases are not exposed this way because a full rebuild replaces them. |
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

This is the chosen narrow scope: neither an absent feature version, an
explicitly empty feature version, nor an entirely empty replacement catalogue
authorizes MongoDB to delete that version's existing records. Non-empty feature
versions still replace their previous records, including removing obsolete
releases within those versions. Consequently an empty in-memory result is not
a durable whole-version deletion request.

The failure outcomes below are explicit policy choices, not assumptions that
failures cannot occur: prefer a stale complete EA on incremental failure, omit
a failed EA during a full rebuild, and reject incomplete binary snapshots except
for the explicit per-artifact checksum exclusions described below.
Retryable GA mapping failures, mapping exceptions and repository-wide listing
failures still abort a full-build attempt;
EA failure isolation does not relax those boundaries.
MongoDB operations throughout persistence, including the metadata cache, share a
bounded retry policy for reads and repeat-safe writes: at most three
application-level attempts, with cancellable delays of 100 ms and 200 ms.
Only known transient connection, server-selection, primary/recovery failures or
errors labelled `RetryableWriteError` qualify. Permanent or unclassified errors
and coroutine cancellation propagate immediately. Failed read cursors restart
with a fresh result rather than retaining partially collected rows.

Append-only inserts, including release/CDXA batches and statistics, are not
replayed by this policy: an acknowledgement failure can mean the write already
succeeded. Release notes use upstream's atomic, ID-keyed replacement/upsert and
therefore receive the repeat-safe retry budget. Complete publications and
delete/insert sequences are not retried. Existing MongoDB driver retry defaults remain unchanged;
three application attempts are not a limit on wire commands or elapsed time.

A permanent failure or exhausted operation budget abandons the entire current
update attempt rather than being isolated to one release or retried at a higher
layer. The scheduler logs the failure and remains active for the next scheduled
cycle. Already completed database writes are not rolled back; because
publication never removes a release before its replacement exists (below), the
database stays usable after any failure. A later
full rebuild uses surviving records and the normal retrieval filters, so it
cannot guarantee restoration of records already deleted outside those filters,
such as excluded legacy releases. That remains an operational data-recovery
limitation of the chosen non-transactional, abandon-on-failure policy.

Version-parsing failures are non-retryable within an update window but are not
remembered by the mapper. This differs from `upstream/main`, where these mapping
failures are retryable. It is an explicit branch policy, not an assumption that
malformed releases cannot be corrected upstream or a promise that they will
recover on any particular cycle.

## Acceptance invariants

These are the contracts against which implementations and review findings must
be evaluated, not a declaration that all paths already satisfy them. Each needs
an owning boundary and a regression that exercises its failure case.
Whole-feature-version disappearance remains subject to the limitation above;
do not expand that scope to make a regression pass.

| ID | Invariant | Owning boundary and required scenario |
| --- | --- | --- |
| I1 | Updater baselines include persisted releases and download attribution independently of API visibility filters or serving-version caps. | Updater snapshot loading: include a zero-binary release and a version above the frontend cap; exercise real MongoDB reads as well as an in-memory double. |
| I2 | A replacement cannot silently discard newer published work within a feature version it replaces. | Mutual exclusion: a scheduled incremental is skipped while a full build holds the update lock (`PublicationInvariantTest`). |
| I3 | The release database is usable at every point: a release is never removed before its replacement exists, and the generation advances only after all writes. A startup read failure cannot become an empty successful baseline. | Mongo publication and snapshot loading: reject release writes or the final publication and assert the previous catalogue stays readable; republish identical content within the same second during a read; republish without duplicates. |
| I4 | One upstream release is replaced as a complete set of splits, never a mixture of refreshed and stale binaries. Failures follow the update-mode policies below. | Mapping and builders: fail one required sidecar in a multi-binary release under both full and incremental updates, retaining explicit per-artifact checksum exclusions. |
| I5 | Non-empty feature replacements remove obsolete releases; missing or empty features preserve stored records. Download-loss guards account for stored and rebuilt versions, including versions absent from the candidate. | Mongo persistence and the full-update guard: exercise absent, explicitly empty, and partially populated feature versions separately and assert download totals explicitly. |
| I6 | Asset-list ordering cannot hide an available, unambiguous sidecar or change checksum decisions. | Sidecar selection: permute exact matches, ambiguous aliases and valid more-specific aliases. |
| I7 | Per-key cache-write ordering survives cancellation, without mixing asset identities or turning pending-fetch sharing into indefinite URL-only caching. | Cache client and persistence: cancel during a blocked database write, then fetch newer content; also exercise identity changes and later URL-only requests. |
| I8 | Only individual repeat-safe Mongo operations receive bounded transient retries. Permanent or exhausted failures abort the scheduled attempt without cancelling future attempts or becoming per-release/optional-artifact fallbacks. | Mongo operation, mapper and scheduler boundaries: verify three application attempts, cancellation, unsafe-insert exclusions, propagation through shared cache fetches, and a later scheduled attempt. |

Use real MongoDB coverage for persistence semantics rather than inferring them
from an in-memory replacement double. For I5, retaining records for absent or
empty versions across a restart is intentional; their reappearance after reload
is the accepted limitation, not evidence that deletion must be added.

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
  Similar string prefixes do not identify the same upstream release. Stored
  releases are indexed by normalized ID on demand, so each incremental cycle
  looks up every upstream release in constant time.

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
| MongoDB read or write fails, including a metadata-cache operation | Apply bounded transient retries only to the individual read or repeat-safe write. On a permanent failure or exhausted budget, abandon the entire update attempt without continuing to other releases or retrying at a higher layer. Try again on the next scheduled cycle. |
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

Release-grouping metadata association uses exact sidecar names, as upstream does:
`<asset-name>.json`, or `<sbom-name-without-.json>-metadata.json` for SBOMs.
Similar names never pair, for example the sidecar for build 10 is not matched to
the SBOM for build 1 regardless of asset order. Installer metadata is loaded
separately and prefers an exact full-filename match before legacy naming
fallbacks; it does not participate in version grouping.

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

Version-parsing failures are non-retryable in this implementation, but the
release mapper keeps no per-release memory: mappers hold no mutable state and
are created on demand by their factory. The only memory is the builder's
exclusion set, which suppresses further incremental attempts at a non-retryable
release across the six-minute incremental runs. Each full build clears that set
and re-attempts the release, so correcting its upstream name or metadata recovers
at the next full build without restarting the updater. The exclusion set is
thread-safe because full and incremental updates run on separate scheduler
threads and share the builder instance. Each full build clears the set and
advances an exclusion epoch both at its start and on successful completion.
Completion removes failures recorded during the build, and the epoch prevents
requests started before or during the build from adding stale failures afterward.
Failures from requests started after completion remain excluded as normal.

This parsing behaviour is a branch policy, not shared upstream behaviour or a
promise that parsing errors are recoverable. Aligning it with upstream's
retryable behaviour requires a separate policy decision.

### 6. Isolate EA failures during full builds

Full updates normally run daily and rebuild the data selected by the existing
vendor, release-type and age filters. Data outside those filters is preserved.
The persisted omission behaviour below assumes the feature version retains at
least one release; whole-feature-version disappearance is outside scope as
described under [Assumptions and scope](#assumptions-and-scope).

With the default `UPDATE_ADOPTOPENJDK=false`, full builds skip legacy
AdoptOpenJDK repositories and preserve their persisted releases. Forced legacy
rebuilds are an accepted exception to per-EA failure isolation: the legacy mapper
can classify a dated release as an EA even when GitHub's `isPrerelease` is false,
whereas the full-build mapping and asset-pagination failure boundaries use that
flag. A metadata or asset-pagination failure for such a release can therefore
abort the full build. Historical EAs are not expected to change, but this does not
guarantee that their metadata remains readable. Do not add shared classification
or legacy-specific recovery solely to eliminate this exceptional failure case.

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
- MongoDB failures propagate past per-release isolation once the individual
  operation's retry budget is exhausted, or immediately when retry is unsafe or
  the failure is permanent. They stop the remaining phases of the scheduled
  update; append-only statistics writes are not replayed. Later scheduled
  attempts use the normal baseline-read and full-build paths.
- Non-retryable mapping results are omitted, as are releases rejected by policy.
- A successful EA refresh replaces every previous split; a successful empty
  result removes them. Failed EAs and policy exclusions are both absent from the
  selected full-rebuild result, although their reasons for omission differ.
- The full-update statistics guard rejects a result whose download total falls
  below the total of the persisted unfiltered snapshot being replaced, after
  allowing for persisted default-vendor EAs absent from the rebuild. Saved
  statistics are not consulted: a failed stats write after a published omission
  would leave them stale and block healthy rebuilds, and they can lag incremental
  refreshes. The persisted snapshot supplies both baseline and allowance because
  it keeps internal releases with no binaries that reload filtering hides from
  the in-memory catalogue. Omitted EAs must not indirectly block publication
  through their missing download counts. Losses from retained EAs and GAs are
  not given this allowance.

Aborting a build before publication leaves the existing database catalogue in
place. The scheduler can continue with other update attempts; this does not
guarantee rollback of a database failure after publication has begun.

Publication to MongoDB is not atomic, so it is ordered to keep the database
usable at every point; the database is never left in a state a starting frontend
cannot load. For each non-empty feature version, every release is upserted by its
`id` (idempotent, so bounded retries are safe), and only then are releases of that
feature version whose ids are not in the new set deleted. A release is never
removed before its replacement exists. The release `id` index is unique; on
startup, legacy duplicate copies of an id are reduced to one before the unique
index is installed, and the next publication replaces that copy. Release checksums
canonicalize feature and release key order, so persisted order cannot change them.
The target checksum is written only after
every feature version has been processed. A failure at any point leaves the
previous generation marker with a catalogue made of complete, valid releases:
the previous ones, plus any already-replaced or newly added ones, and possibly
superseded releases that the next successful publication removes. There is no
incomplete marker, and readers and updater startup need no special recovery
path. A reader that loads during publication may briefly see old and new
releases together under the previous marker; it reloads when the marker advances.
Every completed publication also persists a fresh `generationId`, separate from
its content checksum and timestamp. Reader and full-build marker comparisons
include this identity, so republishing unchanged content within the same second
cannot hide an intervening change. Allocate the identity once per marker write
and reuse it across that operation's bounded retries. Legacy records without
this field retain a stable `null` identity until rewritten; reading an old record
must not manufacture a new identity.

Only non-empty feature versions replace stored records; absent
and explicitly empty feature versions are left unchanged under the scope above.
Readers reject a marker that changes while loading and keep serving their cached
catalogue. If the updater cannot read its startup baseline, startup aborts before
scheduling any updates; it never substitutes an empty catalogue for an unreadable
baseline, and can be retried once the database is readable again.
Updater startup and incremental checksum reconciliation use the unfiltered
persisted snapshot. API-only filtering must not feed publication: in particular,
internal releases with no binaries retain their download counts even though the
frontend hides them.

Each full build captures one complete persisted baseline with its generation,
instead of reusing the scheduler's startup catalogue, read under the update
lock so no incremental can change it during the build. The preservation filter
and statistics guard both use that same baseline. Full and incremental release
updates are mutually exclusive: both hold one in-process update lock for their
whole run, so an incremental publication can never land between a full build's
baseline read and its write, and no generation re-check or discard path is
needed. Scheduled incrementals use `tryLock` and skip that cycle while a full
build runs (they do not queue); the incremental that follows `fullUpdate(true)`
in the same cycle waits for the lock. On startup an incremental runs first,
before the full builds, so the latest EA is refreshed without waiting for a long
full build. That startup incremental is best effort for ordinary fetch failures: they
are logged and the full build still runs. A database failure abandons that attempt
instead; future scheduled release and attestation incrementals are registered regardless.
This is a single-updater, in-process guarantee, not a distributed
lock; a second concurrent updater is unsupported.
Updater reads enumerate the persisted release collection rather
than applying the frontend's version range.
This is the existing single-updater, in-process publication mutex, not a
distributed compare-and-swap or a transaction across independent writers.

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
aliases, independently of asset order. Among aliases, prefer the full basename
with known extensions removed before the shorter first-dot basename. Skip each
ambiguous alias and continue considering the others. Legacy aliases remain
supported when no exact sidecar exists. Cancellation during sidecar retrieval propagates through
the asset-cache and checksum-mapping boundaries; it is not treated as an
unavailable optional checksum.

A legacy basename sidecar shared by several supported artifacts (for example `foo.sha256.txt`
for both `foo.zip` and `foo.msi`) is ambiguous, so it is treated as unavailable for each
of them unless the others have their own exact sidecar. The same applies to `.json` metadata.
Cache writes are ordered per cache key, GitHub download totals are summed as `Long`,
and append-only GitHub statistics saves are not replayed after a database error.
The full-update guard uses the persisted release snapshot, not saved statistics.
Empty probed feature versions are omitted from incremental results, and
incremental selection does the cheap age and already-selected checks before the
per-release metadata lookup.

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
usage history receive one retention-period grace. Cache persistence uses
asynchronous jobs whose completion is awaited under the per-key lock. Cancelling
a fetch during that wait does not release the lock until its write finishes;
cancellation then propagates. HTTP retrieval remains cancellable. Cache reads,
demand touches and writes use the same bounded MongoDB-operation retry policy.
A permanently failed or exhausted cache write propagates to all callers sharing
that fetch and abandons the current update attempt, even if the HTTP content was
fetched successfully. Cache writes return awaitable results rather than
completion-only jobs, so database errors cannot be silently discarded. Propagated
database errors bypass HTTP retries and optional sidecar fallbacks. A failed
background refresh is logged without terminating the refresh worker; later
requests can try again.

A waiter also performs its own demand-touch read after shared success. That read
can fail after the owner has returned successfully; under the strict database
policy the waiter propagates the failure and the update is abandoned. Shared
HTTP work is not a promise that every caller's later bookkeeping has the same
outcome.

Overlapping identical fetches share pending work, including retries, but completed
requests are removed rather than becoming an additional indefinite cache.
Different asset IDs/digests and demand/background requests remain separate.
Cancelling a waiter does not cancel the shared fetch; cancelling its owner cancels
the shared result and allows a subsequent request to start again. Queued and running
background refreshes are deduplicated by URL and validation token.

MongoDB enforces a unique `url` index on the complete cache key. On startup,
migration invalidates all entries for duplicate keys, logging each invalidation,
then replaces the old non-unique index. Refetching avoids selecting potentially
stale content from ambiguously ordered legacy writes; non-duplicate keys and their
usage timestamps are preserved. Repeated initialization is idempotent.
Migration/index errors abort initialization rather than proceeding without
uniqueness. This is a startup migration: old writers must be stopped before it
runs; mixed-version writers are not supported during index replacement.

Release download counts include installer and other eligible ungrouped-asset
downloads exactly once. Attribute ungrouped downloads to the lowest-version
split, independently of GitHub asset order. Metadata-free fallback counts the
same eligible assets without duplicating those totals.
Checksum exclusions do not erase historical upstream download counts. A version
split whose binaries are all excluded can therefore remain as an internal
release with no binaries and its existing download attribution; it exposes no
binary download through the binary-filtered API results. The persisted snapshot
retains it, so the full-update statistics guard still counts it even after a
reload hides it from the in-memory catalogue.

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

## Upstream integration on 2026-11-11

The merge from `origin/main` at `5a0e8251` preserves upstream's release-note and
extended-version fixes alongside the invariants above:

- The incremental callback receives only successfully refreshed releases,
  including the latest-EA selection, even if mapped release data is unchanged.
  Failed snapshots retain old splits without notifying the callback of success.
- Release notes use the new ID-keyed upsert and unique index. Upserts and index
  creation use bounded safe-operation retries; exhausted database failures escape
  release-note isolation and abandon the scheduled update.
- Release-note URL reads retain upstream's timestamp-based synchronous
  revalidation and failed-cache-entry retry. Metadata/checksum assets keep their
  separate ID/digest-aware cache keys, pending-fetch sharing and write ordering.
- Version grouping and split identities retain upstream's patch and additional
  version-number support rather than collapsing distinct metadata versions.

The comparison below remains the recorded October baseline, not a comparison
against the November merge target.

## Behaviour comparison with upstream/main

Re-evaluated on 2026-10-30 against freshly fetched `upstream/main` at commit
`8a1357d894b9e84bfa54e38fdd31f66d73f87153`, including the current local working-tree
changes and invariant regression tests. This replaces the earlier comparisons
against `77a4d8b0` and `d9836348`. It is a fixed-baseline comparison, not a claim
that these local changes are already present in the remote PR.

| Area | Upstream at `8a1357d8` | Current code |
| --- | --- | --- |
| EA failure during a full update | An EA mapping/required-metadata failure or asset-pagination exception aborts that full-build attempt. | Log and omit the entire failed EA, including previous splits, while rebuilding other releases. GA failures still abort. Forced legacy rebuilds retain the exception described above. |
| Full-update download-count guard | Reject a feature-version download-total decrease against the last saved statistic, without allowing for omitted EAs. | Compare against the persisted unfiltered snapshot total, allowing the download counts of persisted EAs now absent from the rebuild. Retained-EA and GA losses receive no allowance. |
| Latest EA polling | An unchanged EA outside the seven-day refresh window is not necessarily fetched. | Select the latest known EA per vendor, repository and feature version even when its timestamp and asset count are unchanged. Existing upload-grace and exclusion rules still apply. |
| Feature-version discovery | Incremental updates visit only versions in the stored catalogue. | Visit the union of stored and supported versions, allowing an EA-only version omitted by a full rebuild to recover incrementally. |
| Older, recently edited EAs | Repeatedly refresh them while their edit timestamp remains within seven days. | An actual timestamp change still triggers refresh, but recent edits alone no longer repeatedly select older, already-known EAs. Recent publication still does. |
| Non-retryable incremental failures | Remove their previous splits. No builder exclusion set prevents subsequent selection. | Retain previous splits and suppress further incremental attempts until a full build clears the builder exclusion set. |
| Unparsable release versions | Mapping failure is retryable by default; there is no mapper-lifetime parsing blacklist. | Non-retryable: the builder suppresses further incremental attempts until the next full build clears its exclusion set and re-attempts the release. The mapper keeps no memory, so a corrected upstream release recovers at the next full build without a restart. |
| Metadata/checksum cache freshness | Use URL-based caching. Replacing an asset at the same URL can reuse cached content until validation refreshes it. | Key asset reads by URL plus GitHub asset ID/digest. Identity changes cause misses; assets without either identity field are fetched on demand. |
| Checksums | Use metadata SHA-256, then checksum sidecars. | Compare a valid GitHub SHA-256 with available metadata/sidecar checksums and exclude only the affected artifact on disagreement. Otherwise prefer GitHub, then the existing fallbacks. Downloaded sidecars with supplied digests are validated before use/caching. |
| Installer metadata | Do not load installer metadata for validation; use its checksum sidecar for the installer checksum. | Load matching installer metadata through the asset-aware cache and include its SHA-256 in agreement checks. Missing metadata assets are allowed; listed metadata that cannot be read or parsed excludes only the installer, not its archive. |
| Cache writes | Wait for completion-only jobs; their asynchronous write failure does not propagate through `join()`. | Await the result under a per-key lock, retaining ordering through cancellation. Permanent or exhausted cache-write failure fails the shared fetch and aborts the update. |
| Updater baseline | Use the API's filtered, serving-version-limited projection for startup and checksum reconciliation. Full builds preserve from the supplied in-memory catalogue. | Read the entire persisted catalogue, including zero-binary accounting records. Each full build captures its own baseline and generation for both preservation and statistics. |
| Publication and readers | Write the target generation in `finally`, even after failed release writes. Reader loads do not reject an incomplete or changing generation. | Upsert-then-delete release writes, commit the generation only after success, and reject changing reader snapshots. Full and incremental updates hold one update lock, so a full build cannot be overtaken by an incremental publication. |
| Database failures | Several release/optional-artifact boundaries can log and downgrade database failures. Failed full updates can continue into subsequent phases. | Retry known transient failures only at individual safe Mongo-operation boundaries, then propagate to the scheduler and stop the current attempt. Keep later scheduled attempts alive. |
| Attestation generation | Advance the generation in `finally`, even after failed attestation writes. | Advance only after successful writes; do not replay ambiguous inserts. This does not add the release catalogue's upsert-then-delete ordering to CDXA. |
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

The daily/six-minute scheduling cadence and full-update age/vendor filters are
unchanged. Publication generation checks and scheduled database-failure handling
are strengthened as described above; they are not unchanged upstream behaviour.
The accepted limitations under
[Assumptions and scope](#assumptions-and-scope) still apply; the merge does not add
historical-EA recovery or whole-feature-version database cleanup.

Additional implementation differences from this baseline are:

- Installer metadata lookup prefers an exact full-filename match before legacy
  naming fallbacks; upstream does not load installer metadata.
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
- GraphQL decoding mismatches are logged and rethrown by the query helper,
  matching upstream. Both paginated release reads and non-paginated CDXA
  summaries abort rather than interpreting malformed data as missing records.
  Valid missing-repository or missing-folder responses retain their existing
  empty-result semantics; a decoding failure is not such a response.

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
- [Unfiltered baseline and reader generation checks](../../adoptium-api-v3-persistence/src/main/kotlin/net/adoptium/api/v3/dataSources/APIDataStoreImpl.kt)
- [Mongo publication](../../adoptium-api-v3-persistence/src/main/kotlin/net/adoptium/api/v3/dataSources/persitence/mongo/MongoApiPersistence.kt)
- [Bounded safe-operation retries](../../adoptium-api-v3-persistence/src/main/kotlin/net/adoptium/api/v3/dataSources/persitence/mongo/MongoInterface.kt)
- [Cache ordering, sharing and failure propagation](../../adoptium-updater-parent/adoptium-datasources-parent/adoptium-github-datasource/src/main/kotlin/net/adoptium/api/v3/dataSources/github/CachedGitHubHtmlClient.kt)

## Regression ownership

The following suites live under
[`adoptium-api-v3-updater/src/test/kotlin/net/adoptium/api`](../../adoptium-updater-parent/adoptium-api-v3-updater/src/test/kotlin/net/adoptium/api).
They identify where to add a boundary regression; passing suites are evidence,
not proof that every interleaving or failure is covered.

| Invariant | Principal regression suites |
| --- | --- |
| I1 | `MongoAPIPersistenceTests`, `UpdaterSnapshotTest`, `AdoptReleaseMapperTest` |
| I2 | `PublicationInvariantTest` |
| I3 | `MongoAPIPersistenceTests`, `ReleaseGenerationReadTest`, `DatabaseFailurePolicyTest` |
| I4 | `AdoptReposBuilderSplitReleaseTest`, `AdoptReleaseMapperTest`, `GraphQLGitHubReleaseClientTest` |
| I5 | `MongoAPIPersistenceTests`, `PublicationInvariantTest`, `AdoptReleaseMapperTest` |
| I6 | `GitHubAssetCacheRefreshTest`, `AdoptReleaseMapperTest` |
| I7 | `CachedGitHubHtmlClientTest`, `GitHubAssetCacheRefreshTest`, `InternalDbStoreTests` |
| I8 | `MongoRetryTest`, `DatabaseFailurePolicyTest`, `CachedGitHubHtmlClientTest`, `v3/stats/StatsInterfaceTest` |
