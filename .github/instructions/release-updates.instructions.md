---
applyTo: "adoptium-updater-parent/**,adoptium-api-v3-persistence/**,adoptium-models-parent/**,docs/adr/0008-release-snapshot-update-semantics.md"
---

# Release updater review context

The goal is active-EA refresh and complete upstream-release replacement, not
general recovery. ADR 8 records the decisions:
`docs/adr/0008-release-snapshot-update-semantics.md`.
Review selection, mapping, publication, reload and cache together. The essential
contracts are repeated here rather than relying on loading the ADR.

## Required invariants

- I1: Updater baselines include all persisted feature versions and zero-binary
  releases with their download counts. Frontend filtering and serving-version
  caps must not feed preservation, checksums or the full-update statistics guard.
- I2: A full build uses one persisted baseline for preservation and statistics.
  Full and incremental updates are mutually exclusive via one in-process update
  lock held for the whole run, so the baseline cannot change during a build and
  no generation re-check is needed. Scheduled incrementals skip (not queue)
  while a full build runs; startup runs an incremental before the full builds.
- I3: The release database must be usable at every point. Upsert releases by id
  before deleting superseded ones; publish the target generation only after all
  release writes succeed. Readers reject changing generations. Failed startup
  reads must not become successful empty updater baselines.
- I4: Incremental success replaces every split for the exact normalized upstream
  ID, including successful empty results. Ordinary incremental failures retain
  the old complete snapshot. Full builds omit failed EAs, without carrying their
  old splits forward; GA and repository-wide listing failures still abort.
- I5: Nonempty release-feature replacements remove obsolete records. Missing or
  empty features retain stored records. Check download loss across stored and
  candidate versions; only absent EAs receive the persisted-download allowance,
  not retained EAs or GAs.
- I6: Prefer exact sidecars, then unambiguous aliases, independently of asset
  order. A checksum disagreement excludes only the affected binary or installer;
  other required-metadata or binary-mapping failures reject the snapshot.
- I7: Cache identity includes available asset ID/digest; without either, fetch
  on demand. Share only pending fetches. Keep per-key write ordering through
  cancellation and await write failures; cache persistence is not best-effort.
- I8: Retry only known transient Mongo failures on individual reads or repeat-safe
  writes: three application attempts, with 100/200 ms backoff. Never replay
  append-only inserts or a whole publication. Propagate permanent/exhausted Mongo
  failures past release isolation, HTTP retries and optional-artifact fallbacks,
  abort the scheduled attempt, and leave future scheduled attempts alive.

## Accepted scope and trade-offs

- Historical EAs are assumed unchanged. Active/latest EAs still refresh.
  `UPDATE_ADOPTOPENJDK=true` is exceptional; a date-classified legacy EA whose
  GitHub prerelease flag is false may abort that forced full build.
- Whole-feature-version deletion and disappearance of all metadata from a
  formerly metadata-based release are unsupported. Preserve genuinely
  metadata-free legacy fallback; do not add historical state solely for these cases.
- `Release.source` is outside binary checksum exclusion. Do not download archives
  to verify completeness or add a retry queue or per-release deadline.
- Database failure does not roll back completed writes; the ordering above keeps
  the catalogue loadable, and the next successful publication removes leftovers.
  Existing driver retries are separate from the application retry budget.
- A failed release publication can leave an EA with a mix of old and new splits
  until the next incremental update replaces every split. This is accepted: EAs
  are less critical and the state is short-lived. Do not report it, and do not
  add an incomplete marker, persisted failure flag, `ALL`-mode recovery rebuild
  or pointer switch to prevent it. The catalogue must stay loadable (I3).
- A shared-fetch waiter can fail its own demand-touch read after its owner
  succeeded. This follows the strict database-failure policy, not a guarantee of
  identical outcomes for every caller.

Report supported-scenario bugs with their trigger, broken invariant and owning
boundary. Prefer a boundary regression over another fallback. ADR 8 maps the suites.
