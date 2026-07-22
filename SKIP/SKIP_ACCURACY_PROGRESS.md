# Progress: accurate skip-segment selection + optional boundary refinement

Tracking implementation of `SKIP/SKIP_ACCURACY_PLAN.md`.
Branch: `feature/skip-segment-voting`.

Legend: `[ ]` pending · `[~]` in progress · `[x]` done

## Step 0 — logistics
- [x] Branch `feature/skip-segment-voting` created off `master`
- [x] Plan saved to `SKIP/SKIP_ACCURACY_PLAN.md`
- [x] Progress journal started (`SKIP/SKIP_ACCURACY_PROGRESS.md`)

## Part A — P0 voting (accuracy)
- [x] **A1** `SkipSegment` model: `Category`, `CoordBase`, `timeTrust`, `confirmed`; keep legacy ctor
- [x] **A2** `SegmentFinder`: parallel profile probe (`probeAll` + `CountDownLatch` + `AtomicReferenceArray`)
- [x] **A2** Two order profiles (anime / non-anime); absolute sources to tail
- [x] **A2** `voteSegments`/`bestCluster`: group by category, cluster by tolerance, ≥2 = confirmed, timing from top `timeTrust`, no averaging; one segment per category (dedup/anti-phantom)
- [x] **A2** Cache key with duration bucket; negative-result TTL (10 min) via `CacheEntry`
- [x] **A2** Fix `isMovie` gate (season present → series profile even if episode 0)
- [x] **A2** Remove all debug logging (`TAG`, `Log.d`, `fmt`, `Candidate.name`, TODO) — whole class rewritten clean
- [x] **A3** `NetworkSegmentsSource`: preserve voting metadata + `confirmed` in copy loop; open-ended `99999` capped to CREDITS in `addMsArray`
- [x] **A3** UI policy in `skipTick`: unchanged (single = auto-skip per mode; `confirmed` kept for future)
- [x] **A4** Centralize constants (`AGREE_TOLERANCE_SEC=30`, `MIN_VOTES=2`, `PROBE_DEADLINE_SEC`, `NEG_CACHE_TTL_MS`, `TT_*`)

## Part B — manual skip offset only (content refinement REMOVED)
> **Decision (user feedback):** the content-based video refinement (black-frame + scene-cut) was
> tried on-device and did not work well enough, so it was **removed entirely**. Only the deterministic
> manual offset is kept. `SegmentRefiner` deleted; `SkipManager.applyRefined`, the `PlayerActivity`
> refiner hooks, the `skipRefineScene`/`skipRefineBlack` prefs + switches + strings, and
> `Utils.isContentRefinementCapable` are all gone. `Utils` reverted to package-private.
- [x] ~~Manual `skipOffset` ListPreference in settings~~ → **replaced by a session-only in-player
  control** (user feedback: offset should be session-scoped and reachable from the player, not global
  settings). Persisted pref/`ListPreference`/arrays/`parseIntSafe`/`pref_skip_offset` removed.
- [x] **Session skip-offset control**: `skipOffsetSec` + `skipSeenThisSession` session fields on
  `PlayerActivity` (reset in `resetApiAccess`); dedicated bottom-row button `buttonSkipOffset` next to
  the gear, shown only when a skip segment exists now or earlier this session (`updateSkipOffsetButton`).
- [x] **Slider panel** `showSkipOffsetDialog` (end-docked translucent, like quality): value readout +
  `SeekBar` (touch fine 0.25 s; TV D-pad `keyProgressIncrement`=0.5 s, focusable) + −/+ + Reset; range
  ±30 s. `applySkipOffset` → `SkipManager.setOffsetSec` + `rebuildSkip` (live highlight move).
- [x] `SkipManager.setOffsetSec`/`applyOffset` kept as-is (already in-memory). New icon
  `ic_skip_offset_24dp`; strings `skip_offset_title`/`skip_offset_reset`/`button_skip_offset` (en/uk/ru).

## Build & verification

### Verified (this session)
- [x] `./gradlew assembleLatestUniversalDebug` green (compiled/dexed/packaged; only pre-existing Java-8 source/target warnings)
- [x] Cleanliness: no `SkipSegments`/`Log.d`/`TODO(debug)`/Claude mentions in changed code

### NOT verified — needs a real device / emulator run
> The whole feature has been built but **not exercised at runtime** — no playback, no live network
> lookups, no device profiling were done. All behavioral claims below are unverified. Run this
> checklist before relying on it / merging. Watch `adb logcat` for crashes; there is no debug logging
> in the code, so add temporary logging locally if needed while testing, then remove it.

P0 — voting / accuracy:
- [ ] **Non-anime** (Breaking Bad `tt0903747` S1E1 via LAMPA): intro/credits fire on time; the
  duration-aware SkipDB timing is used, an absolute source (TheIntroDB) does not overtake it or apply
  a shifted time.
- [ ] **Anime** (Attack on Titan `tt2560140` S1E1): Aniskip is primary; its OP/ED timing is used and
  SkipMe/others do not override it.
- [ ] **Voting / phantom control**: when ≥2 sources agree, the segment is used with the most
  file-accurate timing; conflicting single-source picks in a category do not produce a double intro.
- [ ] **Single-source coverage**: a category reported by only one source is still offered/auto-skipped
  (per the "as before" decision) — coverage not lost.
- [ ] **Replay-shift**: replaying the same title from a differently-cut rip (different duration) does
  NOT reuse stale cached timings (duration-bucket cache key).
- [ ] **Negative-cache TTL**: a title that returned nothing after a transient network error is
  re-probed later in the same process (within ~10 min TTL), not silenced permanently.
- [ ] **Latency**: parallel profile probe completes within roughly one source timeout (not the sum);
  first skip is offered promptly.
- [ ] **Playback safety**: all sources missing / offline → no segments, no crash, playback unaffected;
  open-ended credits do not stall the player.

P1 — content refinement (default OFF; enable the "Refine segments from video" toggle to test):
- [ ] **Fade snap**: local MP4/MKV with a known fade-out intro → intro end snaps to the black frame
  (small nudge); credits start likewise.
- [ ] **No-op safety**: a file with no black boundary in the window → boundary unchanged (no wrong snap).
- [ ] **Ineligible sources**: HLS/DASH URL → refiner bails ("ineligible"), no retriever work; DRM
  content untouched.
- [ ] **Cancellation**: switching episode mid-refine interrupts the worker, no late callback applied.
- [ ] **Budget**: 4K long-GOP file → graceful abort within the ~10s wall-clock budget, playback smooth.
- [ ] **Gating**: toggle is switchable on any device (no grey-out); API 26 → enabling it is a safe
  runtime no-op; TV box + >1080p (or any >2160p) → per-media refusal (no second decoder spun up).

Non-regression:
- [ ] With the refine pref OFF (default), playback and skip behavior are byte-identical to before P1.

Note: fix during build — `Utils` was package-private; made `public` so the `skip` subpackage can call
`isProgressiveContainerUri` / `isContentRefinementCapable`.

## Notes / decisions
- Single unconfirmed segment: behaves as before (auto-skip per mode) — coverage prioritized.
- Probe budget: whole profile, done in parallel to bound latency.
- Chapters + silence-detection: out of scope (deferred); model leaves `CoordBase.CHAPTER` headroom.

## Log
- (init) Step 0 complete: branch + plan + progress artifacts in `SKIP/`.
- P0 (A1–A4) + P1 (B1–B4) implemented; debug logging removed; `Utils` made public.
- Build `assembleLatestUniversalDebug` green. Remaining: on-device verification checklist.
