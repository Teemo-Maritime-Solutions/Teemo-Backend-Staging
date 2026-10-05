# Maritime v2 API and migration

The integrated [phase 7 plan API](phase7-api.md) creates immutable comparisons
from server-computed weather routes, declared contractual mappings and joint
research scenarios. It includes provenance, explanations and recalculation into
a new revision. It requires explicitly configured local plan storage.
Contract evaluation is documented in [phase 5](phase5-api.md); declared-probability
simulation and Pareto analysis in [phase 6](phase6-api.md).

Phase 4 adds `GET /api/v2/maritime/weather` and
`POST /api/v2/maritime/weather-routes`. Their distinct contract, seconds-based time
units, required vessel parameters, forecast pins and replay modes are documented in
[phase4-api.md](phase4-api.md). The distance-only contract below remains unchanged.

The [phase 3 research plan](phase3-research-plan-20260921.md) is implemented through
an optional schema-2 combined artifact and route-level coverage metadata. The
artifact still requires a matching validation report and operator-pinned digest;
this change does not activate it in production. See the latest evidence report for
the validation state of each specific artifact.

The graph is for **research/planning**, not navigation. No public build endpoint:
ETL is an offline/operator task, never an unauthenticated expensive HTTP operation.
All measurements use WGS84 degrees/metres; duration is hours. Endpoint contracts
are implemented in `mapping/routing/v2/MaritimeRoutingController.java`.

| Endpoint | Contract |
| --- | --- |
| GET `/api/v2/maritime/graph` | Graph manifest, version, sources, engineering parameters, coverage and quality report. 503 if missing/invalid local artifact. |
| GET `/api/v2/maritime/provenance` | Exact graph version, source receipts/licences/limitations and parameters. |
| GET `/api/v2/maritime/ports?status=CONNECTED_REFERENCE_POINT&offset=0&limit=50` | Source port records, validated connector status, pagination (limit 1–200). `status=ALL` includes excluded records; no silent geocoding. |
| POST `/api/v2/maritime/routes` | Source port IDs, K bounded loopless candidates, distance, optional user-speed time, constraints and explanations. Returns fewer than K when candidates are similar, insufficient or search budget exhausted. |

Request fields (unknown fields must fail, not be ignored):

```json
{
  "originPortId": "<id returned by /ports>",
  "destinationPortId": "<id returned by /ports>",
  "acknowledgeResearchLimitations": true,
  "k": 3,
  "maxCandidates": 20,
  "maxExpansions": 2000000,
  "minimumSeparationM": 50000,
  "requireKnownLegalStatus": false
}
```

Angle-bracket values are placeholders, not port records. Resolution, search budget,
diversity distance and pagination are **engineering parameters**, not observed
operational data. `k` 1–8, candidates k–100, expansions 1–20,000,000. The default
distance separation is 50 km; zero explicitly disables geographic filtering. This
does not itself prove topological classes. The pinned Suez research artifact now
provides a sourced canal path; `SUEZ_CANAL_RESEARCH` can be excluded to compare
the Cape alternative, without establishing operational transit eligibility.
With positive separation, temporary geographic exclusion searches precede
bounded Yen to avoid spending all candidates on nearly identical mesh paths. With
zero separation, pure bounded Yen is used. This portfolio is not a globally
K-shortest-diverse guarantee. `maxCandidates` jointly bounds the initial path,
geographic attempts (including unsuccessful ones) and enumerated Yen paths;
`maxExpansions` additionally bounds internal spur-search node expansions.

Optional caller inputs: `userSuppliedSpeedKnots`, `userSuppliedDraftM`,
`userSuppliedClearanceM`, `userSuppliedClosedEdgeIds`, `userSuppliedProhibitedZones`.
No speed default; `estimatedHours=null` when absent. Supplied speed is constant
speed over ground and excludes all port waiting, weather and canal time. It is not
a vessel performance prediction. Draft requires explicit clearance (including
explicit zero if desired); unavailable minimum depth rejects the edge. Closure
IDs are local to a graph version; unknown edge/zone IDs fail validation. A known
legal-status request rejects unknown permissions. Omission does **not** establish
legal permission, depth safety, acceptable ice/weather, or insurance cover.

`expectedGraphVersion` is optional for simple queries and REQUIRED when specifying
closed edge IDs. A mismatch returns 409 GRAPH_VERSION_MISMATCH; edge constraints
without a version return 400. This prevents old edge IDs from silently closing
different edges after a snapshot update. Obtain the version from GET /graph.

Origin and destination must be distinct source IDs. Caller declarations are echoed
under `inputs`; `inputProvenance` identifies USER_SUPPLIED, request receipt time,
units and scope. No redistribution rights or observation date are assumed.
Graph/provenance/port responses and handled errors also expose quality metadata;
unavailable graph context has null graphVersion/dataDate and empty sources, not
made-up provenance. This distance-only endpoint has no actor breakdown; the
separate contract and integrated plan APIs require explicit commercial inputs.

Response includes graphVersion, source receipts, dataDate (acquisition time, not
survey date), missingData, uncalibrated confidence, assumptions, routes, search
metrics, rejection reasons and distance recommendation scope. No invented actor
breakdown or financial zero: buyer/seller exposure is unavailable in this response.
`geometry` is a longitude/latitude **MultiLineString** split at the antimeridian;
ellipsoidal geodesic segments are densified using the artifact's configured step.
Never connect the end of one dateline part to the start of another on a flat map.

Status codes: 400 invalid input/unsupported field, 404 source port absent, 422
research acknowledgement absent, unavailable port access, no route under supplied
constraints or search budget exhaustion; 503 no usable artifact. Distinguish
SEARCH_BUDGET_EXHAUSTED from proof of disconnection. Edge rejection counts count
evaluations during searches, not probabilities or counts of distinct hazards.

## Regional controls and route coverage

Each route now includes `coverage`: `purpose=RESEARCH_NOT_NAVIGATION`, `graphVersion`,
`regionalControls`, `spans`, and `missingData`. A span uses inclusive, zero-based
`firstEdgeIndex`/`lastEdgeIndex` into that route's `edgeIds`. `GLOBAL_COASTLINE_RESEARCH`
means the segment has only global coastline checks; `REGIONAL_CHART_CONTROLS_RESEARCH`
marks edges intersecting the declared regional coverage. On crossing edges, the
regional water/control checks apply to the intersecting portion; the portion outside
coverage retains global coastline checks. A marked span does not imply that its
entire geometry is covered by NOAA. Both scopes retain unknown operational layers.

Schema-2 `regionalControls` binds the NOAA source, regional/global input artifacts,
policy, transition parameters and implementation hashes. Original chart evidence
is embedded in the immutable artifact; the server needs no separate live NOAA
download. Missing, altered or incompatible embedded evidence makes loading fail.
The catalogue additionally requires complete, matching regional validation. It
does not retry with a global-only graph. Schema-1 artifacts remain supported with
`regionalControls=null`; they do not acquire regional coverage from this code update.

The two NOAA source cells define the regional extent, not the whole New York state
or all US waters. Their currently unresolved restrictions remain hard blocks for
all alternatives. The current connected regional topology is physical research
evidence, not an unlocked regional route API or authorized berth access.

## Local artifact prerequisites

Set `MARITIME_GRAPH_ARTIFACT`, `MARITIME_GRAPH_SHA256` (trusted lowercase 64-character
whole-file digest) and `MARITIME_GRAPH_VALIDATION_REPORT`. The report must match the
graph version, pass full reciprocal-geometry checks, cover every mesh edge, and
agree with country-audited port coverage. Older unchecked reports are refused.
See runbook.md; no automatic activation or operational certification takes place.

## Deliberate legacy compatibility change

Original URLs and historical read contracts are retained. New calculations at:

- `/api/routes/calculate-optimal-route`
- `/api/routes/distance-between-ports`
- `/api/routes/{routeId}/recalculate`
- `/api/incoterms/calculate`
- `/api/ai/predict-weather-delay`

return **503 UNVERIFIED_LEGACY_MODEL_DISABLED**. This is an intentional safety
migration, not an accidental missing endpoint: legacy schemas cannot represent
unknown estimatedHours/cost/probability and used unverified coordinates, 18-knot
defaults, simulated model labels and arbitrary tariff/insurance/tax percentages.
Preserving successful invented numbers would violate the project requirements.
Use the separate v2 routing, weather, contract, simulation and integrated-plan APIs
within their documented research scopes. The legacy implementation is retained
for regression/migration analysis, not enabled as an alternative production engine.

Seeded mapping data, catalogue backbone, automatic GFW and graph warmup are disabled
by default. **No MongoDB data, history or existing source files were deleted.**
Existing stored routes are historical/unverified and are not retroactively certified.
There is no automatic mapping from legacy Mongo IDs to UN/LOCODE: that requires a
verified correspondence, not fuzzy names or coordinates guessed from city centres.

Current authentication configuration permits all routes; do not expose this server
publicly without a separate authorization/rate-limit deployment review. No graph
mutation, file selection, download or arbitrary URL parameter is exposed over HTTP.

## Research availability and remaining limits

Phases 4 through 7 implement parametric weather-time search, all-eleven-term
contract evaluation, declared joint simulation, VaR/CVaR, Pareto comparison and
integrated recalculation. Each alternative needs explicit contractual and scenario
inputs; the system does not automatically invent terms, tariffs or probabilities.
Operational ETA, calibrated loss probabilities, vessel fuel/emissions, globally
verified canal permissions and terminal access remain unavailable claims.
See [evidence.md](evidence.md) for the measured research scope and limitations.
