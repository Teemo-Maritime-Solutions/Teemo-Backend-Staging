# Prospective April physical-model comparison

Freeze this document, implementation, dependencies, graph, chart and cohort config
before acquiring NOAA AIS 2024-04-01. This date has not been downloaded or examined
in this project at registration. The designated output is
data/maritime-routing/ais-april-holdout-20260914. A machine-readable registration
records SHA-256 bindings and UTC timestamp; evaluation fails if these change or
registration does not precede acquisition. This is local preregistration, not an
independent third-party attestation.

## Frozen comparisons

Use the identical verified NOAA_ENC_REGIONAL_V2 artifact and April cohort for
DISTANCE_V1 and CHART_CHANNEL_BALANCED_V1. The latter's penalty coefficient is
exactly 1; it is a development-selected engineering choice, not a nautical rule.
Retain the March baseline, channel-first and balanced outcomes, including failures.
Do not select the better April output per case and do not tune on April.

The chart source is the same two NOAA New York cells in a complete versioned
40-layer GIS snapshot. AIS stays outside graph construction. Select merchant
vessels by deterministic identity hash before any route outcome, up to 64; retain
observations outside the envelope as segment boundaries and quarantine conflicting
same-time positions. Select up to 20 eligible segments by source/identity/start-time
hash. Keep existing gap, jump, length, connector and memory bounds unchanged in
regional-ais-config.json. Multiple segments may share a vessel.

## Unchanged regional physical acceptance

- At least 10 selected segments; at least 80% measurable, failures included.
- Every measured discrete Hausdorff and Frechet error at most 1,000 metres.
- Zero predicted chart-water conflicts; full curves checked at 6.25 m and metrics
  sampled at 100 m with a 1,000-sample bound.
- Report failures, observed/predicted physical lengths, per-case paired changes,
  wall time and process peak working set. Optimization penalties are not distances.

Accept/reject each model independently. Report the balanced model's predeclared
result and the comparator even if it performs worse. Do not redefine these targets,
drop difficult observations, shift endpoints or add data-derived passage claims.

## Gate boundary

All passage restrictions are deliberately unevaluated in this isolated physical
diagnostic; zero operational routes. Current vessel, tide, bridge maintenance,
depth clearance, closures and terminal access are not established. AIS 2024 and
chart 2026 are time-mismatched. This test is regional route-shape evidence, not
historical hydrographic truth, global phase-3 completion or production activation.
