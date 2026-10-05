# Chart-channel development comparison

This is a post-outcome development experiment, NOT an untouched holdout.
The corrected March distance-only run measured 17/20 cases, but eight measured
cases exceeded the unchanged 1 km Hausdorff/Frechet target. Investigate whether
unconstrained distance minimization cuts source-published channel bends.

Keep the V2 graph, source, AIS observations, endpoints, case selection, physical
mask, sampling and acceptance criteria unchanged. Compare DISTANCE_V1 against
CHART_CHANNEL_LEXICOGRAPHIC_V1 on exactly the same cohort. Use only valid NOAA
FAIRWY areas (208) and positive DRGARE (228), clipped to the conservative water
domain. Do not derive a channel from AIS or add invented coordinates/widths.

The experimental tuple search first minimizes projected distance outside those
source polygons and then geodesic route length. All bounded source/destination
connectors participate in both costs. There is no fitted penalty coefficient,
assumed depth, vessel speed, legal direction or passage permission. This is a
research route-choice objective and can prefer detours; report their lengths.
All unresolved chart restrictions remain recorded and unevaluated offline.

This hypothesis was selected using March development evidence. Any success here
requires a separately frozen untouched evaluation. A regional pass does not
complete global phase 3 or authorize phases 4-6.
