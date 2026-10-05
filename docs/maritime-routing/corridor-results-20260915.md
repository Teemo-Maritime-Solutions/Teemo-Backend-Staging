# Directed AIS experiment: results and phase-3 decision

Status: **NOT_PASSED**. No threshold changes, success-only filtering, production
activation, database writes, commits or deployment. All paths below are relative
to the repository; large evidence files remain under ignored data/maritime-routing.

## 1. Diagnosis and implementation

The earlier April channel-preference failures were two physically clear route-choice
regressions, not evidence that chart hazards should be removed. Source audit:
`corridor-failure-audit-20260915.json`, SHA
`fd69eba431f08b3c51330f180a2d26b1a942753aae993baa4c757e639bf5dd69`.
The obstruction/pier affecting unmeasurable April tracks still has unresolved
depth/condition evidence. No source coordinates or physical exclusions changed.

`regional_corridor_model.py` fits only March 1 original sequential merchant AIS:
26 eligible tracks, 23 usable, three chart-water-conflicting tracks accounted for;
874 directed observation pairs and 1,514 stationary/short pairs not used for fit.
An edge requires complete coverage by a 150 m pair buffer and direction cosine
>=0.5. Reverse mesh edges do not inherit forward AIS evidence. Optimization is
physical distance plus unsupported directed distance, not navigation permission.
Original restrictions, unknown depth and legal status remain unchanged.

Model: `march-directed-corridor-model-v1-20260915.zip`, SHA
`69a8dff41b46166cf416a7e44434b86cd81da1b331eaa93876dc566e198207a0`.
2,143/16,178 physical directed edges have this model's support. March resubstitution
measures 17/20 cases, maximum Frechet 678.79 m; it is NOT independent validation.

## 2. Prospectively frozen temporal comparison

Both dates, both objectives, all criteria, the model, data bindings, 17 implementation
files and dependency versions were registered at 2026-09-15T17:29:49.653845+00:00,
before acquiring either holdout. The separate April frozen implementation is intact.
No tuning occurred between the four sequential runs.

- Protocol: [corridor-holdout-protocol-20260915.md](corridor-holdout-protocol-20260915.md).
- Registration: [corridor-holdout-registration-20260915.json](corridor-holdout-registration-20260915.json),
  SHA `e12cf33382e8c467e2037119421b0e9d3cefb8dc0c7d6886b145cdb7e6f8f091`.
- Exact replay bundle: `corridor-frozen-implementation-20260915.zip`,
  SHA `eb2a9c30863c34e68cc3e3468ab4657de567b2dd80ab14869241487d26b0f5c4`.
- May source: 271,683,237 bytes,
  SHA `bea92163dd948284428302e5aacbf1f2a061562bd02f0c33e20d609aeabdc6a7`.
- June source: 262,909,026 bytes,
  SHA `9c54adb46f0e816fb328ec96132ea1b1d5f94dd0456f46d9810e8490adeb1a57`.

Unchanged acceptance per date/model: >=10 selected, >=80% measurable, every measured
Hausdorff and Frechet <=1,000 m, zero predicted chart-water conflicts. Each date has
20 identical selected cases for both objectives; all original rows/times/endpoints
were verified by paired reporting. Unknown observations are retained in denominators.

| Date / objective | Measurable | Cases over 1 km | Median Frechet | Maximum Frechet | Time | Peak process working set |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| May / distance | 18/20 | 11 | 1,042.90 m | 3,445.25 m | 108.91 s | 177,442,816 B |
| May / directed AIS | 18/20 | 2 | 434.44 m | 3,438.28 m | 122.52 s | 181,587,968 B |
| June / distance | 19/20 | 12 | 1,043.36 m | 6,830.49 m | 67.90 s | 175,849,472 B |
| June / directed AIS | 19/20 | 2 | 455.99 m | 6,830.49 m | 76.72 s | 181,436,416 B |

ALL FOUR FAIL. No predicted chart-water conflicts, but zero operational routes.
May paired measured cases: 15 improved, one regressed, two unchanged. June: 16
improved, one regressed, two unchanged (0.01 m reporting tolerance, NOT acceptance
tolerance). No pooling across dates, per-case switching or significance claim.

Report SHA-256 (files named `corridor-<date>-<objective>-20260915.json`):

| Report | SHA-256 |
| --- | --- |
| may-directed | 32b2cbbca13e40d99e1e984cec80dc9b364aad1539f19854dc648d7b736a0414 |
| may-distance | 88ba734584f3fa7e79e860a685ba86a3e4834c8cdec17246410ee650fc91bd3b |
| may-paired | b7943d98aa4ee0236acd22cabdccdadad87d515fb97efc3feb1191b8612e9126 |
| june-directed | d1cc7e0eda72c0cdabb65548dc9a7e4f69a15339646d6f2dc248fc7ecf96fb5b |
| june-distance | c28e443926c44d6c1e4126f1ab38e6abbf6632b28f26e47664ca7a1d981d8eba |
| june-paired | 082fa6e4b17163563b8b611db67732f55d0a3bed02528f7c0fb8f1d4e45f80a4 |

## 3. What the remaining failures mean

Post-evaluation diagnosis re-reads original source rows and verifies source, graph,
track identity, endpoints and times. It is explanatory, not a new acceptance rule.

| Measured failure | Endpoint separation | Observed length | Predicted length | Frechet |
| --- | ---: | ---: | ---: | ---: |
| May `187c982f...` | Original endpoints retained | 10,282.62 m | 11,837.88 m | 1,009.50 m |
| May `42825ce7...` | 203.87 m | 9,291.87 m | 228.37 m | 3,438.28 m |
| June `68843e88...` | Original endpoints retained | 10,999.11 m | 12,629.50 m | 1,098.87 m |
| June `b2bd6ab4...` | 107.27 m | 16,121.26 m | 137.40 m | 6,830.49 m |

The two near-returning curves are inside conservative chart water. They are not
established AIS errors or confirmed manoeuvre types. For any observation p and
tolerance epsilon, a curve from a to b passing within epsilon of p has length at
least d(a,p)+d(p,b)-2*epsilon. With epsilon=1 km, original observations require at
least 4,924.26 m (May) and 11,764.74 m (June), whereas feasible endpoint routes have
costs of 228.37 m and 137.40 m. A positive-cost endpoint optimum cannot be expected
to reproduce these excursions. More tuning of the same objective cannot remove
that structural conflict. This bound does not infer vessel intent.

The two other measured failures remain ordinary route-shape errors. Three
unmeasurable cases remain excluded from metrics but included in selected counts:
May has one buffer/invalid/unknown-depth exclusion and one source obstruction/pier
intersection; June has a track near source land/shoreline construction. No hazards
were cleared on the basis of AIS.

Diagnostics: `corridor-may-diagnosis-20260915.json` SHA
`97a5d037d1f33f8e3bbf02cbf75017e86292bea7ce7aff6ca59ecde13464da05`;
`corridor-june-diagnosis-20260915.json` SHA
`7f4ec8fd547325182b7d19880b6f781631d243cc987ae91bdb2e21ee4fb1c4b7`.

The user confirmed the scope: **recommend transit routes between origin and
destination**. Transit-route prediction and full trajectory reconstruction
are different tasks. A future experiment needs a prospective, source-backed voyage
definition and/or intended intermediate destinations. Do not retroactively split
these holdouts, remove return tracks or count their exclusion as success. Preserve
these failed results and register any revised task on untouched observations.

## 4. Global data and integration

The existing global refined mesh has 98,811 nodes and 2,335,476 directed edges;
all 1,167,738 reciprocal pairs previously passed finer coastline checks at 500 m.
This shares GSHHG lineage and is not independent hydrographic truth. Its 1,114
connected port references are not approved terminal approaches. Expected sourced
straits and conditional Suez/Cape evidence remain missing.

Public discovery on this continuation:

- The [Danish authority](https://www.dma.dk/safety-at-sea/navigational-information/ais-data)
  identifies free historical CSV archives. Both web retrieval and an approved bounded
  CLI attempt failed to read the archive index (CLI TLS handshake timeout). This is
  an access result, NOT evidence that the data require purchase or do not exist.
- The [LINZ download page](https://encservice.linz.govt.nz/download) returned HTTP 200
  through the approved CLI and exposes base/update links. The official service
  uses S-63 encryption and [registration/permits](https://encservice.linz.govt.nz/registration).
  No account creation, terms acceptance, permit request or encrypted archive download
  was undertaken. Raw source availability alone would not validate approaches.
- The [Suez authority's rules page](https://www.suezcanal.gov.eg/English/Navigation/Pages/RulesOfNavigation.aspx)
  does not supply the missing pinned channel geometry or establish vessel-specific
  permission. Suez/Cape alternatives remain gated.

83 Python tests pass, including original suites and directional evidence,
registration/tamper guards, complete case evaluation and geometric diagnosis tests.
56 targeted Java tests pass with zero failures/errors/skips, including eight real
global source-reference pairs, four antimeridian crossings, four equator crossings,
three >=50 km-separated alternatives and HTTP constraints. This is the routing
suite, not a claim that every project/Spring-context test was rerun. A separate V1
regional regression test also passes; both V1 and V2 preserve blocked activation.
No runtime or production configuration changes were made for this experiment.

Dated Java reports are under `target/maritime-routing/20260915/`; earlier reports
were not overwritten. Global load: 14,116.23 ms; eight route searches: 62.84-730.22
ms; three alternatives: 963.40 ms. Heap after queries: 1,396,178,944 bytes, maximum
heap: 1,887,436,800 bytes. This is not peak RSS or an SLA/load test. Regional V2
loads in 405.08 ms, retains all 19 unavailable berth references and returns zero
routes for the adjacent-node policy check. Its heap measurement shares the Java
process with the global test and must not be interpreted as regional-only memory.

Global runtime report SHA:
`946ca89f3cf0eedfe6e7c8ce54c7d0c34a732cde66ac5c0602b8275975c70144`.
V2 negative report SHA:
`2bb4784ea7a49b68058932a51e0cdc626506a7d6b5b852ef9324b61cc6cf7edd`.
Offline Maven resolution failed; the authorized online run succeeded. No Maven
settings, credentials or production properties were changed. Final recheck verifies
all 11 April and 17 May/June registered implementation hashes still match.

## Gate conclusion

Regional direction modelling is implemented and independently evaluated, but the
regional acceptance and global phase-3 gate remain failed/incomplete. Later phases
remain gated. Completing software tests cannot substitute for missing maritime
evidence or a coherent, prospectively specified prediction task.
