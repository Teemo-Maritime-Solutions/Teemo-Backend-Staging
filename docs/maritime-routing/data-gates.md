# Data gates: what is available and what is still missing

Current scope: [PHASE3_GLOBAL_RESEARCH_V2](phase3-research-plan-20260921.md), authorized
by the user on 2026-09-21. Worldwide geometric research planning with preserved
New York controls is now implemented and its revised gate
[passed on 2026-09-22](phase3-research-results-20260921.md). Global operational coverage is not the revised phase
3 acceptance criterion; the missing layers below still limit what can be claimed.

The user has no licensed AIS or surveyed approach files. This is not permission
to invent observations or to assume that all such data require payment.

## Available without user credentials

- Panama Canal classic-lock research centerline and water polygons from a pinned
  2026-09-22 OSM extract, integrated and checked as documented in
  [Panama research](panama-research-20260923.md). Community mapping, not official
  hydrographic clearance; expanded locks, vessel compatibility, permissions and
  lock transit times remain unavailable claims.

- ECMWF IFS original public forecast run 2026-09-22 00 UTC, 29 six-hour frames to
  +168 h: waves, wind and surface currents at 0.25 degrees. Versioned and validated
  in [phase 4](phase4-results-20260922.md); 729/1,114 connected port points have all
  required weather fields, without filling the remaining coastal/missing cells.
- Global GSHHG 2.3.7 coastline and original UN/LOCODE 2025-1 references, with receipts.
- Natural Earth 5.1.1 map units for coarse coordinate-country quarantine.
- Regional historical NOAA AIS: 2024-01-01 diagnostic and a separately registered
  February temporal holdout; March now development evidence; April downloaded
  only after freezing its independent comparison protocol and implementation;
  May and June acquired after freezing a separate March-trained directional model;
  July/August acquired after freezing the separate prospective transit experiment.
- September/October acquired after a separate 27-file coherent O/D registration.
  Both dates pass the unchanged regional transit criterion. March–August are now
  development sources for this new model; all historical failed reports remain.
- Official UKHO Ships Routeing Measures: 217 source features with original
  responses, identifiers and OGL metadata. Isolated research coverage audit only;
  the publisher states these are unsuitable for navigation/navigational products.
  Public MCA Dover, MPA Singapore and Suez index/guidance snapshots also retained.
- [NOAA ENC Direct to GIS](https://nauticalcharts.noaa.gov/learn/encdirect/): pinned
  original 30-layer and expanded 40-layer snapshots of two New York Harbour cells,
  imported only into isolated research pilots. The converted GIS product is not
  certified for navigation. DRGARE water and bridge/support geometry are now
  separated in a versioned model; unknown clearance remains unknown.
- USACE: 58 facility reference points and 16 network links with source receipts;
  these are not verified terminal water approaches. NAVCEN safety-zone acquisition
  is incomplete (published line file returned 404). No absence-of-restriction claim.

Do not equate these with a globally verified operational network. AIS is reception-
biased and historical; a traversed segment does not establish present permission,
safe vessel clearance, cargo suitability or authority to use a route.

## Remaining inputs and acceptance evidence by scope

| Layer | Minimum evidence needed | Current state |
| --- | --- | --- |
| Port access | Official port/terminal identifier, water approach geometry, relation to the source reference, coordinate datum/precision, validity, licence and original source feature IDs | UN/LOCODE, NOAA berth and USACE references available; no approved terminal approaches, on-land points unchanged |
| Channels/straits | Sourced geometry, permitted direction, vessel/size/draft restrictions, controlling authority, effective dates and applicable closures | New UKHO research reference geometries available, not imported: 51/60 lanes have no global-mesh node (Dover 9/12). No complete expected-passage validation, global operational input or Suez/Cape clearance |
| Depth | Surveyed/charted depth coverage, vertical datum, resolution, survey age, uncertainty, applicable tide/clearance model | Regional DEPARE/DRGARE attributes retained; current vessel clearance remains unknown and draft-constrained routing rejects it |
| Observed corridors | Licensed time-ordered positions, documented gap/outlier filters, vessel population and dates; training split separate from validation | New coherent O/D model uses 160 original transit tracks from six declared dates; five vessel-group development checks retain three failures. Earlier March-only model unchanged; neither model clears the physical/permission gate |
| Validation | Separate geometry/runtime research criteria and observed-voyage accuracy criteria | Revised global research gate PASS with nine registered world cases, full geometry accounting and regional controls. September/October holdouts PASS only their regional target. Historical failed dates remain; worldwide operational and independent AIS accuracy are still unverified |
| Weather/vessel research and operational optimization | Forecast run/valid times, winds/waves/currents, vessel performance curve, departure and operational bounds | Phase 4 research PASS with original public ECMWF data and a user-selected parametric vessel model. No calibrated vessel curve, fuel/emissions estimate, all-port weather coverage or operational optimization approval |
| Contractual exposure | Versioned responsibility matrix plus named place, actual carriage control, source/user tariffs, policies and calibrated risk distributions | Gated; legacy arbitrary percentage outputs disabled |

No coordinate corrections by nearest city/name matching. USER_SUPPLIED may be
accepted explicitly in a future approach importer, but a user's assertion alone
must not be relabelled as hydrographic authority or a verified approach.

## Access checks already exhausted for the selected WPI endpoints

The official CSV returned HTTP 403. Both the previous viewer and the alternative
`vcps.nga.mil/.../WPI/World_Port_Index_Viewer/MapServer` failed TLS trust locally
after approved network access. Web retrieval of the latter also timed out. No TLS
bypass, system trust-store edit, credentials request or unofficial mirror used.
Resolving that trust chain requires a separately justified trust/configuration
decision, or an accessible official distribution. Availability of WPI still would
not certify berths or canal permissions by itself.

## Scope decision and preserved experiments

The user has now authorized the research scope change described in the linked
plan. No additional approval is needed to plan around geometric worldwide coverage
while preserving New York controls. Missing complete global permissions/approaches
and AIS no longer block this research gate. They still preclude worldwide
operational or observed-route accuracy claims. Existing frozen criteria/results
are not relaxed or relabelled: the new scope gets a separate acceptance report.

The user already selected origin/destination transit prediction. The latest
[experiment and audit](od-results-20260921.md) passes two fresh regional dates with
a separate coherent O/D model, while finding concrete global lane-representation
gaps. Do not ask again whether to reconstruct near-returning curves. Any further
model/cohort change needs a new version and untouched evaluation; prior failed
results stay visible. September/October must not be called untouched after they
inform a future change. Additional New York tuning is not the remaining global gate.
Any later scope change must be explicit; discarding inconvenient cases or changing
a frozen experiment's acceptance is not an implementation-only fix.

Additional public source checks: DMA identifies free historical AIS, but its
archive index timed out in the web reader and approved CLI TLS handshake. LINZ's
ENC download page was accessible through the CLI, while use requires S-63 permits
and registration; no registration, licence acceptance or third-party contact was
performed. These checks do not establish that all global data are paid/unavailable.

The user approved the US regional ENC/AIS and semantic pilots; those are already
implemented with open sources, not pending approval. Their evidence must not be
sold as completion of global validation. A worldwide
operational promise requires coverage from relevant hydrographic/channel authorities
and suitable validation data. Keep the global mesh research-only while those inputs
are missing. No public release, purchase or third-party coordination is authorized
implicitly by this document.
