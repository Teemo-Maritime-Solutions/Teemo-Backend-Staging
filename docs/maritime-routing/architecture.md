# Maritime routing: audit, architecture and evidence

Audit started 2026-09-08. Initial worktree clean; no applicable AGENTS.md found in
the repository or parent directories. This is a research/planning system, **not
an ECDIS, nautical chart or assurance of safe navigation**. A connected graph is
not evidence of adequate depths, legal passage or safe weather.

## 0. Verified existing flow

`RouteController (/api/routes)` -> `RouteService` (MongoDB ports, via/avoid,
history/recalculation) -> `RouteCalculatorServiceImpl` -> `RouteGraphBuilder`
(catalogue + cached GFW presence overlay + port connectors) -> `AStarPathfinder`
-> `RoutePath` -> legacy response/metadata. SafetyValidator examines principal
ports **after** search. It does not constrain traversed waters.

Findings from source, not assumptions:

| Component | Finding |
| --- | --- |
| AStarPathfinder | Distance plus fixed 10% restricted / 20% high-risk penalties; injected safety, clock and navigation providers unused in search. |
| RouteGraphBuilder | Fixed 18 knots; manually located catalogue; overlays made bidirectional; land validation skipped for overlay edges and canals; port shortcuts can cross land (220/450 nautical miles). |
| MaritimeLandMask | Natural Earth 1:10 million GeoJSON, not 10 metre data; tests interior samples every 8 nm using linear latitude/longitude interpolation; ignores polygon holes and endpoints; handcrafted fallback polygons on missing/corrupt resource. |
| RouteCalculatorServiceImpl | Missing edge silently replaced by a straight line and 18 knots. |
| GlobalFishingWatchCorridorOverlayProvider | Neighbour links between presence cell centroids, not observed sequential trajectories; spatial thresholds are not AIS confidence. Cache exists but can refresh in request workflow. |
| RouteService | Labels catalogue-assisted routes AIS_OVERLAY; legacy distance helper can use Haversine. Weather is not part of edge evaluation. |
| Weather/AI | api.weather.gov hazards do not establish global coverage; hazard heuristics have fixed probabilities; data/build_dataset*.py contain random weather/delay labels. Existing ONNX model has no verified observational lineage. |
| IncotermServiceImpl | Client distance, fixed freight/tax/insurance/port rates, only CIF/FOB/DAP recommendations and experience scores; not route-specific economic exposure. |
| Security | Existing security configuration permits every URL. New build operations must stay offline; this project is not thereby production-secure. |

Baseline targeted Maven tests passed before edits (see evidence.md for commands).
Existing tests using synthetic ocean points are algorithm tests, not global validation.

## 1. Source decisions (primary sources checked 2026-09-08)

| Source | Decision, licensing and limitations |
| --- | --- |
| [GSHHG 2.3.7](https://www.soest.hawaii.edu/pwessel/gshhg/) | Selected coastline input. Release 2017-06-15; LGPL, keep accompanying notice/license with derived artifacts. Full/high/intermediate resolutions are distinct and recorded. L1 land + L5 Antarctic ice front, conservatively exclude enclosed inland waters. Old generalized coastline, no channel dredging/depth/legal status. Dateline-split shapefiles. |
| [Natural Earth](https://www.naturalearthdata.com/about/terms-of-use/) | Selected map units 5.1.1, public domain, for coarse country/coordinate sanity checks. Not sovereignty, maritime boundaries or berth validation. Original legacy coastline provenance remains unknown; the new download has its own receipt, never fabricated retrospective metadata. |
| [NGA WPI](https://msi.nga.mil/Publications/WPI) | Evaluated, not used: viewer TLS trust failure and official CSV HTTP 403 after authorized network access. Certificates never bypassed. [2019 publisher notice](https://msi.nga.mil/api/publications/download?key=16694622/SFH00000/Pub150bk.pdf&type=view) is not proof of an unavailable current snapshot. Reference coordinates would still not establish berth/water access. |
| [UN/LOCODE](https://unlocode.unece.org/publications/) | Selected official 2025-1 package; publication site specifies CC BY 4.0. Retain attribution/notices. Function 1 and original valid coordinates required; missing/deleted/conflicting records excluded. Source country/coordinate errors were found and quarantined, never corrected by guessing. Trade-location reference, not a terminal. |
| [GEBCO](https://www.gebco.net/data-products/gridded-bathymetry/terms-of-use) | Public domain with acknowledgement; explicitly NOT for navigation/safety at sea. Not accepted as authoritative minimum depth or clearance. May support separately labelled research, not safe-draft claims. Depth remains unavailable without appropriate data. |
| [GFW API](https://globalfishingwatch.org/our-apis/documentation/) | API restricted to non-commercial use; authentication and dataset-specific terms required. Existing credentials are not inspected/reported. [Presence cells](https://api-doc.globalfishingwatch.org/our-apis/documentation/docs/v3/general-api-doc/data-caveats) are not continuous directed tracks. No automatic promotion to navigable corridors. |
| [NOAA AIS 2024](https://www.fisheries.noaa.gov/inport/item/73064) | Selected 2024-01-01 regional diagnostic from the new official Azure distribution linked by NOAA; obsolete ZIP link returned 404. [Use constraints](https://coast.noaa.gov/data/marinecadastre/ais/faq.pdf): historical terrestrial AIS for coastal/ocean planning, not global satellite tracks or navigation. Raw data kept local; unrestricted commercial redistribution not assumed. Excluded from graph construction. Once its results inform design, classify it as development evidence and reserve a fresh holdout. |
| [ECMWF open data](https://www.ecmwf.int/en/forecasts/datasets/open-data) | Candidate global winds/waves with CC BY 4.0 plus ECMWF terms/attribution. Pin forecast run, valid time, units, resolution and retention. Forecasts are predictions, not observations. |
| [Copernicus Marine](https://help.marine.copernicus.eu/en/articles/4220312-i-just-opened-my-account-but-will-it-still-be-free-in-2-3-or-5-years) | Candidate currents/waves, attribution and product licence; download may require registration. No credentials assumed. |
| [IHO / national hydrographic offices](https://iho.int/en/standards-in-force) | Charts/ENC, routing measures and official channel authorities needed for detailed approaches, depths, restrictions and closures. Standards are not a free global restriction dataset. Canal centrelines/clearances cannot be hand-authored or cut through land. |
| [OSM](https://www.openstreetmap.org/copyright) | Candidate refinement, ODbL and database share-alike obligations; variable completeness and no navigational warranty. Deferred; never silently combined with incompatible redistribution terms. |

## 2. Architecture decision

Offline Python ETL; Java immutable indexed graph and independent search engine.
New `/api/v2/maritime` contract allows nullable measurements and explicit missing
data, unlike legacy primitive estimatedHours. Preserve legacy URLs/shapes while
removing unsafe fallbacks; do not promise that formerly unsafe routes still succeed.
Unverified legacy estimates must not enter v2 or be called production evidence.

Evaluate [H3](https://h3geo.org/docs/) versus [S2](https://s2geometry.io/devguide/s2cell_hierarchy):
H3 supplies global neighbours and children, including pentagons/poles; S2 has exact
geometric subdivision and good spherical geometry, but a separate neighbour and
mixed-level adapter is necessary. Choose H3 for offline discretization only; its
approximate child geometric containment is **not** a land predicate. All node/edge
geometries are independently checked. A latitude/longitude rectangular mesh is
rejected for polar degeneracy and latitude-dependent connectivity.

Keep open-water coarse H3 centres; refine cells near shorelines/ports at configured
levels. Connect same-level neighbours and levels only when their full geodesic
polyline clears land. Mathematical mesh coordinates are **DERIVED**, not invented
observations or shipping lanes. Component analysis determines actual connectivity;
no fake bridge to force success. Land-located ports stay unavailable, with original
coordinates preserved, until an independently sourced water approach is supplied.

Physical directed edge -> hard restriction evaluation -> contextual RouteCostPolicy
-> search. No fixed combined weight in artifacts. Store WGS84 geodesic metres;
geometry in longitude/latitude degrees. Missing depth, speed, AIS support and channel
permission are null/unknown, not zero/unrestricted. Directed AIS overlays must come
from sequential observations and documented gap/quality filters; aggregates can
only provide separately labelled density evidence.

Use A* with a proved lower bound (zero is admissible for arbitrary nonnegative
policies; distance policy may use a conservative geodesic lower bound). Yen gives
loopless directed K paths and reuses the solver; Eppstein permits walks by default
and adds complexity here. Bound candidates/expansions, expose budget exhaustion,
and filter shared geographic corridors; returning fewer than K is legitimate.
Dense H3 meshes produce many similar Yen variants. First try temporary geographic
exclusions centred on interior nodes of accepted paths, then fill from bounded Yen.
Exclusions are search parameters, not observed hazards. With nonzero separation,
this is a candidate portfolio, not a global K-shortest-diverse guarantee. Initial
path, geographic attempts and enumerated Yen paths share maxCandidates; internal
spur searches also share maxExpansions. A conservative whole-segment separation
bound prevents different-resolution nodes being mistaken for different corridors.
The heuristic includes the minimum accepted edge/geodesic rounding ratio so that
measurement tolerance cannot make the distance lower bound overestimate.
Neither different cell IDs nor edge overlap alone prove geographic diversity or
topological distinction. Require geometric separation and eventually named passage
signatures. Do not claim all homotopy classes have been enumerated.

## 3. Artifact / data model / ETL

Versioned ZIP, JSON-lines tables and manifest; SHA-256 per entry; deterministic
ordering/ZIP timestamps, no pickle/executable payloads. Offline acquisition writes
source file + receipt (URL, licence/terms URL, source version/date, acquired UTC,
units, coverage, limitations, content SHA-256). Builder rejects absent metadata,
changed input bytes, invalid coordinates/geometries and unknown source references.
No secret or query-token URLs in receipts. Keep all datasets/artifacts outside Git.

Manifest: schemaVersion, graphVersion, createdAt, sources, engineering parameters,
table hashes/counts, quality state, unavailable layers. Nodes: id, point, kind,
source refs. Edges: from/to, geometry, distanceM, minimumDepthM nullable, kind,
specialZone nullable, restrictions, evidence refs; direction is stored explicitly.
Ports: source ID/name/point, connector status, node ID nullable and reason.
Runtime loads once from configured local artifact; immutable adjacency index, no
per-request AIS or ETL. Missing/invalid artifact -> explicit unavailable response.

Schema 2 adds the `GLOBAL_REGIONAL_RESEARCH_V1` profile, mandatory embedded regional
evidence and source/artifact/policy bindings. NOAA chart coverage takes priority for
water geometry inside its source cells; GSHHG remains the mask outside. Derived H3
resolution-8 transition nodes join the coarse worldwide mesh to the frozen regional
mesh through completely checked physical segments. Their coordinates are derived
mesh centres, not claimed observations or terminal approaches. Existing source port
coordinates and unavailable statuses are preserved. Regional edge annotations are
validated and exposed as route coverage spans; unresolved restrictions still enter
the same hard cost-policy gate used for every alternative.

At a chart boundary, eroding source polygons clipped by the chart itself would
create an artificial gap. The combined profile uses original positive source water
within a two-tolerance boundary band, with buffered obstacles still excluded. It
does not buffer water outward or alter the original regional experiment. Complete
composition validation separately checks the new geometry and exact inheritance
of retained global geometry from its pinned full-coast report. Regional observations
from the frozen pilot are not new validation observations for the combined graph.

ETL steps: download/receipt -> verify hash/licence -> geometry validation -> water
classification -> multiresolution mesh -> geodesic edge intersection checks ->
validated ports/optional sourced overlays -> components -> artifact -> independent
validation/benchmarks -> promotion. Invalid polygons must fail or be quarantined
with explicit evidence, never silently repaired and labelled original.

Updates: new source snapshot never overwrites an accepted artifact. Rebuild to new
version, repeat quality gates, compare coverage/regressions, then deploy by an
operator changing artifact path. Retain prior version for rollback. Topology static;
forecasts/closures versioned separately and expired by validity time.

Research activation requires an operator-pinned whole-file SHA-256 and matching
passing report with verified reciprocal geometries, complete edge coverage and
country-QA port counts. Independent execution is not independent hydrographic
truth: both coast checks share GSHHG lineage. The reciprocal-mesh validator rejects
nonreciprocal overlays; future directed overlays require a directed-edge validator.

Country QA changes availability, not node/edge geometry. Rejected port-reference
nodes may remain physically valid water nodes but cannot be chosen as API endpoints.
Invalid-source envelope quarantine excludes substantial Antarctic waters; handling
polar coordinates does not establish polar route availability.

## 4. Phase gates and acceptance

Scope revised at the user's request on 2026-09-21: phase 3 now targets worldwide
research planning with preserved New York controls. The authoritative execution
plan is [PHASE3_GLOBAL_RESEARCH_V2](phase3-research-plan-20260921.md), now
[PASS as of 2026-09-22](phase3-research-results-20260921.md). Earlier protocols and the original NOT_PASSED gate remain historical
evidence, not pending requirements for this revised research scope.

1. Source-backed global mesh, port coverage report and zero intersecting edges
   **relative to chosen coastline and discretization tolerance**. Report excluded
   components/ports, not just successful pairs. All covered ports must share a
   navigable component; report strong connectivity for directed overlays.
2. Directed search, hard closures/draft/unknown-depth rules, admissible cost policy,
   loopless K candidates, measured geographic separation and explicit rejection.
3. Validate the actual combined research artifact: complete segment/connector
   geometry, source-reference routes across oceans/dateline/hemispheres, preserved
   New York controls with no global bypass, honest per-route coverage/unknowns,
   bounded search and reproducible API/benchmark evidence. Preserve frozen regional
   AIS results within their measured scope. Worldwide AIS agreement, all-port access
   and globally verified permissions are not prerequisites for this revised gate;
   they remain unavailable claims. No invented canal shortcuts. Unit fixtures alone
   cannot satisfy this gate. See the linked acceptance matrix; **the revised gate
   must pass before proceeding to later phase deliverables.**
4. Versioned forecast/vessel models and temporal search. FIFO needs proof or use
   time-expanded labels; a single label per node is not generally valid for dynamic
   non-FIFO cost. Missing fuel curve -> no precise consumption/emissions.
   **Research scope PASS on 2026-09-22**: original ECMWF waves/winds/currents,
   user-declared vessel coefficients and time-expanded search with acknowledged
   holding assumptions. See [phase 4 results](phase4-results-20260922.md) and
   [its API/model contract](phase4-api.md). Coastal forecast gaps and uncalibrated
   vessel behavior remain explicit; no operational ETA or fuel claim.
5. Versioned paraphrased all-11 Incoterms 2020 responsibilities with named place,
   sale versus carriage, payer versus risk bearer versus actual routing control;
   sourced/user-supplied fees and cargo models, exposure by buyer/seller separately.
   **Research scope PASS**: [phase 5 results](phase5-results.md), 60 Java tests,
   provenance-required declared costs and separate conditional actor losses.
   Ordinary performance only; policy recovery and probabilities remain unknown.
   The legacy fixed-rate calculator is not part of this new API or its evidence.
6. Validated scenario distributions/correlation and deterministic seeded simulation,
   expected metrics, VaR/CVaR confidence and tail convention, Pareto frontier without
   arbitrary hidden weights; never derive probabilities from arbitrary constants.
   **User-selected declared-scenario research scope PASS**: [phase 6 results](phase6-results.md),
   80 Java tests and 17 closure checks. Joint probabilities/dependence are explicit
   user inputs, mathematically validated but not empirically calibrated. Exact
   finite-support reference, seeded common draws, sampling bounds and Pareto
   preserve that distinction; real-world distribution calibration is not claimed.
7. Versioned route/comparison/provenance/explanation/recalculation APIs with missing
   data, assumptions, temporal coverage, actor breakdown and recommendation basis.
   **Integrated research scope PASS**: [phase 7 results](phase7-results.md),
   89 Java tests and 16 closure checks. The isolated `mapping/planning` facade
   calls the shared weather controller and the frozen contractual/simulation
   engines, verifies complete declared leg mappings and derives scenario hours
   from actual route duration plus explicit additional times. Configured local
   storage keeps immutable revisions with hashes, provenance and changes.
   A real ocean-node closure reroutes both alternatives without modifying the
   parent. Public deployment, owner authorization and calibrated commercial
   probabilities remain outside this research scope; see [API](phase7-api.md).

## 5. Risks / non-goals

No free coastline guarantees all canals/harbours. Published depth is not a vessel
clearance guarantee (datum, tide, squat, survey age). Global mesh coverage differs
from global port accessibility and operational feasibility. Historical AIS cannot
prove present permission. Ice/closures/weather unknown cannot mean safe. Coarse
research routes must be labelled accordingly and never pass the operational gate.
The deliverable status and measured limitations are maintained in evidence.md;
later phases are not declared complete just because extension interfaces exist.
