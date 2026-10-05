# Active continuation checkpoint

## Phase 7 completed: integrated research plans

**PHASE7_INTEGRATED_RESEARCH_PLANS: PASS**, 16 closure checks and 89 Java tests,
zero failures/errors/skips. `/api/v2/maritime/plans` now joins actual server-side
weather routes, declared contractual leg mappings and phase-6 joint simulation.
Immutable revisions preserve inputs, versions, geometry, provenance, actor
exposure/control and explanations. Recalculation records the parent hash and
changed inputs without modifying the parent.

The real Atlantic integration computed two routes and three commercial
alternatives, then recalculated both routes around a previously used closed edge.
The 54 prior bound files, previous evidence, graph/forecast pins and all 16,330
regional restrictions are preserved. 409/422 failures create no partial plans.
Persistent closure: `data/maritime-routing/phase7-evidence-v1/status.json`.
See [results/reproduction](phase7-results.md), [API](phase7-api.md) and
[example](phase7-example.json). No active test/server processes remain.

Storage needs the explicit local `routing.maritime.plans.directory` property.
No deployment, database or configuration changes were made in this phase.
Declared probabilities remain uncalibrated; ocean-node mappings are not verified
terminal access. Owner authorization and public deployment are outside this
research closure. Earlier checkpoints below retain their historical scope.

## Phase 6 completed: declared joint scenario research

**PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH: PASS**, 17 closure checks. The user
explicitly selected declared research probabilities without empirical calibration.
Implemented `/api/v2/incoterms/simulations` and `/model`: joint finite tables,
canonical seeded common-row sampling, actor loss/cost and duration mean/VaR/CVaR,
simultaneous Monte Carlo error bounds, dependence reporting and explicit-objective
Pareto on the finite-distribution reference. No hidden weights or probabilities.

80 Java tests pass: 20 new and 60 regressions, zero failures/errors/skips. Example
HTTP replay is identical; maximum 200,000 draws / 16 alternatives / 256 rows tested.
All 41 prior bound files, the physical graph SHA and forecast manifest SHA match.
Persistent closure: `data/maritime-routing/phase6-evidence-v1/status.json`.
See [results/reproduction](phase6-results.md), [API and sources](phase6-api.md),
and [synthetic example](phase6-example.json). No active processes remain.

No empirical risk calibration, insurance recovery, route feasibility or real
commercial recommendation is claimed. No frozen phase-3/4/5 code, deployment
configuration, database or Git history changed. Next is phase 7: route/contract/
comparison provenance, explanations and recalculation integration. Earlier
checkpoints below preserve their historical scope.

## Phase 5 completed

**PHASE5_CONTRACTUAL_RESEARCH: PASS**, 12 executable checks. New isolated
`/api/v2/incoterms/rules` and `/api/v2/incoterms/evaluate` cover all eleven terms,
both FCA delivery variants, named delivery/destination events, actor-specific
decimal costs, cargo risk and independently declared routing control. Amounts
require provenance; missing amounts keep the actor total null. Conditional cargo
losses do not imply probabilities or insurance recovery.

60 Java tests pass: 28 contractual and 32 weather/physical-route regressions.
All 31 phase-3/4 bound implementation hashes, physical graph SHA and forecast
manifest SHA match. Persistent evidence: `data/maritime-routing/phase5-evidence-v1/`.
See [results and replay](phase5-results.md), [API](phase5-api.md), and
[primary sources](phase5-sources.md). No frozen code, deployment configuration,
database or Git history changed. No processes remain running.

Scope is ordinary performance with declared commercial inputs, not adjudication
or verified tariffs/policies. The legacy fixed-rate `/api/incoterms/calculate`
remains outside this evidence; consumers must migrate explicitly. Next is phase 6
scenario distributions/correlation, seeded simulation and Pareto analysis, with
no invented probabilities. Earlier checkpoints below are historical.

## Phase 4 completed: 2026-09-22

**PHASE4_VERSIONED_WEATHER_PARAMETRIC_RESEARCH: PASS**, 17 executable checks.
The user selected a parametric research vessel model. Original ECMWF IFS waves,
winds and surface currents for 2026-09-22 00 UTC through +168 h are preserved;
the new API uses a pinned forecast and time-expanded node/time labels, explicit
modelled waiting and bounded search. Three real-graph/forecast ocean scenarios
pass (Atlantic, Pacific/dateline and Indian). 33 Java and 3 Python tests pass.
All 16,330 restricted regional edges/connectors remain blocked; the frozen phase 3
implementation and physical artifact are unchanged.

Weather point coverage is 729/1,114 connected port references; 385 lack required
grid data and are not filled or treated as calm. This is not all-port route access.
No calibrated vessel ETA, fuel/emissions estimate or operational approval is claimed.
See [results](phase4-results-20260922.md), [API](phase4-api.md), and
[reproduction/configuration](phase4-runbook.md). Closure and source bindings:
`data/maritime-routing/phase4-evidence-v1-20260922/status.json`.
The next planned phase is the contractual/Incoterms model. Earlier checkpoints
below preserve their historical scope and dates.

## Revised phase 3 completed: 2026-09-22

**PHASE3_GLOBAL_RESEARCH_V2: PASS.** See [combined research results](phase3-research-results-20260921.md).
Implemented mandatory source-coverage regional controls, coastal transition mesh,
schema-2 evidence loading, matching regional validation and per-route coverage.
Final artifact: `global-regional-research-v4-20260921.zip`, SHA-256
`1eed131c9bb2cb27596ac1aa451ffca555c4ca9ab448599297c8bd0226f7967c`.
102,604 nodes / 2,356,006 directed edges; 1,114 connected references unchanged;
2,925 regional nodes physically joined. All pending New York restrictions remain
blocked; the 19 regional berth references remain unavailable.

All 1,178,003 reciprocal pairs accounted for, regional/transition geometry checked,
unchanged global geometry bound to its original full-coast evidence. Nine registered
world cases pass, with actual dateline/equator and three-basin coverage, alternatives
and HTTP constraints. 117 Python tests and 25 Java tests pass; final Maven exit 0.
The 18-file engineering registration and inputs match. All four original AIS
registrations' implementations and protocols remain unchanged.

Machine-readable closure: `data/maritime-routing/phase3-research-status-v2-20260922.json`,
14 checks PASS. Two post-report mutation checks reject missing regional approval or
a wrong protocol digest. Historical `phase3-status-20260921.json` stays NOT_PASSED
under the former scope. No worldwide operational/AIS-accuracy claim, activation,
deployment, database or Git-history change. No continuation process remains running.
The next planned stage is phase 4 weather/vessel inputs; it is separate from this
completed research geometry phase. Earlier checkpoints below are historical.

## Scope update requested by the user: 2026-09-21

Current plan: [worldwide research planning with New York controls](phase3-research-plan-20260921.md),
`PHASE3_GLOBAL_RESEARCH_V2`, **IN_PROGRESS**. After reviewing Chen et al., the user
authorized worldwide geometric research planning while retaining regional controls.
The global mesh and regional experiments are reusable baselines; their integration
is still pending. Next: versioned regional-priority policy, a combined artifact with
no global bypass, route coverage metadata, and registered real-artifact checks.
Full worldwide chart/permission/AIS coverage is outside this revised closure gate;
wave/vessel integration belongs to phase 4. No DDQN retraining is required for phase 3.

This update changes the plan and living documentation only. The original
`phase3-status-20260921.json` remains NOT_PASSED; all frozen protocols, datasets,
results and implementations retain their prior meaning. The checkpoints below
describe the previous scope and chronology, not additional current blockers.

## Resumed at user request: 2026-09-21

User requested continuing phase 3 to completion by all necessary means. Implemented
a separately versioned coherent O/D preference model, six-date extraction, five
vessel-group development checks, registration/acquisition/evaluation for untouched
September/October, paired audit and source-row diagnosis. No registered prior file,
Java/configuration, graph topology, production activation, DB or Git history change.

**Regional temporal acceptance PASS on both dates; global phase 3 NOT_PASSED.**
September 19/20 measured, maximum Frechet 769.60 m; October 13/15, 883.36 m. Neither
date has a measured spatial failure or predicted chart-water conflict. Original
>=10 / >=80% / <=1 km criteria unchanged; all three unmeasurable observations remain
in denominators. Baselines fail with 11/3 spatial failures. Complete original censuses
are retained. No tuning between dates. Temporal data may contain previously seen
vessels and are not an unseen-vessel test.

Training: 191 original March–August segments / 145 vessel groups, 160 usable tracks,
26 outside cohort, five chart conflicts. Grouped development: 120 selected / 116
measured; failures 70 -> 3. April/May/June grouped results still FAIL; historical
failed holdouts are preserved, never renamed successes.

Registration `od-holdout-registration-20260921.json`, SHA
eacf4ce0a790dfb71dd55826db444ab0f9e9ee6c7270196b6a434842f412cb50;
created 2026-09-21T15:54:50.089402+00:00 before either acquisition. Do not modify its
27 bound implementation files or protocol in place. Replay bundle SHA
fc8505e8256d2623dbe078adbc763675332804e67ab86555d964395234fcb7d3.
All four experiment registrations' implementation hashes match. Full Python suite
110 tests passes; git diff --check passes. Java unchanged, not rerun.

New official UKHO snapshot: 217 original features (21 points, 39 lines, 157 areas),
OGL terms and explicit not-for-navigation suitability limitation preserved. Separate
research audit of global mesh: 51/60 lanes contain no node, 57/60 no fully contained
reciprocal pair; Dover 9/12 and 11/12. This is geometry coverage, not compliance or a
complete expected-passage test. No UKHO geometry imported or legal clearance inferred.
Public MCA/MPA/Suez indexes/guidance also snapshotted, not asserted as chart coverage.

See [od-results-20260921.md](od-results-20260921.md) for all results, source chronology,
hashes and limitations. `phase3-status-20260921.json` recomputes both-date regional
acceptance and preserves the unresolved global evidence requirements. Next work is
source-bound passage refinement/expected-passage tests and wider regional validation,
not repeated New York tuning or claiming global success from the regional pass.
All 35 holdout source tracks were independently reread for chart diagnosis. The
three unmeasurable cases retain source-water/coverage conflicts. No jobs from this
continuation remain running. Post-evaluation helpers/tests were archived separately;
all four frozen protocols as well as their bound implementation hashes still match.

## Resumed at user request: 2026-09-16

User requested continuing the plan to complete phase 3. Added a separate transit
evaluator, registration, bounded acquirer, paired census audit and cost attribution.
March model and all previous graph/holdout implementations remain unchanged. Full
Python suite: 101 tests pass. No Java/production configuration, activation, DB or
Git changes. Java was not rerun in this continuation.

July 1 and August 1, 2024 were registered before acquisition using the previously
declared displacement-dominated transit cohort and unchanged 1 km error target.
Registration: `transit-holdout-registration-20260916.json`, SHA
750a3da6c85cc97943edd3c0f3ecd263bf4bf15bcc2c423b2b97b344cf4c8876;
created 2026-09-17T00:56:45.678908+00:00 (September 16 local).
Replay bundle: `transit-frozen-implementation-20260916.zip`, SHA
7ef607a53d67d0f8e9110166974af46ec1469f0a8b813a6a61d421ad7bc46db5.
Do not modify these 22 registered implementation files or the new protocol in
place. New comparison/reporting code is post-evaluation only, not model tuning.

Both sources acquired with registration-bound receipts: July 263,429,168 bytes,
SHA 44d67c37f2b35f8e4cb33e802c0285646710edf6bd3f0e31720d69ab10d44c3b;
August 302,467,362 bytes, SHA
d745cfaf6a1da4ed7eb62308f1ecbff157652dbfa1241e4651b63b41023b2452.
All four sequential runs finished, without intervening tuning. July 20/20 measured,
spatial failures 11 -> 1, median Frechet 1,031.32 -> 346.64 m; August 18/20 measured,
failures 13 -> 1, median 1,154.75 -> 330.04 m. Directed maxima 1,213.62 / 1,129.17 m:
both dates still FAIL the unchanged <=1 km criterion. Full candidate censuses:
July 30 original / 26 eligible / 4 outside / 20 selected; August 44 / 39 / 5 / 20.
No original cases, failed observations or prior reports were removed.

Source-row geometry diagnosis and reconstructed preference costs completed. The
remaining measured failures are displacement-dominated transits in chart water,
not near-returning curves. Directed AIS prefers lower optimization cost but worse
observed shape; post-hoc switching to the baseline for those cases is not allowed.
Two August observations leave positive known Group-1 water; no exclusion feature
hits or inferred permission. Full results and next research requirements:
[transit-results-20260916.md](transit-results-20260916.md).

No jobs remain running. Frozen implementation/protocol hashes still match. Global
phase-3 gate remains NOT_PASSED; authoritative global access/channel/expected-passage
evidence is still absent. New model development needs its own declared training set,
grouped development checks, immutable version and untouched prospective dates.
July/August must not be called independent again after they inform later changes.

## Resumed at user request: 2026-09-15

User said "Ok, sigue con el orden propuesto para completar la fase 3". Phase 3 is
still NOT_PASSED. No production configuration, activation, database or Git-history
changes. April's eleven frozen implementation files and protocol are unchanged.

Implemented a separate directed AIS research preference model from original
March consecutive observations: 874 directional pairs, 23/26 eligible tracks;
three chart-conflicting training tracks remain accounted for. Physical graph,
coordinates, restrictions and unknown clearance are unchanged. Model SHA
69a8dff41b46166cf416a7e44434b86cd81da1b331eaa93876dc566e198207a0.
March resubstitution has 17/20 measurable and maximum Frechet 678.79 m; this is
training evidence, NOT independent validation.

Both May 1 and June 1, 2024 holdouts were registered before acquisition, with exact
model, protocol, 17 implementation files and environment. Registration SHA
e12cf33382e8c467e2037119421b0e9d3cefb8dc0c7d6886b145cdb7e6f8f091;
replay bundle corridor-frozen-implementation-20260915.zip SHA
eb2a9c30863c34e68cc3e3468ab4657de567b2dd80ab14869241487d26b0f5c4.
DO NOT MODIFY those 17 files or corridor-holdout-protocol-20260915.md in place.

All four sequential evaluations have finished, without tuning between them:
May 18/20 measurable, spatial failures 11 -> 2, median Frechet 1,042.90 -> 434.44 m;
June 19/20 measurable, failures 12 -> 2, median 1,043.36 -> 455.99 m. Both directed
models still FAIL the original 1 km target; maxima 3,438.28 / 6,830.49 m. The worst
case on each date travels kilometres and returns near its starting coordinate;
an endpoint-only positive-cost optimum does not explain that excursion. Do not
remove those cases, change thresholds, label them erroneous AIS, or infer intent.

83 Python tests and 56 targeted Java tests pass. Post-evaluation source/geometry
diagnosis and Java integration verification completed. Java test-only changes add V2 negative evidence,
actual global dateline/equator checks and dated output directories; runtime code
is unchanged. Eight global source-reference pairs succeed, four cross the dateline
and four the equator; three alternatives and HTTP constraint checks pass. V2 retains
all unresolved restrictions, all 19 unavailable berth references and zero returned
routes/catalog activation. Reports are in target/maritime-routing/20260915; old
benchmarks are preserved. Full results: [corridor-results-20260915.md](corridor-results-20260915.md).
Public source discovery: approved
DMA AIS index request timed out during TLS; LINZ ENC download page returned 200
but its data require S-63 permits/registration. No account or licence action taken.

User subsequently confirmed: "Recomendar rutas de tránsito entre origen y destino".
The next experiment will prospectively define its transit cohort; previous failed
evaluations stay immutable. No retroactive success claim or relaxed spatial target.
A separate V1 Java regression also passed with its default profile preserved.
Implemented `transit_cohort.py` with explicit displacement-dominated selection and
complete census of selected/unselected/out-of-scope original segments, independent
of chart/routing/metric outcomes. Full Python suite now 88 tests passes. See
[transit-next-experiment.md](transit-next-experiment.md): cohort implementation is
ready, but a new evaluator/registration/untouched-date evaluation is NOT yet done.
The original 1 km spatial target stays unchanged; this population is not all global
shipping or verified port-to-port voyages. No background jobs remain running.

## Resumed at user request: 2026-09-14

User explicitly said "Bien, continua con el plan". The earlier pause is superseded.
Phase 3 remains NOT_PASSED. This continuation corrects the regional physical model,
preserves operational restrictions and performs a prospectively frozen April AIS
comparison. No Java, production configuration, DB, activation or Git history changes.

Current artifacts: NOAA source registry V2 (40 layers), corrected Group-1 water and
overhead bridge constraints, 2,951 nodes / 16,178 directed edges, 8,089 reciprocal
pairs passed at 12.5 m. Two independent builds have identical ZIP SHA
00558726e21cc9dcf45bca2df3088088bcb5f9ab2dc54c815945731b4dcff3eb.
All 19 terminal reference points remain unavailable and all edges retain unresolved
restrictions. The semantic loader rejects the new profile; Java catalog requires
the still-absent portCountryQa. This does not activate regional routing.

March development: same 20 selected cases and source rows; 17 measurable versus
zero on V1. Distance-only has eight >1 km cases; channel-first and balanced variants
have two each. Balanced median Frechet 339.75 m versus 994.74 m, maximum 1,173.90 m
versus 1,255.00 m. All March variants FAIL the unchanged regional target.

April protocol and code/config/data/environment registration frozen before source
acquisition; do not edit registered implementation or protocol while evaluating.
Registration: april-holdout-registration-20260914.json, SHA
745370b059c5db3689c03c38783a66d00e49a77f79253fb3f66c0671c56c2de3,
2026-09-15T02:05:29.396270+00:00 (September 14 local).
AIS April 1: 232,734,541 bytes, SHA
48b48ffa83dbcd9b609b733ea558d75a8da6c3cc81f7dfddd550e312939b262b.
Both April evaluations completed: 18/20 measured. Distance-only maximum/median
Frechet 2,409.63/1,131.83 m; balanced 1,217.15/385.91 m. Eleven spatial failures
become two, but BOTH models FAIL the unchanged <=1 km target. Paired 18 measurable
cases: 13 improved, 3 regressed, 2 unchanged. Two unmeasurable tracks intersect
charted obstruction 156:141425 and shoreline construction 138:51936 in US5NYCCF.
No cases removed and no April-based model tuning or outcome-dependent switching.

70 Python tests pass; archived V1 geometry replay passes all 7,036 reciprocal
pairs; V2 passes 8,089 pairs. git diff --check passes. All jobs in this continuation
have finished. Exact frozen implementation bundled locally as
april-frozen-implementation-20260914.zip (SHA
3c54f01f0e3480be4a6fd6c7fb903042f14e2001a25f1ab9ef28732a6e72ed1b).
Full results, hashes and limitations: [regional-v2-results-20260914.md](regional-v2-results-20260914.md).
The correction/comparison work is complete, NOT the phase-3 acceptance gate.
Do not edit the frozen April implementation/protocol in place without preserving
its replay bundle; further model changes require a new registered experiment and
untouched holdout. Wider global approach/channel evidence remains missing.

## Historical pause at user request: 2026-09-14 (superseded above)

User requested continuing phase 3 until successful, then explicitly paused work.
At that checkpoint phase 3 remained NOT_PASSED and no jobs remained running.
No commits, deployments, activation or DB writes had occurred.

New implemented scripts: phase3_sources.py, audit_external_access.py,
acquire_regional_ais.py, validate_regional_ais.py, diagnose_regional_ais.py;
regional-ais-config.json and tests/test_phase3_continuation.py. acquire.py now
checks Zstandard magic for the registered March holdout too. All work is local.

Public snapshots acquired under ignored data/maritime-routing:
- usace-docks-20260914: 58 original facility references; source ZIP SHA
  73254fabb2793b6b20072e957e0ff04a0426f46cac6c8a786cc11e42fd06dff4.
- usace-waterways-20260914: 16 network references; source ZIP SHA
  760d425fc3351c87be61d4f34b685f5342c0230db7f956bb0352ff8488337a27.
- navcen-safety-20260914: two downloaded safety-zone files, one published line-file
  URL HTTP 404; incomplete coverage. Source ZIP SHA
  49ef29004883fae17562fc99b3f0688956bd096dc438ed5aad4a0a37ab4a5f79.
- external-access-audit-20260914.json: 11 original USACE reference points have
  clear mesh connectors; 47 outside conservative water. NONE is an approved
  terminal approach. Several are statistical/anchorage references; detailed dock
  records often cite 1998 surveys. Three of 16 full network links clear the chart
  domain; no geometry imported. Proximity comparisons are only a review queue.

Frozen experiment protocol: phase3-continuation-20260914.md. Preserve its original
contents; it was written before acquisition/outcome inspection. Regional physical
targets were >=10 cases, >=80% measurable and every measured spatial error <=1 km.
- New NOAA AIS 2024-03-01: 195,962,525 bytes, SHA
  3f3bc2c030af78e27b883a001bdec96f647361528f4a46d66fb04f2ef10acfaa,
  under ais-march-holdout-20260914. 7,238,053 rows; 28 regional merchant vessels;
  26 eligible segments; 20 fixed selected cases.
- regional-ais-march-20260914.json: all 20 cases OBSERVATION_CHART_WATER_CONFLICT,
  zero measured. Regional physical acceptance FAIL, global gate NOT_PASSED.
  59.42 s, process peak working set 167,407,616 bytes. Report SHA
  d9499ec7ffbfb13bf5d499cd8715dbc88307d47b64095377afcd8220a3174b41.
- regional-ais-march-causes-20260914.json: 19 tracks leave positive known DEPARE,
  14 intersect source exclusions, one intersects source land. Reasons overlap;
  none leaves declared chart coverage. This does not identify confirmed causal
  truth, and March is now development evidence for any subsequent tuning.
- Physical diagnostic intentionally does not evaluate legal restrictions and
  reports PASSAGE_NOT_EVALUATED. It is separate from all semantic/Java routing;
  no operational routes or terminal endpoints were created.

Verification: full Python suite 48 tests passed; after adding a synthetic complete
metric/search test, targeted new suite 10 tests passed. Full suite has NOT been
rerun after that addition or the last survey-index probe edit. No Maven rerun was
needed/performed because Java was unchanged. git diff --check passed before those
last additions. Synthetic successes are not actual maritime validation.

Latest source check: eHydro RecentSurveyBins FeatureServer HTTP 403 in web reader
and approved CLI downloader. Read-only probe: phase3_sources.py survey-index
--inspect. USACE discovery page also returned CLI 403; its official ArcGIS links
were verified in web reader and their data/terms downloaded successfully.
NOAA BlueTopo was investigated as a possible further research source; NOAA describes
it as not-for-navigation and including unqualified bathymetry. No download/import.

On explicit resume: review per-feature depth/exclusion causes before choosing a
corrective experiment; investigate authoritative depth/access evidence and scoped
restrictions. Do not relax frozen acceptance targets, promote statistical dock
references, or call a regional result global success. Finish documentation updates
(evidence.md/data-gates.md still have historical sections), run the final Python
suite after any edits, and preserve the existing immutable reports.

## Latest: semantic pilot implementation (2026-09-09)

User approved semantic resolution and then asked to continue. Implemented isolated
offline semantic_pilot.py, review_noaa_165169.py, acquire_noaa_semantics.py,
suggest_noaa_approaches.py and verify_noaa_candidates.py. Full contracts, evidence,
hashes and commands: semantic-pilot.md. No global graph or production API changes.

- Current official NOAA Coast Pilot PDF, printed 06 SEP 2026, acquired and verified
  locally. SHA 4670561a2662ca82a9374f08e86a028ae5016150d39aab3bb0a010b977b9a2c8.
- 14 restriction features now modeled via the source-backed COTP authorization
  branch, four remain unresolved. This does not assert active permission or resolve
  live geographic applicability. Authorization/instruction evidence absent.
- Separate immutable terminal references and manually curated water approaches;
  required coordinate AND association evidence, source/hash/time/vessel/voyage
  checks, bounded offline scenario search and all five classifications implemented.
- Semantic artifact noaa-semantic-pilot-v2.zip, version
  c3d4e7793a587f97e4b52e88da3dbbe367618bbb7d626572c57b32d0232bc3b9;
  ZIP SHA 06414d616a50738497331bd0cad4de44267594aff53290045c349d7e1f3d50cb.
  Repeated build reproduced identical bytes. 7,036 base reciprocal pairs rechecked.
- 90 source-selected terminal requests rejected early for absent approaches;
  zero real routes or activated endpoints. Do not report those as graph searches.
- 57 exact-source vertex candidates, three per terminal; 535 clear water-to-mesh
  connectors independently rechecked at 6.25 m steps, sharing the chart mask.
  Candidate v2 report and .validation.json passed coordinate/geometry checks;
  terminal association remains NOT_VERIFIED. No automatic nearest-water relocation.
- 39 Python tests pass, including synthetic positive routes and fail-closed cases.
  Full Maven rerun: 68 tests, zero failures/errors/skips, exit 0. Latest baseline
  runtime report files now contain the rerun at about 21:03 UTC (old global load
  16,754.46 ms; baseline NOAA load 905.11 ms); older metrics below are historical.
- No jobs running, commits, deployments, activation or DB writes at this checkpoint.

Next actual data gate: evidence-backed manual terminal/access association and scoped
passage evidence. Do not silently turn candidates into curated endpoints, assume a
permit, or claim that a declaration/document hash authenticates legal permission.
The frozen review context is an availability audit, not a departure date. Review
window defaults to 24 h, an engineering freshness policy, NOT legal validity.
Phases 4-6 remain gated; do not repeat the global mesh build or ask again whether
the user approves a regional/semantic pilot. They already approved both.

## Prior regional checkpoint (historical metrics below)

Latest continuation: 2026-09-09. Regional NOAA pilot approved by the user and
implemented; see noaa-pilot.md for the latest evidence, not a pending scope question.
No commits, deployment, graph activation or DB changes.
No running jobs at this checkpoint. Full Maven suite including old global and NOAA
real-artifact tests: 68 tests, zero failures/errors/skips; build/test exit 0.
Regional source acquisition, deterministic repeat build and full edge check finished.
Older checkpoint.md and pause-20260908.md are historical. Do not restart completed
builds or overwrite immutable reports. No subagents. Protect secrets: never print
application.properties, full Spring test logs, Maven settings or JVM crash dumps.

## Completed in this continuation

- Refined H3 2/4/5 full-GSHHG build: 98,811 nodes, 2,335,476 directed edges,
  1,529 strong components, largest 96,401 nodes; build 1,906.27 seconds.
- Country audit: 1,114 connected UN/LOCODE references (old 1,113);
  105 initially connected references quarantined, no coordinate corrections.
- Refined audited artifact: data/maritime-routing/global-coastal-r4-audited-v1.zip.
  Version 2ff9db4a8ba536ce0a7e61b92012a61a8ab4deca555811d6ee1d2b95c0e3f096.
  SHA bcc127e48f59d6ff6b6081e8e94de8cff9bdc704873f98dc93be0876aa32a04a.
  ZIP 59,792,089 bytes; uncompressed edges.jsonl 845,769,688 bytes.
- Full geometry report global-coastal-r4-audited-v1.validation.json PASSED:
  1,167,738 reciprocal pairs, no land conflicts or distance mismatches, complete
  reverse-geometry checks, 1,525.64 seconds. Source-lineage limitations still apply.
- New validate_connectivity.py and four synthetic tests independently verify strong
  directed connectivity, endpoint/port references, manifest counts and restrictions.
  Both artifacts passed. Refined: all 1,114 covered references mutually reachable,
  1,239,882 ordered distinct pairs, 37.32 seconds. Old: 1,113, 1,237,656, 8.19 seconds.
  Reports alongside each artifact, suffix .connectivity.json. This proves path
  existence, not a million benchmark queries or operational navigation.
- Python suite: 19 tests passed.
- Full Java suite with old real artifact: 67 tests, zero failures/errors/skips.
- Full Java suite with refined real artifact: 67 tests, zero failures/errors/skips.
  Both exercise actual loader/catalog/HTTP routes and 8 source-selected global pairs.

## Frozen paired AIS experiment: completed, not a future untouched sample

NOAA February 1, 2024 raw file, local ignored snapshot-20260908:
202,314,194 bytes, SHA
1c4b98f2a930cc428e2e7d35367285c19a1ca2d7ee171d5736e5a517272877ef.
7,349,856 rows; 1,892 eligible cargo/tanker vessels; 64 preselected vessels;
30,215 observations; 42 eligible segments; 20 selected cases.

Same validator/config/helpers/dependencies, source rows and case hashes:
- old: 3 measured, 9 no anchor, 7 observation/coast conflicts, 1 disconnected;
- refined: 5 measured, 7 no anchor, 7 observation/coast conflicts, 1 disconnected;
- 3 common measured cases with unchanged Hausdorff/Frechet errors;
- 2 newly measurable cases, errors 18.20 km and 81.51 km; not proof of accuracy.
- 15 unmeasurable cases remain in the denominator; no invented pass threshold.

Reports: ais-holdout-old.json (125.85 s), ais-holdout-r4.json (122.20 s),
ais-paired-comparison.json. AIS jobs overlapped the coastline audit after verifying
available physical memory; timings are not a controlled paired performance benchmark.
January 1 results were development evidence for this experiment, not its holdout.
Further tuning informed by February results requires a NEW untouched evaluation split.
Do not describe either date as globally representative.

## Runtime evidence and resource bounds

Reports under target/maritime-routing/runtime-benchmark-<graphVersion>.json.
Default runtime-benchmark.json is the most recent refined run, not the old result.

Old: load 6,674.54 ms; eight routes 30.86-324.57 ms; three alternatives 501.81 ms;
heap used after queries 674,950,144 bytes, heap cap 1,258,291,200.
Refined: load 14,365.33 ms; eight routes 74.30-891.28 ms;
three alternatives 1,185.39 ms; heap used 1,507,328,000 bytes, cap 1,887,436,800.
Not peak RSS, throughput, p95, SLA or a controlled speed comparison.
Source-selected eight pairs happened to be identical in both runs.

Bounded launcher: MAVEN_OPTS=-Xmx256m.
Old fork: -DargLine=-Xmx1200m.
Refined fork: -DargLine=-Xmx1800m, -Dmaritime.max-entry-bytes=1000000000.
Both full Maven commands are in runbook.md; approved network runs succeeded.
Offline Maven lacked the parent POM under the required repository identity and failed;
that attempt was not counted as a test result.
Avoid large concurrent GIS/Java jobs. Memory was measured via
Microsoft.VisualBasic.Devices.ComputerInfo (total physical 16,849,956,864 bytes);
free memory is dynamic, not a future sizing guarantee.

## Remaining gate and approved regional pilot

Overall phase 3 is NOT PASSED. Phases 4-6 remain unimplemented under the user's
explicit gate. Missing authoritative approaches/channels/constraints/depth and wider
independent validation cannot be filled with guessed values or GIS-only pass labels.
Global graph is research-only; coastal refinement alone did not solve the deficit.

User has no external licensed datasets. Do not ask for the same unavailable files again
or assert all useful data are paid. NOAA ENC/ENC Direct is an official regional option
(rechecked during this continuation); GIS conversion is not certified for navigation.
The user approved the US pilot. Do not ask for that decision again.
NOAA New York acquisition/build/validation completed: 30 source layers, 2 charts,
2,644 physical nodes / 14,072 directed edges, deterministic repeat ZIP hash.
All 19 official berth references overlap official land; none was relocated.
All edges carry unresolved regulatory restrictions and remain hard-blocked by Java.
7,036 reciprocal pairs passed the chart geometry recheck; zero eligible berth routes.
This is negative regional evidence, NOT completion of global validation.
Source: data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json.
Artifact/report: noaa-new-york-pilot-v1.zip and .validation.json in that data root.
NoaaPilotArtifactIntegrationTest verifies real-reader compatibility, hard search
rejection and no GraphCatalog activation. No production Java behavior was changed.
Python suite now 31 tests. Commands/evidence and hashes: noaa-pilot.md.
Regional Java load 1,018.42 ms; no eligible routes or activation. Latest global-old
rerun replaces its benchmark JSON: load 6,646.24 ms, eight routes 28.82-399.39 ms,
three alternatives 1,130.17 ms. Earlier timings above remain historical.
Next data gap: authoritative water-side access points with terminal associations
and applicable dynamic restrictions; do not guess nearest-water associations.
Selected NGA/WPI endpoints previously failed HTTP403/TLS after approved access;
no TLS bypass or system trust-store edits.

## Handoff requirements

Evidence, licensing/source choices, reproducible commands and endpoint contracts:
evidence.md, architecture.md, runbook.md, api.md, coastal-experiment.md, data-gates.md.
Large sources/artifacts are ignored, not Git additions. Source receipts remain local.
Legacy unsourced calculation endpoints deliberately return 503; historical reads
remain. Frontend needs the documented v2 migration; no automatic Mongo-ID mapping.
No production activation or public deployment performed. Do not claim world novelty.
