# Frozen coherent O/D transit experiment: September and October 2024

Freeze before acquiring either September 1 or October 1 NOAA AIS source. Use the
six-date model `od-coherent-model-v1-20260921.json`, SHA-256
`bb8644cdc676f3f9004233140c8f30f66aae6e603fd3fccfc6ae856730a0ff4a`.
Its development specification is `od-development-20260921.md`. Training comprises
March–August 1, 2024 only. All prior failed experiments remain unchanged and visible.

Five vessel-group development checks already completed: 120 selected cases,
116 measured; 70 distance-only spatial failures versus 3 conditioned failures.
March, July and August pass the regional engineering criteria; April, May and June
fail. This does not establish universal accuracy. Development report SHA-256:
`ef75f483d59d7a251c5c68a43af73f252bb8982fc3052c1bf86e522d7d587b8b`.
No parameter or case-specific change follows that evaluation in this experiment.

## Prediction and physical model

Use all 160 chart-clear, transit-eligible training tracks in the final model;
retain the other 31 original tracks in the model census. Each query uses only
the original origin and destination, never its interior observed trajectory.
Select one coherent, forward training subtrack by summed endpoint projection
distance, ties by source track hash: each projection at most 1,000 m away, directed
along-track separation at least 1,000 m. Only original consecutive pairs overlapping
that subtrack support the route. Keep the original 150 m evidence buffer, minimum
25 m movement, direction cosine >=0.5, and distance + unsupported distance objective.
If no template matches, all edges have unsupported cost equal to distance, giving
the distance optimum. This rule is fixed before either holdout. No post-hoc switching.

Use the unchanged NOAA V2 physical graph and independently checked geometry report.
Original graph coordinates, chart exclusions, unknown depth, legal restrictions and
endpoint anchors remain unchanged. The offline physical diagnostic does not activate
the Java graph or clear any restriction. Template buffers never add physical water.

## Population, acquisition and execution

Use the original FROZEN_CONFIG and TransitConfig, including 64-vessel hash sampling,
20-case hash selection, original gap/jump limits, endpoint displacement >=1,000 m
and displacement/observed-length >=0.5. Preserve the complete original candidate
census, including unselected, outside-cohort and selected failed cases. No endpoint
relocation, case replacement, new intermediate destinations or intent labels.

Register exact graph/chart/model/protocol, 27 implementation files, environment,
training-source hashes and passed full physical-geometry report. The untouched-file
audit covers the existing local maritime source root, not all external systems.
Archive exact registered implementation and protocol. Local time records are not
third-party timestamp attestation. Download fixed HTTPS URLs only, no redirects or
TLS bypass; maximum 400,000,000 bytes/date, Zstandard and length validation, receipts
with code/registration/source hashes and acquisition timestamps. Failed acquisition
does not authorize substituting a date.

Evaluate September distance then conditioned; October distance then conditioned.
Same extracted observations/census for both objectives per date. No tuning between
dates. Original measured Hausdorff/Frechet use the unchanged 100 m sampling and
bounded sample count; full predicted geometry is independently checked in chart
water. Retain all output reports with resource use and selected template provenance.

## Unchanged acceptance and interpretation

Per date and objective: at least 10 cases, at least 80% measurable, every measured
Hausdorff AND Frechet error <=1,000 m, zero predicted chart-water conflicts. Report
each date separately, including comparator failures; the new model requires both
dates passing for this regional temporal result. Do not pool failures away or change
criteria after acquisition. A failed grouped development month remains failed even
if both temporal holdouts pass. Temporal holdouts may contain previously seen vessels;
they are not unseen-vessel tests. These are regional segments, not verified voyages.

Even a two-date regional pass cannot close global phase 3: authoritative worldwide
geometry, access, expected passages, constraints and adequate independent coverage
remain separate requirements. AIS is historical and reception-biased; the charts
are from 2026 and observations from 2024. No current permission or vessel clearance
is inferred from any prediction or past observation.
