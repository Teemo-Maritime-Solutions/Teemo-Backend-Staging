# Regional V2 results: phase 3 still NOT_PASSED

User resumed work on September 14, 2026 (America/Bogota). All changes and
acquisitions are local research. No Java/configuration changes, commits,
deployments, DB writes, endpoint activation or global-graph replacement occurred.

## Implemented corrections

The PDF review of [IHO S-57 UOC edition 4.4](https://iho.int/uploads/user/pubs/standards/s-57/S-57%20Appendix%20B.1%20Annex%20A_Ed%204.4.0_FINAL.pdf)
informed the water/bridge classification; see the separate
[development rationale](group1-development-20260914.md). `S57_GROUP1_V2` retains
positive DEPARE and DRGARE within chart coverage. Supported overhead bridges
retain unresolved clearance conditions; support geometry and other hazards remain
excluded. This does not establish current depth, vessel clearance or permission.

The 40-layer source registry is pinned against the
[NOAA Harbour registry](https://encdirect.noaa.gov/arcgis/rest/services/encdirect/enc_harbour/MapServer).
The extra layers returned 2 bridge-support areas, 111 piles, 9 hulk areas,
4 caution areas, 2 land points and 1 shoreline-construction point; four other
added layers returned zero features in the selected cells. Empty is not universal
absence. This remains a limited converted GIS source, not a complete ENC product.

All 30 common source layers have identical geometry and attributes after removing
only the declared service object ID. NOAA reassigned those IDs; they are preserved
separately in each immutable snapshot. Comparison report:
`data/maritime-routing/noaa-snapshot-v1-v2-comparison-20260914.json`.

V2 graph: 2,951 nodes, 16,178 directed edges, 18 strongly connected components;
largest component 2,925 nodes. All 8,089 reciprocal pairs passed at 12.5 m.
All 19 NOAA berth references remain unavailable; all edges retain unresolved
restrictions. Six bridge features are unresolved overhead constraints. The existing
semantic loader rejects V2, and Java catalog admission still requires the absent
portCountryQa. No runtime settings changed.

Two separate builds produced exactly the same artifact bytes. The archived V1
artifact was also revalidated successfully with the updated loader: all 7,036
reciprocal pairs, same 19 unavailable berths. V1 water classification is preserved.

## Experiments and unchanged acceptance

Each experiment requires >=10 selected cases, >=80% measurable, every measured
Hausdorff/Frechet <=1,000 m and no predicted chart-water conflicts. Invalid or
unmeasurable cases stay in the denominator. Selection, gaps, distances, connector
budgets and sampling were not relaxed. All predicted measured curves passed the
6.25 m physical-water check.

| AIS / model | Measurable | Median Frechet | Maximum Frechet | Spatial failures >1 km | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| March, old V1 distance | 0/20 | unavailable | unavailable | unmeasurable | FAIL |
| March, V2 distance | 17/20 | 994.74 m | 1,255.00 m | 8 | FAIL |
| March, V2 channel-first | 17/20 | development report | 1,241.73 m | 2 | FAIL |
| March, V2 balanced channel | 17/20 | 339.75 m | 1,173.90 m | 2 | FAIL |
| April holdout, V2 distance | 18/20 | 1,131.83 m | 2,409.63 m | 11 | FAIL |
| April holdout, V2 balanced channel | 18/20 | 385.91 m | 1,217.15 m | 2 | FAIL |

March is development, not independent validation. Balanced channel search uses
physical distance plus exactly 1 times projected off-channel distance. Its source
polygons are NOAA FAIRWY/positive DRGARE; no AIS-derived geometry, invented widths,
speed or clearance values enter this objective. The multiplier is an explicitly
development-selected engineering setting, not a nautical rule. Scores are never
reported as physical route lengths. See the [balanced experiment](channel-balanced-development-20260914.md).

The April [protocol](april-holdout-protocol-20260914.md) and
[machine-readable registration](april-holdout-registration-20260914.json) were
frozen at 2026-09-15T02:05:29.396270+00:00, before acquisition began. Both April runs
verified the exact code, library versions, graph, chart, configuration and protocol
hashes. The same 20 cases/source rows were used in both runs: 18 common measurable
cases, 13 improved Frechet errors, 3 regressed and 2 unchanged. No per-case model
switching or blended acceptance is reported.

April contained 8,178,885 CSV rows, 24 regional merchant vessels, 18,039 retained
records for those vessels, and 28 eligible regional segments before the fixed
20-case selection. Balanced run: 121.38 s, peak process working set 178,470,912 bytes.
Distance run: 108.63 s, peak 176,574,464 bytes. They ran sequentially; process
measurements include imports and scans and are not Java serving benchmarks.

## Remaining observed failures

Two April measured cases exceed the unchanged target, at 1,217.15 m and 1,127.14 m
Frechet. They are opposite-direction Upper Bay trips of about 7.4 km observed
length, not evidence that the chart channels are mandatory routes. Source-channel
preference is insufficient to model every route choice. Do not remove these cases
or choose their distance-only paths after inspecting the holdout.

The two unmeasurable April tracks intersect the same charted obstruction and
shoreline-construction features:

- `noaa-enc:156:US5NYCCF.000:141425`
- `noaa-enc:138:US5NYCCF.000:51936`

The observed-point counts and full densified curves are reported separately in
`regional-ais-april-v2-causes-20260914.json`. This attribution is not proof that AIS
or the chart is erroneous; the 2024 observations and 2026 chart are time-mismatched.
No points were moved and no hazard was removed to force a match.

## Immutable evidence under data/maritime-routing

| File | SHA-256 |
| --- | --- |
| noaa-new-york-v2-20260914/enc-source.zip | 1d71a3bf90a79863aa995d054dd2303a3c6c39e2f96e78d1577651d7b981b150 |
| noaa-new-york-pilot-v2-20260914.zip (and repeat) | 00558726e21cc9dcf45bca2df3088088bcb5f9ab2dc54c815945731b4dcff3eb |
| ais-april-holdout-20260914/ais-2024-04-01.csv.zst | 48b48ffa83dbcd9b609b733ea558d75a8da6c3cc81f7dfddd550e312939b262b |
| regional-ais-april-distance-v2-20260914.json | 2a5bbdc5783b27a5bc6b5879de04abda6fcc1245ea2d0068490a50d57f2e4114 |
| regional-ais-april-balanced-v2-20260914.json | 4892b7dfd717b74b6a79235c6977c6423fa9871976f0288fbdc26c77c2bb89e2 |
| regional-ais-april-paired-v2-20260914.json | 577a9253788328f1ce306fd7dda577a5151935a5ce60191420b51819d88c6f3e |
| regional-ais-april-v2-causes-20260914.json | efa79ed13ffef7e36480eeece533b194f4838c9677b674f030ddeec1f2ccfff9 |
| april-frozen-implementation-20260914.zip | 3c54f01f0e3480be4a6fd6c7fb903042f14e2001a25f1ab9ef28732a6e72ed1b |

The implementation bundle preserves the eleven registered source files, protocol
and registration with verified read-back hashes, without redistributing raw AIS.
Registration SHA: `745370b059c5db3689c03c38783a66d00e49a77f79253fb3f66c0671c56c2de3`.

## Verification and continuation boundary

70 Python tests passed, including legacy tests, Group-1/bridge/support handling,
tuple route costs, connector participation, source comparison, prospective
registration tamper/chronology checks and paired-cohort integrity. Fixtures are
synthetic; their success is separate from the failed real-world acceptance above.
No Maven run was needed for this Python-only continuation. `git diff --check` passed.

The source-model correction and independent comparison are complete. Regional
acceptance and global phase 3 are NOT_PASSED; phases 4-6 remain gated. A subsequent
route-choice model would need its own declared training sources and a fresh
untouched holdout, not retroactive tuning of the April result. Source-directed AIS
corridors are a possible next experiment, not implemented or asserted here.
Global completion also still needs wider approach/channel evidence and validation;
regional AIS improvement cannot establish those facts or current passage clearance.
