# Prospective transit experiment: results and remaining phase-3 gate

Status: **NOT_PASSED**. All four registered evaluations and both post-evaluation
diagnoses finished. No production activation, Java/configuration changes, database
writes, commits or deployment. No jobs remain running at the final checkpoint.

## Implemented and verified

Separate transit evaluator, fixed-date acquirer, registration/freezer and paired
census audit; original frozen April/May/June code and reports are untouched.
The new registration binds 22 implementation files. Cohort selection uses original
source observations before chart/routing/metric outcomes, preserving all eligible
segments, out-of-scope curves, hash-unselected transits and selected failures.

Full Python suite: **101 tests passed**. New checks cover selection, actual synthetic
search/metrics, source conflicts, registration mutation, training leakage, chronology,
acquisition truncation/format/size limits, overwrite rejection, paired census and
cost reconstruction. These are software checks, not independent maritime evidence.
Java was unchanged and not rerun in this continuation; previous Java results remain
historical. Frozen implementation hashes for all three registrations still match.

## Prospective registration and sources

- [Protocol](transit-holdout-protocol-20260916.md), unchanged since registration.
- [Registration](transit-holdout-registration-20260916.json), SHA-256
  `750a3da6c85cc97943edd3c0f3ecd263bf4bf15bcc2c423b2b97b344cf4c8876`.
- Registered at `2026-09-17T00:56:45.678908+00:00` (September 16 local).
- Replay bundle `transit-frozen-implementation-20260916.zip`, SHA-256
  `7ef607a53d67d0f8e9110166974af46ec1469f0a8b813a6a61d421ad7bc46db5`.
- March model unchanged, SHA-256
  `69a8dff41b46166cf416a7e44434b86cd81da1b331eaa93876dc566e198207a0`.
- July download started `2026-09-17T00:58:36.150817+00:00`: 263,429,168 bytes,
  SHA-256 `44d67c37f2b35f8e4cb33e802c0285646710edf6bd3f0e31720d69ab10d44c3b`.
- August download started `2026-09-17T01:00:18.472660+00:00`: 302,467,362 bytes,
  SHA-256 `d745cfaf6a1da4ed7eb62308f1ecbff157652dbfa1241e4651b63b41023b2452`.

Both receipts bind the exact registration and acquirer. Initial sandbox socket
denial was followed by approved network acquisition, not reported as NOAA failure.
Local registration is not third-party timestamp attestation. No model tuning
occurred between July distance, July directed, August distance and August directed.

## Complete census and acceptance

July: 8,995,093 CSV rows, 26 regional merchant vessels, 30 original eligible
segments: 26 transit-eligible, four outside scope, twenty selected and six eligible
but hash-unselected. August: 10,062,722 rows, 42 regional merchant vessels, 44
original eligible segments: 39 transit-eligible, five outside scope, twenty selected
and nineteen hash-unselected. Both objectives have identical full censuses and
selected source rows/endpoints/timestamps per date. Out-of-scope does not mean bad
AIS or verified manoeuvre intent. Multiple segments may share a vessel.

Unchanged acceptance per date/model: >=10 selected, >=80% measurable, every measured
Hausdorff and Frechet <=1,000 m, zero predicted chart-water conflicts.

| Date / objective | Measured | Over 1 km | Median Frechet | Max Frechet | Time | Peak process working set |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| July / distance | 20/20 | 11 | 1,031.32 m | 1,278.00 m | 121.66 s | 176,254,976 B |
| July / directed AIS | 20/20 | 1 | 346.64 m | 1,213.62 m | 129.50 s | 181,207,040 B |
| August / distance | 18/20 | 13 | 1,154.75 m | 2,184.47 m | 113.76 s | 175,808,512 B |
| August / directed AIS | 18/20 | 1 | 330.04 m | 1,129.17 m | 133.74 s | 181,583,872 B |

All four fail spatial acceptance. No predicted chart-water conflicts. Paired
Frechet comparisons: July 17 improved / 3 regressed; August 16 improved / 2 regressed;
none unchanged at a 0.01 m descriptive reporting tolerance (not an acceptance
tolerance). August's two unmeasurable cases remain in the twenty-case denominator.
Runtime is whole offline validation, including source scans; not serving latency,
p95, throughput or an SLA. Some runs overlapped small tests or data download.

## Why the remaining failures are different from the returning curves

Re-read original AIS rows and checked identity, endpoints and timestamps. The two
measured failures are ordinary displacement-dominated transits in conservative
chart water, not the previous near-returning excursions:

| Failed directed case | Endpoint separation | Observed length | Baseline Frechet | Directed Frechet |
| --- | ---: | ---: | ---: | ---: |
| July `a7961ff4...` | 10,813.43 m | 11,863.49 m | 741.61 m | 1,213.62 m |
| August `9a05418b...` | 10,966.54 m | 11,834.36 m | 657.04 m | 1,129.17 m |

Reconstructed physical and preference costs from actual graph edges/connectors and
the unchanged training model. Both directed routes have a lower declared objective
than their distance-only alternatives, despite higher observed-shape error:

- July: physical distance increases 281.03 m; unsupported directed distance drops
  1,163.93 m; objective improves 882.90 m, while Frechet worsens 472.01 m.
- August: physical distance increases 294.04 m; unsupported directed distance drops
  1,561.99 m; objective improves 1,267.95 m, while Frechet worsens 472.14 m.

This supports a route-preference generalization problem, not evidence that clearing
hazards, moving endpoints or raising the error limit would fix it. It does not
prove every aspect of the search implementation correct. Selecting the distance
route only for these known failures would leak the observed answer; it is not a fix.
The endpoint-excursion lower-bound diagnostic found zero objective incompatibilities;
that test's absence of a bound violation is not proof of route-choice accuracy.

August cases `0e839a40...` and `6f960903...` leave positive known Group-1 chart water.
No source-exclusion feature intersections were attributed. Missing positive water
coverage is not proof that the AIS is wrong, that land is absent or that clearance
can be assumed. The physical mask and all unresolved restrictions remain unchanged.

## Immutable report hashes

All report filenames below are under ignored `data/maritime-routing/`.

| Report | SHA-256 |
| --- | --- |
| transit-july-distance-20260916.json | c6f0aed3dd60c0c955d89843c5d40ece34eb1f2d83debfe7a49ff35a0ae6c65d |
| transit-july-directed-20260916.json | c96b382136b4bd84438db94e2ccabb009950dac08398b5d644ded96dc3712879 |
| transit-july-paired-20260916.json | 71153ca83a627afef0ec9a87c0b08933cb60bbf7e0e7b1318da013b5262c98c5 |
| transit-august-distance-20260916.json | ad2c8d002243151df6a646f8a7eb594e04f0aa3d666f0f9b515a88098449ecdb |
| transit-august-directed-20260916.json | b758500fd6affcc1333ed087112fa0482a986250c2ee7f23ca6087af3ea6439d |
| transit-august-paired-20260916.json | ec1d6a1a7ff60b193e145c8a6ba874877ece2ab5cf9266ed2f47ef61e0d89849 |
| transit-july-geometry-diagnosis-20260916.json | 266caaf966a62f6f646872b494653ea8ee666030124cb5fe6115648fa25649f6 |
| transit-august-geometry-diagnosis-20260916.json | 83faf00e4cfa317f31a9e13650ebcbb76440b38751f43ecad522f9358f1b3879 |
| transit-july-preferences-v2-20260916.json | a902cda80c71f19b8e2e84bef67373702b79f3391d7fba8395500ee43e04ff13 |
| transit-august-preferences-v2-20260916.json | ce3fac6f050dcce98077610df17388a9c983604cc06a5b74477bdc3354367e0e |

Preference attribution v2 adds rejection of nonfinite/boolean recorded costs;
prior attribution outputs are preserved. Neither version changes any evaluation
output or registered implementation. Reporting/diagnosis is post hoc and separate
from the frozen predictive experiment.

Exact current reporting/diagnosis helpers and new tests are archived separately as
`transit-reporting-tools-20260916.zip`, SHA-256
`6485dcb7687f00b77b9bd91215aa1a46b5a3f3290ce00e3d9fb2d25b468eedae`.
This is a post-evaluation replay archive, not a preregistration of those diagnostics.

## What is required next, without claiming completion

1. Develop a new, separately versioned route-choice model with a declared training
   set and grouped development validation. Test whether origin/destination-conditioned
   coherent training routes generalize better than a global binary edge-support
   reward; this is an untested hypothesis, not an implemented fix. Do not silently
   tune the old model or use per-case switches learned from these outcomes.
2. Preserve July/August as completed failed holdouts; once they inform design they
   are development evidence for the next experiment. Freeze new untouched dates
   and unchanged criteria before acquisition. Further sequential experimentation
   must remain visible; do not report only a later successful attempt.
3. Obtain authoritative positive-water/access/channel/expected-passage evidence
   where absent. Global coverage, verified water-side terminal associations and
   operational constraints remain independent gaps. No permission can be inferred
   from AIS or from passing this regional experiment.

The approved regional pilot is not a scope decision to certify a worldwide product.
Completing global operational coverage can require external source access, licence
decisions or expert/authority review; no accounts, paid access, licence acceptance
or third-party messages were initiated. A fresh web-reader check of the official
[NGA WPI page](https://msi.nga.mil/Publications/WPI) and its published map service
again timed out; no worldwide-data unavailability conclusion follows from that.
