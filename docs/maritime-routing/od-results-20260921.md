# Coherent origin/destination model: regional acceptance passed, global phase 3 open

The new regional model passes **both prospectively registered temporal dates** at
the unchanged 1 km spatial target. This closes the latest New York regional transit
experiment. It does **not** close global phase 3: the independent UKHO source audit
now quantifies missing representation of published traffic lanes, and wider access,
constraint and independent-trajectory evidence remains absent.

No Java/runtime configuration, graph topology, production activation, database,
deployment or Git history change was made in this continuation. Existing worktree
changes from previous sessions are preserved. Full Python suite: **110 tests pass**;
`git diff --check` passes. Java was unchanged and was not rerun. All four previous
registrations (11/17/22/27 files) still match their implementations.

## Model and grouped development

[Development declaration](od-development-20260921.md) preceded extraction/fitting.
March–August 1, 2024 are explicitly development data for this new model, including
the previously failed holdouts; those historical reports are unchanged.

Six raw source scans retained 191 original eligible segments from 145 vessel
groups: 160 transit-eligible chart-clear tracks used, 26 outside the fixed transit
cohort, five chart conflicts retained but unused for preference fitting. The model
keeps original rows/times/coordinates and deterministic vessel-group identities.

The predictor chooses one coherent forward subtrack using only query endpoints.
It retains the prior directional evidence tolerance and physical search, restricting
support to that subtrack's consecutive observation pairs. It never uses intermediate
query observations to choose a prediction. Original chart geometry, unknown depth,
legal restrictions and operational routing gates are unchanged. No matching template
means the distance optimum, according to a rule declared before evaluation.

Five SHA-256 vessel groups exclude every date of a held-out vessel from prediction
support. Across six dates: 120 selected / 116 measurable, distance-only spatial
failures **70 -> 3**. These are grouped development diagnostics, not temporal
holdout results. All dates and failures remain visible:

| Development date | Measured | Distance failures | O/D failures | O/D max Frechet | Regional criterion |
| --- | ---: | ---: | ---: | ---: | --- |
| March | 19/20 | 9 | 0 | 780.34 m | PASS |
| April | 20/20 | 14 | 1 | 2,409.63 m | FAIL |
| May | 19/20 | 10 | 1 | 1,035.20 m | FAIL |
| June | 20/20 | 13 | 1 | 1,062.71 m | FAIL |
| July | 20/20 | 11 | 0 | 831.92 m | PASS |
| August | 18/20 | 13 | 0 | 960.18 m | PASS |

March–June case populations here use the already declared transit cohort, so they
are not paired comparisons to the older all-segment experiments. July/August are
not independent again. The three failed development months are not erased by the
new temporal result. No additional configuration was selected after these results.

## Prospective registration and acquisition

[Protocol](od-holdout-protocol-20260921.md) and
[registration](od-holdout-registration-20260921.json) bind the model, graph, chart,
criteria, 27 implementation files and environment before either new source.
Registered at **2026-09-21T15:54:50.089402+00:00**. The original code/protocol bundle
is `od-frozen-implementation-20260921.zip`. Do not edit those registered files or
the protocol in place. Reporting helpers are separate post-evaluation code.

| NOAA source | Download started UTC | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| September 1, 2024 | 2026-09-21T15:56:43.899560+00:00 | 282,418,723 | `1943877050f2f1bc470a4c41b1c1eeff3164f6951c461b60484e9f32a381ff06` |
| October 1, 2024 | 2026-09-21T15:59:00.826458+00:00 | 249,912,729 | `9fddcb2f5041775363db477831be524c83d2e1321fe6d016a5c1b82a710dc0a6` |

Receipts bind registration/acquirer hashes. Initial sandbox network denials were
retried with approval, not treated as public-source unavailability. No model tuning
occurred between the four evaluations. The local registration is not a third-party
timestamp attestation; the untouched-file audit covers the declared data root.

## Independent temporal results

Per date/objective: >=10 selected, >=80% measurable, all measured Hausdorff AND
Frechet <=1,000 m, zero predicted chart-water conflicts. Each result is computed
from the full selected cohort. No averaging hides an individual failure.

| Date / objective | Measured | Over 1 km | Median Frechet | Max Frechet | Max Hausdorff | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| September / distance | 19/20 | 11 | 1,010.19 m | 2,127.86 m | 2,127.86 m | FAIL |
| September / coherent O/D | 19/20 | 0 | 241.81 m | 769.60 m | 769.38 m | **PASS** |
| October / distance | 13/15 | 3 | 544.06 m | 1,440.40 m | 1,440.40 m | FAIL |
| October / coherent O/D | 13/15 | 0 | 326.01 m | 883.36 m | 875.71 m | **PASS** |

September census: 9,566,456 CSV rows; 32 regional merchant vessels; 36 original
segments / 32 transit-eligible / four outside cohort / 20 selected / 12 eligible
but hash-unselected. October: 8,570,408 rows; 17 vessels; 21 original segments /
15 transit-eligible / six outside cohort / all 15 eligible selected. No padded or
replacement cases. The one September and two October chart-conflicting observations
remain in the denominators. All 32 measured predictions used a coherent template;
the predeclared no-match rule was not invoked for these measured cases.

Paired reporting recomputes acceptance, checks complete censuses and original
selected rows/endpoints/times, and checks frozen source/model/code/environment
bindings. Development reporting also checks that selected training tracks belong
to neither the evaluation vessel nor its excluded fold.

Whole evaluation process: September 120.10 s / 212,619,264 B peak working set;
October 76.41 s / 209,293,312 B. These include import, raw-data scans, both objectives
and metrics. September overlapped source-download/geometry-audit work. These are
not controlled serving-latency, throughput, p95 or Java memory measurements.

The separate source-row diagnoses reread original observations and validate case
identity/endpoints/timestamps before chart attribution. Their derived views bind
the original O/D report hash; they do not modify cases or acceptance. Historical
AIS/chart disagreement is not proof of invalid AIS or permission to ignore a chart.

September's unmeasurable track leaves positive Group-1 water and intersects source
features `noaa-enc:138:US5NYCCF.000:51961` and
`noaa-enc:230:US5NYCCF.000:21390`. Both October failures leave positive Group-1
water; one also leaves the declared chart coverage. No October source-exclusion
hits were attributed. All 35 selected tracks were reread and verified; neither
diagnosis found an endpoint-excursion objective lower-bound incompatibility.

## New independent passage evidence and remaining global gap

The official [UKHO marine-data catalogue](https://www.admiralty.co.uk/access-data/marine-data)
led to its public [Ships Routeing Measures service](https://datahub.admiralty.co.uk/server/rest/services/Hosted/Ships_Routeing_Measures/FeatureServer).
Pinned item `fbf168cd00374802be438255513134a4`, owner `UKHydrographicOffice`;
source/service identity, all feature IDs, batched membership and raw responses
were checked. Snapshot: 21 points, 39 lines, 157 areas. All 217 original geometries
are valid. Duplicated geometric shapes with distinct source IDs remain distinct.

The metadata provides OGL v3 terms and explicitly says these data are unsuitable
for marine navigation or creation of navigational products. Attribution: contains
public sector information from the UK Hydrographic Office under OGL v3. The data
were used only for an isolated research coverage audit; no graph import occurred.

Audit of the existing 98,811-node / 2,335,476-edge global research mesh checked
content-addressed graph identity, table hashes and its full reciprocal validation.
Geodesics in the relevant region were sampled at 500 m against original polygons:

| Published lane population | Lanes | No mesh node | No fully contained reciprocal mesh pair |
| --- | ---: | ---: | ---: |
| All UKHO traffic-separation lanes | 60 | 51 | 57 |
| Source-labelled Dover lanes | 12 | 9 | 11 |

This quantifies insufficient lane representation. It is not itself a navigational
compliance test: intersections, points or a contained edge do not establish a
complete direction-compliant route, applicable permission, depth or expected-passage
test. Conversely, a missing contained edge is not a declaration that ships cannot
traverse the area. Nothing was snapped to a lane or promoted into the graph.

Original public index/guidance responses were also preserved for
[MCA Dover guidance](https://www.gov.uk/government/publications/dover-strait-crossings-channel-navigation-information-service/dover-strait-crossings-channel-navigation-information-service-cnis),
[MPA Singapore publications](https://www.mpa.gov.sg/who-we-are/newsroom-resources/publications/singapore-port-information),
and [Suez navigation rules](https://www.suezcanal.gov.eg/English/Navigation/Pages/RulesOfNavigation.aspx).
These are useful authority references, not imported channel geometry or complete
current clearance evidence. No account, payment or third-party contact occurred.

## Reproducible artifacts and what remains

Files below are under ignored `data/maritime-routing/` unless otherwise stated.

| Artifact | SHA-256 |
| --- | --- |
| od-coherent-model-v1-20260921.json | `bb8644cdc676f3f9004233140c8f30f66aae6e603fd3fccfc6ae856730a0ff4a` |
| docs registration | `eacf4ce0a790dfb71dd55826db444ab0f9e9ee6c7270196b6a434842f412cb50` |
| od-frozen-implementation-20260921.zip | `fc8505e8256d2623dbe078adbc763675332804e67ab86555d964395234fcb7d3` |
| od-grouped-development-v1-20260921.json | `ef75f483d59d7a251c5c68a43af73f252bb8982fc3052c1bf86e522d7d587b8b` |
| od-grouped-paired-20260921.json | `c03e08b7eaf1caa5ccfc6039f4f6f4c2ff1e6e2614d4be8570c75b15b967c75b` |
| od-september-holdout-20260921.json | `f1d8cdc6c302c9a3641d8cf6c165fa8b30192bbadca1fedb5f6c304299df834a` |
| od-october-holdout-20260921.json | `0059df9c927a9dbe1740d888f3f07531849187286fd092bb67879cbfaf045619` |
| od-september-paired-20260921.json | `d08291a009548a1c66f49c41d921df29a94f8bcf6b76ef2c3cb8062b45aa2f8c` |
| od-october-paired-20260921.json | `f4f2b12a77869ba789a6b626f913d1d053cc4b7f6e53511671f4a1e1ac5995ba` |
| od-september-diagnosis-20260921.json | `aae9eae75c7702f5ab566a9672fcf6611a26d11f146b874e0a5df317a41e89ed` |
| od-october-diagnosis-20260921.json | `1d632136ad2ac3bc277a2b6fae0e403e7fefa7ab56911308004abe838af7bf58` |
| official-passage-sources-20260921.zip | `21028b737062c8a02bff18819629843ea124531daaedc8bee8689039eeefe360` |
| ukho-routeing-research-20260921.zip | `0673dd6405466f412ab51215a727f47b63a46b0438146bfa3768d8917aa46ca5` |
| ukho-global-mesh-audit-20260921.json | `ee9af65e8a25e9fc09ea90302eb8284d0df378c04c03c29aa661d50140a5cc8c` |
| phase3-status-20260921.json | `9c6f7f633683c0e3480e19ca6f5950462951796acc7c7288c6ac364390858df1` |
| od-post-evaluation-tools-20260921.zip | `8d3a0c41e89c9c3915adaa4411ae15c93ab5d1837627642ec7000c806793c2b2` |

The executable status audit requires both registered dates, rechecks their criteria
and censuses, and reports regional temporal **PASS**, global phase 3 **NOT_PASSED**.
Commands are in [runbook.md](runbook.md). No acceptance threshold was lowered.
The post-evaluation archive preserves reporting/source-audit helpers and tests;
it is not a prospective registration. No jobs remain running at this checkpoint.

Remaining work is not another New York parameter search: obtain suitable finer
channel/water geometry and direction/constraint evidence, build and validate a
separate source-bound refinement for the missing passages, exercise actual expected
passages with original source endpoints, establish verified terminal associations
where required, and evaluate untouched observations in additional regions. The
existing global mesh and its source-reference ports cannot be called a verified
operational world network. Scope and evidence requirements from architecture.md
remain unchanged; phases 4–6 remain gated.
