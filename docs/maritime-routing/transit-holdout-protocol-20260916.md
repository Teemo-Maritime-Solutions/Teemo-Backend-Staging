# Frozen origin/destination transit experiment: July and August 2024

Register both NOAA dates (2024-07-01 and 2024-08-01) before downloading either.
Use the existing March-trained directed model without refitting, parameter changes
or selecting a model using these new outcomes. Earlier April/May/June failures
and their exact replay implementations remain immutable.

## Prediction task and population

Recommend a route between the original origin and destination of a regional,
displacement-dominated merchant AIS segment. This is not full-trajectory
reconstruction, a port-call detector, an intent classifier or all global voyages.

Keep the existing FROZEN_CONFIG, vessel sampling, source parsing, timestamp,
envelope, gap, jump and track-length checks. Before graph, water, path or metric
checks, require endpoint displacement >=1,000 m and displacement / observed
curve length >=0.5. These population boundaries were declared before acquisition;
they do not relax the independent 1,000 m spatial acceptance criterion.
Preserve the complete original candidate census, source rows and endpoints,
including out-of-scope curves and hash-unselected transits. Select at most twenty
eligible transits by the existing source/identity/start-time hash. Failed selected
cases stay in the denominator. No extracted intermediate destinations or per-case
objective switching. Legitimate sinuous transits may be outside this population.

## Unchanged physical graph, model and comparator

Use NOAA_ENC_REGIONAL_V2 and the immutable March directed model:
`march-directed-corridor-model-v1-20260915.zip`, SHA-256
`69a8dff41b46166cf416a7e44434b86cd81da1b331eaa93876dc566e198207a0`.
Its 874 original consecutive pairs from 23 usable March tracks are the only fitted
evidence. Three training chart-conflicting tracks remain accounted for.

Compare DISTANCE_V1 and DIRECTED_AIS_BALANCED_V1 (physical distance plus one times
unsupported directed distance; 150 m proximity, 25 m minimum training movement,
direction cosine >=0.5). Do not modify geometry, chart exclusions, restrictions,
unknown depth, legal status, endpoints or Java runtime. Original case evaluation
and metrics are imported from the frozen evaluator, not independently redefined.

## Acceptance and execution

Per date and objective: >=10 selected transits, >=80% measurable, every measured
discrete Hausdorff and Frechet <=1,000 m, zero predicted chart-water conflicts.
Do not pool dates or switch objectives. Report comparator failures even if the
directed model passes; a comparator failure is not a directed-model failure.
The directed model needs both dates independently passing for regional transit
evidence. Each model/date still gets its own acceptance result.

Run July distance, July directed, August distance, August directed sequentially
without tuning between runs. Keep all four outputs immutable. Check identical
censuses/source rows for paired results. Report restrictions, training support,
physical distances, metrics, coverage, runtime and whole-process working set.

Bind exact implementation, environment, protocol, graph, chart, fitted model and
validated physical geometry in the registration. Archive exact implementation and
registration before acquisition. Acquirer uses fixed HTTPS NOAA URLs without
redirects/TLS bypass, at most 400,000,000 bytes per date, Zstandard header/length
checks, timestamps, digests and registration hash. A failed/incomplete download
does not authorize substituting another date. Local registration is not an
independent timestamp attestation. The untouched-source check covers the declared
local data root; it does not prove absence from all external locations.

## Gate interpretation

A regional transit pass is not global phase-3 acceptance, legal passage clearance,
verified terminal access or proof of current navigability. 2024 AIS versus 2026
charts, terrestrial reception bias, multiple segments per vessel, sparse training
and population limits remain explicit. All operational routes remain blocked
unless their separate authoritative evidence gates are satisfied. No activation,
production configuration change, credentials, licences or deployment is implied.
