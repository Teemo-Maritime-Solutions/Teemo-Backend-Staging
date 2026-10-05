# Coastal refinement experiment, frozen before second sample

The 2024-01-01 NOAA diagnostic revealed 9/20 unavailable geometric anchors and
1/20 disconnected anchors, in addition to 7 coastline/observation conflicts.
It has now informed design and is **development evidence**, not an untouched test
set for the next iteration. Its 3 measured fragments do not establish accuracy;
shortest endpoint paths need not reproduce loops/operations within AIS fragments.

## Prespecified change

`coastal-refinement-config.json` raises H3 coastal resolution from 3 to 4 globally,
retaining base 2, port 5, full coast, 500 m geometric checks, 20 km connector radius,
original coordinates, invalid-geometry envelope quarantine and all other parameters.
The 150,000-node resource ceiling fails explicitly; it is not permission to omit
regions silently. No nodes are placed at AIS sample locations. No data-derived
false depths, channel geometry, speed or legal permissions are added.

Assess whether this purely geometric refinement increases source-port connectivity
and available coastal anchors. Retain the prior graph and its reports. A refinement
is not automatically promoted or called operationally realistic.

## Untouched second sample

NOAA 2024-02-01 is registered in acquire.py from the official distribution linked
by the 2024 NOAA catalogue. It is not a training input. Keep the same AIS selection,
gap, connector and metric settings. Compare old/new graphs on this same sample;
do not change parameters after seeing its outcomes and still call it a holdout.
It is a temporal split, not a guaranteed disjoint-vessel or independent-receiver
split. Two dates cannot establish global accuracy or seasonal robustness.

Before evaluating that second date, fix a validator limitation found by code
inspection: choosing only the closest clear anchor can falsely report disconnection
when another clear candidate reaches the destination. The revised validator
minimizes total connector-plus-route distance over ALL candidates within the same
24-candidate/20 km bounds. A synthetic regression covers an isolated nearest anchor.
Use this same revised validator for BOTH old and refined graphs on the second date;
do not attribute improvements from this validator change solely to mesh refinement.

Report every selected case and status; paired coverage changes are more meaningful
than comparing only successful-case average errors. No arbitrary operational pass
threshold will be invented. The global quality gate also still requires verified
port approaches, channels/restrictions and wider independent coverage.

## Execution status

Reconstruction completed in 1,906.27 seconds: 98,811 nodes and 2,335,476 directed
edges. After the same country audit, 1,114 reference ports are connected, versus
1,113 previously. The additional mesh density is not itself evidence of better
routing. Audited ZIP is 59,792,089 bytes (old 21,170,346). Complete geometry checks
passed (all 1,167,738 reciprocal pairs, zero conflicts/distance mismatches).
Paired second-sample validation completed with unchanged validator,
helpers, dependencies and case selection: 3/20 measured before, 5/20 after; 15
remain unmeasurable. The common three cases have unchanged geometric errors;
newly measurable cases have 18.20 km and 81.51 km discrete geometric errors.
See evidence.md for the full denominator, source hashes, timings and lengths.
The design above was recorded before these outcomes; subsequent tuning must not
reuse this date as an untouched holdout. The quality gate remains NOT PASSED.
