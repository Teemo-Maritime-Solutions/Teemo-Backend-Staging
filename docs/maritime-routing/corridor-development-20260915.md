# Directed AIS route-choice experiment, 2026-09-15

Preserve the failed April comparison and its frozen eleven-file implementation.
Do not change its code, source chart, observations, criteria or acceptance result.

## Diagnosis and hypothesis

The two measured April regressions relative to distance-only have clear physical
paths but worse chart-channel preferences. The two unmeasurable tracks intersect
source obstruction/pier features with missing VALSOU and SORDAT 200404. Neither
clearance nor a positional error can be established from those attributes. Keep
all physical exclusions, endpoints and unknown passage conditions unchanged.

Fit a SEPARATE directed preference model only to March 1, 2024 NOAA merchant AIS.
Use the existing bounded vessel selection, track/gap/conflict filters and chart
domain. Retain excluded track counts and source-row lineage. Use every eligible
training track, not only the twenty evaluation cases. A whole training track
crossing the current chart domain is excluded from fitting and recorded.

Associate full physical graph edges with original consecutive observation pairs
using a 150 m projected buffer and direction cosine >=0.5. Ignore pairs with
movement <25 m, so stationary jitter does not supply direction. These are declared
engineering association parameters, NOT measured AIS accuracy, legal lanes,
surveyed channel width or permitted direction. Limit to 10,000 tracks / 100,000
pairs. Buffers never expand the physical water domain.

Research objective: geodesic distance plus exactly one times distance on edges
unsupported by a direction-compatible training pair. Include both endpoint
connectors with their actual travel direction; retain all clear candidates.
Report physical distance separately from the score and retain all unresolved
chart constraints. No additional reverse edge gains evidence merely because
the physical graph has reciprocal topology.

March replay is resubstitution/development, not validation. After development,
freeze code, model, chart, cohort, criteria and two untouched dates (May 1 and June
1, 2024), before acquisition. Compare the declared AIS model against distance-only
on identical selected cases. No holdout-specific coefficient adjustment, per-case
model switching, removed failures, altered thresholds or operational activation.
The original regional >=80% measurable / every measured spatial error <=1 km
criteria remain; global acceptance is separate.
