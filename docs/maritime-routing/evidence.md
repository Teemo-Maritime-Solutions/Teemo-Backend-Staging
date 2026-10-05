# Phase evidence

Current phase 7 scope: **PHASE7_INTEGRATED_RESEARCH_PLANS: PASS**.
[Results and reproduction](phase7-results.md): 16 closure checks, 89 Java tests,
actual weather routes linked to declared contracts/scenarios, immutable plans,
provenance/explanations and a real closure-induced recalculation. The 54 prior
bound files, previous evidence and physical/forecast pins match. All 16,330
regional restrictions remain blocked. Local storage must be configured; public
deployment and owner authorization remain outside scope. Closure is preserved in
`data/maritime-routing/phase7-evidence-v1/status.json`.

Completed phase 6 scope: **PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH: PASS**.
The user explicitly chose declared research probabilities without a calibration
claim. [Results](phase6-results.md): 17 checks, 80 Java tests, joint seeded
simulation, actor metrics, Monte Carlo bounds and explicit-objective Pareto.
The 41 prior bound files and physical/forecast pins remain unchanged. Phase 5
also remains [PASS](phase5-results.md). Historical
entries below retain their original dates and limitations.

Current phase 4 scope: **PHASE4_VERSIONED_WEATHER_PARAMETRIC_RESEARCH: PASS**,
2026-09-22. [Results and limitations](phase4-results-20260922.md): 17 executable
checks; original global ECMWF forecast; three basin scenarios; 33 Java and 3 Python
tests; preserved phase 3 bindings and New York hard restrictions. The vessel model
is explicitly user-selected parametric research. Weather availability at source
port points is 729/1,114, not worldwide port-to-port weather coverage. Fuel/emissions
remain unavailable. Persistent closure: `phase4-evidence-v1-20260922/status.json`.

Completed phase 3 scope (user-authorized 2026-09-21):
[PHASE3_GLOBAL_RESEARCH_V2](phase3-research-plan-20260921.md), **PASS on 2026-09-22**.
[Combined-artifact results](phase3-research-results-20260921.md): 1,178,003 reciprocal
pairs accounted for, preserved regional controls, nine registered world routes,
117 Python and 25 Java tests passed. The separate executable closure report is
`phase3-research-status-v2-20260922.json`. Full global operational/trajectory evidence
is not a prerequisite or an established claim for this scope. Continuation reports
below retain the original gate's historical results.

Latest continuation (2026-09-21): [coherent O/D results](od-results-20260921.md).
Both new registered temporal dates PASS the unchanged regional physical transit
criterion: September 19/20 measurable, maximum Frechet 769.60 m; October 13/15,
883.36 m. All failed observations remain counted. The frozen 27-file implementation
and six-date model were registered before acquisition; no between-date tuning.
Grouped development retains three failed cases/months and all historical holdouts.
110 Python tests pass; prior registrations match; Java unchanged/not rerun.

Global phase 3 remains NOT_PASSED. New independent UKHO source coverage audit finds
51/60 traffic lanes without a mesh node (Dover 9/12); no operational import or
expected-passage compliance is inferred. Wider authoritative geometry/access and
validation remain required. Regional acceptance and global gaps are separately
reported in the executable `phase3-status-20260921.json` audit.

Previous continuation (2026-09-16): [prospective transit results](transit-results-20260916.md).
Separate evaluator and 22-file registration; July/August acquired only after freeze.
Full candidate census retained and both models evaluated without tuning. July
20/20 measured and August 18/20; directed AIS reduces >1 km failures from 11/13 to
one per date, but maxima 1,213.62/1,129.17 m still FAIL. Post-hoc cost attribution
shows a preference generalization issue, not the previous near-returning task
mismatch. 101 Python tests pass; Java unchanged/not rerun. No operational activation
or global pass; no jobs remain running. Full source chronology, hashes, commands
and limits are recorded in the linked report and runbook.

Previous continuation: [directed AIS results](corridor-results-20260915.md) and
[progress.md](progress.md). March-only sequential/directional preference model
implemented without altering the physical graph or unresolved restrictions. May
and June were both preregistered before acquisition: 18/20 and 19/20 measurable,
spatial failures reduced from 11/12 to two per date, but both still FAIL <=1 km.
Near-returning original trajectories expose an endpoint-only prediction limitation;
no failures were removed. 88 Python tests and 56 targeted Java tests passed,
including real regional V2 negative checks and eight global source-reference pairs
with actual dateline/equator crossings, alternatives and HTTP checks. No operational
activation. Global phase 3 remains NOT_PASSED; later phases remain gated.
The user confirmed origin/destination transit recommendation as the target task.
The [transit experiment](transit-next-experiment.md) subsequently implemented that
scope prospectively and completed July/August evaluation; see the latest result
above. No revised-cohort success is claimed.

Previous continuation: [regional V2 results](regional-v2-results-20260914.md), [progress.md](progress.md) and the
[April prospective protocol](april-holdout-protocol-20260914.md). Corrected regional
Group-1 water/bridge model passes complete geometry checks (8,089 reciprocal pairs),
with deterministic repeated builds. March development improves from 0/20 to 17/20
measurable but still fails the unchanged spatial target. April's frozen comparison
measures 18/20, reduces maximum Frechet from 2,409.63 to 1,217.15 m, and still FAILS
the unchanged 1 km criterion. 70 Python tests pass;
no Java rerun or edits in this continuation. Global gate remains NOT_PASSED.

Earlier continuation: [semantic-pilot.md](semantic-pilot.md). Isolated semantic
resolution implemented with 14 conditional / 4 unresolved chart features, preserved
terminal coordinates and explicit water-side endpoint curation. 57 source-coordinate
candidates / 535 clear connectors verified but no terminal association asserted.
39 Python tests and 68 Java tests pass; zero real semantic routes/activated endpoints.
Global phase 3 remains NOT_PASSED and the global graph/production API are unchanged.

| Phase | State | Evidence / outstanding work |
| --- | --- | --- |
| 0 audit | Implemented | architecture.md; initial clean worktree; baseline command below passed. |
| 1 global graph | Research mesh implemented; incomplete operational layers | Actual source-backed H3 mesh, country-audited UN/LOCODE references, immutable indexed artifact. Regional directed AIS preference now available offline, not a global overlay. Authoritative approaches/channels incomplete. |
| 2 routing | Static research engine implemented | Directed A*, separate contextual costs/constraints, bounded Yen/geographic portfolio, distance/time availability, explanations. Time-dependent weather search not yet implemented. |
| 3 revised research gate | PASS | Combined artifact v4, complete geometry accounting, New York enforcement, route coverage metadata, nine world cases and 14 closure checks pass. Original global gate remains NOT_PASSED in its historical report. |
| 4 dynamic/vessel | Research PASS | Versioned ECMWF inputs and declared vessel model; see phase4-results-20260922.md. |
| 5 Incoterms/cargo | Research PASS | Eleven terms, separate actor costs/risk/control; legacy fixed estimates excluded. See phase5-results.md. |
| 6 uncertainty/Pareto | Declared-scenario research PASS | User-approved uncalibrated joint distributions, seeded draws, risk metrics and Pareto; no empirical probability validation. See phase6-results.md. |
| 7 API | Research subset implemented | Graph/provenance/ports/K-routes. Operational/business comparisons and dynamic recalculation remain gated. Intentional legacy 503 migration documented in api.md. |

Baseline (before edits):

```powershell
mvn -q "-Dtest=RouteCalculatorServiceImplTest,RouteGraphBuilderOverlayTest,RouteServiceMaritimeGraphTest,MaritimeGraphLandSafetyTest,GlobalFishingWatchCorridorOverlayProviderTest" test
```

Maven needed network approval to resolve dependencies; authorized run exited 0.
WPI viewer TLS trust error and official CSV HTTP 403 occurred after authorized
network access. Certificates were not bypassed. UN/LOCODE 2025-1 was acquired from
the official UN release instead; this does not turn reference points into berths.

## Measured research snapshot

`data/maritime-routing/global-audited-v2.zip`:

- graphVersion `bb0c6f9213dc70e8a954409b9a0561c7a7b54b116ec1eecece95357b6f43347b`;
- SHA-256 `fdc1229b166406d6f42890ae56200a79e105c48396ceb881eeed737ca489d81a`;
- 21,170,346 bytes, 45,520 nodes, 823,784 directed edges;
- 1,320 strong components; largest has 43,486 nodes;
- 17,520 source port references, 1,113 connected after country QA;
- 104 previously connected references quarantined for country QA problems;
- connected references: north 954 / south 159, east 730 / west 383.

Other port statuses: 5,792 missing coordinates, 9,728 on-land requiring sourced
approach, 580 without a clear connector, 139 disconnected, 123 country mismatch,
44 unavailable country geometry and 1 conflicting source coordinate record.
No guessed relocation. Coarse country consistency is not port/berth verification.

The earlier high-resolution (`h`, not `f`) coastline snapshot was rejected: 11,501
conflicts in 418,452 reciprocal pairs against the full mask/conservative quarantine.
These include invalid-source exclusion envelopes, not all proven shoreline crossings.
Rebuilding with full GSHHG and 500 m geodesic checks gave 411,892 checked pairs,
zero conflicts and zero distance mismatches (398.06 seconds). Both checks share
historical GSHHG lineage; neither is independent current hydrographic truth.

Latest validator additionally verifies that reverse geometries really match and
every directed edge has its counterpart. Its post-pause full run **passed**:
411,892 pairs, zero geometric conflicts, zero distance mismatches, complete
reciprocal geometry verification, 775.44 seconds. Report:
`global-audited-v2.checked.validation.json`. The interrupted earlier run was not
counted as completed evidence. Overall phase 3 remains NOT PASSED.

## Refined coastal snapshot

Prespecified H3 coast level 3 to 4 experiment, without AIS-derived nodes:

- Audited artifact `global-coastal-r4-audited-v1.zip`, 59,792,089 bytes.
- graphVersion `2ff9db4a8ba536ce0a7e61b92012a61a8ab4deca555811d6ee1d2b95c0e3f096`.
- SHA-256 `bcc127e48f59d6ff6b6081e8e94de8cff9bdc704873f98dc93be0876aa32a04a`.
- Build: 1,906.27 seconds, 98,811 nodes, 2,335,476 directed edges.
- 1,529 strong components; largest has 96,401 nodes.
- 1,114 connected port references after country QA, only one more than previously.
- Country QA excludes 105 previously connected references; no coordinate edits.
- `edges.jsonl` expands to 845,769,688 bytes; exceeds the default Java entry cap.

New independent directed-connectivity validator recomputes components from table
bytes, verifies original node/port/edge endpoint relations and manifest counts,
and separately excludes prohibited/unresolved restrictions. Both runs passed:

| Snapshot | Covered references | Ordered distinct covered pairs | Seconds |
| --- | ---: | ---: | ---: |
| Previous coast 3 | 1,113 | 1,237,656 | 8.19 |
| Refined coast 4 | 1,114 | 1,239,882 | 37.32 |

Same strong component proves path existence for every covered ordered pair; it
does not mean a million searches were benchmarked or navigation was certified.
Reports: `global-audited-v2.connectivity.json` and
`global-coastal-r4-audited-v1.connectivity.json`. Unknown legal status is allowed
only in the explicitly research-only policy.

Full refined geometry validation **passed**: 1,167,738 reciprocal pairs (all
2,335,476 directed edges), zero conflicts against full GSHHG and zero distance
mismatches; reciprocal geometry/completeness verified. Time: 1,525.64 seconds.
Report: `global-coastal-r4-audited-v1.validation.json`. Validator SHA-256
`93ec7e3fc450fab3f44f54768cea2a07922a29faa24feb4a91ea649049ae0238` matches the
latest full check on the previous snapshot. Connected reference hemispheres:
north 955 / south 159, east 731 / west 383. Same historical coastline lineage,
not independent hydrographic truth. The geometry report's AIS/channel fields
are not an aggregate of later diagnostics; paired AIS evidence is separately
recorded below. No automatic promotion of this graph.

## Regional AIS negative evidence

Official NOAA 2024-01-01 daily file: 196,492,824 bytes, SHA-256
`04e80b2f2896f51a7623e098eb48503b95786c9badf06550ce9e7d418654cb2d`.
Receipt and raw source kept under ignored `snapshot-20260908/`.
Report: `data/maritime-routing/ais-validation-v1.json`, 99.70 seconds.

Scanned 7,293,408 rows; 897,549 cargo/tanker rows, 1,741 eligible vessels.
Deterministic pre-routing selection: 64 vessels, 31,923 observations, 40 eligible
track segments, 20 hash-selected cases. Outcomes include all selected failures:

| Outcome | Cases |
| --- | ---: |
| No clear endpoint anchor within configured bounds | 9 |
| Observation/coastline conflict | 7 |
| Disconnected anchors | 1 |
| Route geometry measured, not certified | 3 |

Discrete Hausdorff/Frechet errors of the three measured fragments: 11,996.57,
14,680.47 and 17,369.42 m. Both metrics happened to coincide in those cases.
Do not report 3 successful cases as 100% validation or treat unmeasured errors as
zero. Fragments are not complete voyages, and endpoint-shortest paths need not
reproduce operational loops. No acceptance threshold or operational confidence
was fabricated. Coverage failures alone prevent a global-realism claim.

These outcomes informed coastal refinement, so this date is development evidence.
Neither sample establishes worldwide/seasonal coverage.

## Frozen paired February AIS experiment

Official NOAA 2024-02-01 file: 202,314,194 bytes, SHA-256
`1c4b98f2a930cc428e2e7d35367285c19a1ca2d7ee171d5736e5a517272877ef`.
Both completed runs use identical source bytes, configuration, sampling, validator
and helper/dependency hashes; `compare_ais.py` verified those invariants and exact
selected track keys/source rows. Raw observations were not graph-building inputs.

7,349,856 rows, 954,092 cargo/tanker rows, 1,892 eligible vessels; 64 preselected
vessels, 30,215 observations, 42 eligible segments, 20 selected cases. One duplicate
observation removed; 264 segment boundaries recorded.

| Outcome | Coast 3 | Coast 4 |
| --- | ---: | ---: |
| Measured, not certified | 3 | 5 |
| No clear bounded endpoint anchor | 9 | 7 |
| Observation/coastline conflict | 7 | 7 |
| Disconnected anchors | 1 | 1 |

Paired cohorts: 3 measured in both, 2 newly measurable, 15 unmeasurable in both,
none lost. The common three cases have exactly unchanged discrete Hausdorff and
Frechet errors: 23,688.42, 14,833.74 and 16,815.29 metres. Newly measurable cases
have errors 18,200.90 and 81,507.64 metres (both metrics coincide for these cases).
Their observed/modelled lengths are 21,697.48/62,705.64 and
199,889.78/329,220.74 metres respectively. Measurability is not accuracy.

The refinement improves sampled measurement coverage from 3/20 to 5/20, not the
geometry of the common measured cohort. This does not justify a global-realism
claim, despite the substantially larger graph. No success-only aggregate or
invented operational acceptance threshold. Future tuning against these results
requires a new untouched evaluation split. See coastal-experiment.md.

Reports: `ais-holdout-old.json` (125.85 s), `ais-holdout-r4.json` (122.20 s),
`ais-paired-comparison.json`. These diagnostic runs shared the machine with the
long coastline audit: elapsed times are not controlled paired performance results.
Overall validation gate remains NOT PASSED; coastal access/corridor evidence is
still missing. Do not advance contractual optimization by treating unknowns as zero.

## Runtime and tests

Previous explicit real-artifact Java/MockMvc run passed with operator digest checks,
eight international source-derived pairs, three GBABA-USAA2 alternatives, absent
speed yielding null time and unknown permissions rejected when required. Java 17.0.15,
heap cap 1,258,291,200 bytes. After making the lower bound robust to accepted
rounding: graph load 5,810.02 ms; individual search 43.01-364.72 ms; three 50 km-
separated alternatives 568.25 ms; heap used after queries 637,009,920 bytes.
These are single-process research measurements, not peak RSS or a load/SLA test.

The latest full Java run passed: **67 tests, 0 failures/errors, 0 skipped**,
including explicit real-artifact integration and the API metadata/version checks.
Measured at 2026-09-09T03:13:49Z (2026-09-08 evening local time): old graph load
6,674.54 ms, eight searches 30.86-324.57 ms, three alternatives 501.81 ms, heap
used after queries 674,950,144 bytes under the same 1,258,291,200-byte cap.
The coastline audit was also running; do not interpret timing differences from
the earlier benchmark as a controlled performance comparison. Version-named report
is under `target/maritime-routing/` as described in runbook.md.

A regression covers
coincident H3 centres where GeographicLib returns zero and PROJ a positive floating
residual; no source coordinates/edges were altered. The recorded global admissible
distance lower-bound factor is 0.2747590247495699 on this snapshot; small-length
rounding can weaken the heuristic, but does not justify silently changing distances.
The full Java run was then repeated on the refined graph: **67 tests, zero failures,
errors or skips**, including the same eight source-port pairs and three alternatives
through the real loader/catalog/HTTP controller. Measured at
2026-09-09T03:19:36Z (2026-09-08 local date), with GIS jobs finished:

- Load 14,365.33 ms; eight queries 74.30-891.28 ms.
- Three 50 km-separated GBABA-USAA2 research alternatives in 1,185.39 ms;
  distances 8,004,678.56 / 8,009,072.00 / 8,145,140.40 m; no search-budget exhaustion.
- Heap used after queries 1,507,328,000 bytes; cap 1,887,436,800 bytes (1800 MiB).
- Explicit uncompressed entry limit 1,000,000,000 bytes; lower-bound factor
  0.23027137197822317. No source distances changed to improve the heuristic.
- Version-named benchmark:
  `target/maritime-routing/runtime-benchmark-2ff9db4a8ba536ce0a7e61b92012a61a8ab4deca555811d6ee1d2b95c0e3f096.json`.

These are single executions with different heap bounds and background conditions,
not a controlled load test or production memory guarantee. A bigger graph yields
shorter modelled paths in these eight cases, but does not repair the measured
coastal coverage/accuracy deficit. No Suez/Cape validation was inferred.

Python after resume: **19 tests passed**, including independent strong-connectivity
checks and all-clear-anchor selection. Conditional real-artifact test is normally
separate from the default Maven suite; the measured run explicitly enabled it.
Commands are in runbook.md.

No production deployment, MongoDB edits or Git commits. Later phases remain
unimplemented under the user's explicit phase-3 quality gate.
# Regional NOAA continuation, 2026-09-09

The user-approved New York pilot is implemented and tested; full source decisions,
hashes, limits and reproducible commands are in [noaa-pilot.md](noaa-pilot.md).
30 official layers / 2 cells produced a deterministic 2,644-node, 14,072-edge
physical mesh. The complete 7,036-reciprocal-pair geometry check passed, but all
19 source berth labels intersect official land and every edge has unresolved
chart regulatory restrictions. Zero eligible berth routes; global phase 3 remains
NOT_PASSED. No source coordinates moved, permissions inferred, or graph activated.

31 Python tests and the full 68-test Maven suite passed (zero failures/errors/skips),
including old global and regional actual-source integration. Java regional load:
1,018.42 ms; rejected routing and activation are expected negative evidence.
The global-old rerun loaded in 6,646.24 ms, eight routes 28.82-399.39 ms and three
alternatives 1,130.17 ms; prior metrics below are historical. The previous refined
global build was not repeated or modified. No phases 4-6 were started.
