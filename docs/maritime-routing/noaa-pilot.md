# NOAA regional approach pilot

Continuation: the approved isolated semantic layer, reviewed regulation branches,
water-side candidate evidence and latest test results are in
[semantic-pilot.md](semantic-pilot.md). The physical artifact documented here remains
unchanged; a conditional interpretation does not retroactively activate this graph.

The user approved this regional milestone after the global research mesh/AIS
comparison. It does not replace that mesh, change legacy endpoint contracts, or
pass the global phase-3 gate. No graph activation, publication or DB mutation.

## Source decision, before implementation

Use [NOAA ENC Direct](https://nauticalcharts.noaa.gov/learn/encdirect/) Harbour
layers, with active band-5 charts selected by catalogue title containing
`New York Harbor`, then joined to GIS coverage by exact DSNM. The live GIS coverage
service returned null TITLE values; catalogue selection replaces the initial
unsuccessful title query. Catalogue issue dates are not asserted to be GIS editions.
This is a geographically motivated pilot, not selected to maximize AIS success.
Coordinates/bounds and cell identifiers must come from the coverage service;
no handwritten bounding box, guessed berths or relocated UN/LOCODE references.
If no matching coverage exists, fail and reassess the source selection explicitly.

[ENC terms](https://charts.noaa.gov/ENCs/ENC_Agreement.shtml) permit downloads/use
and redistribution subject to the stated conditions; redistributed/modified products
are not official ENCs. Keep origin attribution and the agreement with snapshots;
do not redistribute from this repository. The [OCS licensing policy](https://nauticalcharts.noaa.gov/data/data-licensing.html)
does not justify silently relabelling every externally contributed source as CC0.
ENC Direct's GIS conversion is explicitly NOT certified for navigation.

The selected service is updated weekly, but individual SORDAT/survey ages may be
older or absent. Record original attributes, layer metadata, queries, request times,
responses and hashes. A live ArcGIS service is not a transactional snapshot:
check feature ID membership before/after downloads and reject pagination loss;
record residual within-acquisition update uncertainty. Do not infer survey date
from retrieval time or ArcGIS software version.

Rejected for physical navigability: NOAA's historical port-delineation polygons
contain intentional inland buffers and old AIS-derived boundaries. They are not
water or berth certificates. S-57 native cells remain a possible future alternative;
using the documented GIS service now avoids inventing an S-57 parser or dropping
unrecognized attributes during conversion.

## Pipeline and acceptance

1. Acquire one source-selected Harbour-scale chart set and all registered physical,
   hazard, depth, quality, datum, berth and route layers. Unknown/missing schema or
   incomplete queries fail. Immutable receipts and ZIP, ignored by Git.
2. Normalize source features without relocating them. Preserve DSNM, the layer's
   declared OID (not necessarily OBJECTID),
   SORDAT, SORIND and raw restriction/depth attributes. An unknown name remains
   unnamed, not a generated real-world place name.
3. Build a bounded regional research mesh inside declared chart coverage and
   source water/depth polygons. Land/structures/hazards are geometry exclusions.
   Preserve unresolved regulatory intersections on physical edges as hard search
   exclusions (including research mode), not as invented permanent land or a
   permission. No GSHHG carve-outs or global topology replacement.
4. Preserve charted depth as chart evidence, not tide-adjusted safe water depth.
   Missing datum/quality/coverage blocks vessel-clearance claims. Recommended
   tracks and pilot boarding places do not imply legal permission or a berth.
   Source direction semantics require verified IHO rules; unknown direction must
   not become an asserted permitted direction.
5. Source-derived berth/approach endpoints, bounded geodesic connectors and
   explicit unavailable statuses. Validate all edges against the regional mask,
   source coverage and geometry; show excluded features and disconnected cases.
6. Synthetic negative tests, actual-source routes, reproducible resource metrics.
   January/February AIS are already development evidence; new tuning requires a
   new untouched date for evaluation. Current charts vs historical AIS are not a
   contemporaneous safety validation. No contractual optimization in this pilot.

Engineering choices (mesh spacing, connector radius, geometry tolerance, resource
ceilings) must be configurable and never described as observed vessel limits.
The global mesh and existing version-pinned artifacts remain untouched.

## Acquisition command

```powershell
.venv-maritime/Scripts/python scripts/maritime/enc_snapshot.py --title "New York Harbor" --output data/maritime-routing/noaa-new-york-v1
```

Network approval may be required. HTTPS certificate validation stays enabled;
fixed NOAA hosts only, no credentials or unauthenticated mutation endpoints.
Feature batches default to 50 (validated range 1-50) to bound GET query size.
The earlier 200-ID query failed HTTP404 for layer 85; the smaller batches acquired
all 940 features and passed exact-ID checks. That layer's declared OID is
`HARBOUR.SLCONS_LINE.FID`. Successful empty ID responses use `objectIds: null`.
Coverage CATCOV is returned as the decoded string `coverage available`, not numeric
1 in this snapshot. Unknown labels never imply available coverage.

## Implemented experiment: 2026-09-09

Source acquisition succeeded: 30 layers, two active catalogue-selected cells
`US5NYCCF.000` and `US5NYCDF.000`, 1,515,669-byte source ZIP. Hash:
`55f5fb6293bd5cb64844ba7ad761b64e291ef7b4ad8bf0264168d76a156939cf`.
All raw requests/responses, catalogue and agreement are archived with the receipt
under ignored `data/maritime-routing/noaa-new-york-v1/`. No redistribution in Git.

`build_enc_pilot.py` implements source-bounded H3 resolution 10, neighboring-cell
connections, geodesic subdivision and original-coordinate berth connectors.
Its local WGS84 azimuthal equidistant projection is centered on source coverage;
the regional profile rejects extents over two degrees, antimeridian-spanning and
polar coverage. It is not the global mesh's projection/antimeridian implementation.
The engineering profile is `noaa-pilot-config.json`: 25 m geodesic steps, 1 m
geometry tolerance, zero additional coastal buffer, 500 m connector ceiling,
24 candidates, at most three connections per berth, 100,000-node budget.
These are engineering choices, not vessel limits or survey accuracy.

Positive known DEPARE supplies water. Unknown/nonpositive depths, land,
construction, obstructions, wrecks, bridges, gates and unsurveyed areas exclude
geometry. Dredged areas alone do not replace DEPARE coverage. Invalid geometries
would exclude their full envelope, without repair; none occurred in this snapshot.
DRVAL1/DRVAL2 are preserved as chart attributes, with original datum/quality fields,
and **minimumDepthM remains null**. See [IHO's depth-area encoding rules](https://iho.int/iho_pubs/standard/S-57Ed3.1/S-57%20Appendix%20B.1%20Annex%20A%20UOC%20Edition%204.1.0_Jan18_EN.pdf).

The first strict-mask attempt produced no water: the source's regulatory polygons
cover the full selected area. The corrected physical/restriction separation follows
the original project architecture: source feature IDs are carried in edge
`restrictions`, and the existing Java policy rejects every unresolved restriction.
No bypass flag or automatic legal interpretation was added. The charts reference
regulations including 33 CFR 165.169; live applicability/permissions have NOT been
resolved. A regulated polygon is not asserted to be permanent physical land or an
unconditional legal ban. Source labels and regulatory references remain verbatim.

### Measured results (negative routing evidence)

- 2,644 nodes, 14,072 directed edges, 36 strong components, largest 2,033 nodes.
- 19 source berth references; **all 19 intersect NOAA's own Land_Area**.
  No reference relocated or joined through land. Distances to conservative water
  are diagnostic only (13.97-219.30 m), not approved connector lengths.
- All 14,072 edges intersect unresolved regulatory features and remain blocked
  during search, even with research acknowledgement.
- Artifact `noaa-new-york-pilot-v1.zip`: 1,617,566 bytes; build 19.77 seconds.
- Version `85299d8710b6f85ae3ddd4b2c4166070976fb1d117290702f62c2721a889e739`.
- ZIP SHA `368c2fb8a91010b22c974aa059e813626cc4375c54b992c309d04ebd6970462a`.
- A second build (21.65 seconds) reproduced the identical version and ZIP SHA.
- `validate_enc_pilot.py` checked all 7,036 reciprocal pairs at 12.5 m subdivision,
  source attributes/coordinates, restrictions, depth evidence, table hashes and SCCs:
  geometry PASS, **routing/global gate NOT_PASSED**, zero connected berths, 12.60 s.
  It recomputes from the pinned source but shares the mask implementation; this is
  not independent hydrographic truth or held-out AIS validation.
- Python: 31 tests pass, including synthetic water/land/unknown-depth, registry,
  bounded download, coordinate preservation and altered reciprocal-evidence failures.
- Full Maven suite, with both real global and NOAA artifacts enabled: **68 tests,
  zero failures/errors/skips**, exit 0. This compiles the existing backend and the
  new `NoaaPilotArtifactIntegrationTest`; no production Java code changed in this pilot.
- Java loaded the regional artifact in 1,018.42 ms, rejected adjacent-node routing
  with `UNRESOLVED_HARD_RESTRICTION` and rejected GraphCatalog activation.
  Report: `target/maritime-routing/noaa-pilot-<graphVersion>.json`.
  The 786,588,960-byte heap-used reading is from the shared full-suite JVM, not
  isolated pilot memory or peak RSS; heap cap 1,258,291,200 bytes.
- Global regression still passed eight actual-source international pairs and three
  alternatives. Latest old-graph load 6,646.24 ms; routes 28.82-399.39 ms;
  alternatives 1,130.17 ms. This rerun updates the old-version benchmark file;
  earlier measurements in evidence.md remain historical, not the latest file values.

### Reproduce without network

Use new output names; builders and validators refuse to overwrite artifacts/reports.

```powershell
.venv-maritime/Scripts/python scripts/maritime/build_enc_pilot.py --receipt data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json --config scripts/maritime/noaa-pilot-config.json --output data/maritime-routing/noaa-new-york-pilot-new.zip
.venv-maritime/Scripts/python scripts/maritime/validate_enc_pilot.py --artifact data/maritime-routing/noaa-new-york-pilot-new.zip --receipt data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json --output data/maritime-routing/noaa-new-york-pilot-new.validation.json
```

The generic Java artifact reader can inspect this profile. GraphCatalog deliberately
does NOT accept this regional pilot for API activation; there is no fake country QA
or exemption added. Production source/API configuration remains unchanged.

### Next data/semantic gate

A chart's berth label placed on a pier is not a water-side vessel approach point.
Need authoritative water-side access geometry and its association to the terminal,
plus time/vessel-dependent regulatory applicability. Do not move source berth labels
to the nearest water cell or treat missing live restrictions as permission.
The archived active NY catalogue was also checked for band-6 berthing alternatives:
its two cells are Sackets Harbor and Barcelona Harbor, not New York Harbor.
Thus no higher-scale New York berth cell was available in this captured catalogue;
this is not a claim that no other official provider has suitable access data.
If the next milestone instead models an administrative terminal linked to a distinct
water-side endpoint, document that semantic migration before changing the API.
No new paid-data demand, global PASS, Incoterm optimization or novelty claim follows
from this pilot. It establishes a reproducible, tested diagnosis of missing data.
