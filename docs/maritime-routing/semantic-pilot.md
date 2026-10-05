# Regional semantic resolution: isolated design

Approved by the user on 2026-09-09. No global graph, production policy or endpoint
changes. The existing immutable NOAA physical artifact remains the baseline.

The offline semantic artifact separates original terminal references from curated
water-side endpoints. Their relationship is metadata, never a sailing edge across
land. Every water-to-mesh connector must pass the unchanged chart/depth mask.
Manual endpoints require independently supplied coordinate AND terminal-association
evidence, source receipts, a reason and reviewer. No nearest-water relocation.

Restriction classifications are ABSOLUTE_BLOCK, CONDITIONAL_RESTRICTION,
NAVIGABLE_WITH_RULES, INFORMATIONAL and UNRESOLVED_BLOCK. Classifications bind the
source ZIP hash, feature content hash and baseline graph version. Every permissive
interpretation needs review provenance, evidence, explicit effective interval and
regulatory citation. All intersecting restrictions apply; a permissive rule cannot
override another block. Unknown, expired or unsupported conditions fail closed.

Conditions are a bounded declarative conjunction of typed equality checks, not
executable expressions. Context values must carry USER_SUPPLIED or source-backed
provenance and an effective interval. A successful evaluation means only that the
documented scenario meets the encoded conditions; it does not authenticate a permit
or establish completeness of the legal model. Missing live applicability is not
silently replaced by false. No cost penalties are used to bypass restrictions.

Evidence review: the [2025 official edition of 33 CFR 165.169](https://www.govinfo.gov/content/pkg/CFR-2025-title33-vol2/pdf/CFR-2025-title33-vol2-sec165-169.pdf)
contains several separately scoped zones and conditional operations. It is NOT
accepted as proof of current 2026 applicability. The eCFR current page could not be
read through the available browser tool. A newer official NOAA publication was
subsequently acquired and checked locally (below). No broad NOAA polygon is reclassified as
INFORMATIONAL merely because it covers the harbour. NOAA source INFORM/SORDAT and
geometry are preserved in the review queue. The selected chart's shoreline lines
have no terminal names, and its only named shoreline-construction area is Old Pier 4;
these do not justify automatic associations to the 19 berth labels.

Acceptance: deterministic immutable artifacts; unchanged base topology unless
validated endpoint connectors are appended; original terminal coordinates retained;
geometry checks on every connector; rule/endpoint evidence hashes; counts by class,
unresolved restrictions, connected endpoints, accepted/rejected route requests;
synthetic positive and negative scenarios plus actual NOAA negative evidence.
No legal permission, vessel clearance, global validation or Incoterm claim.

## Implemented evidence and results

`acquire_noaa_semantics.py` downloaded the official [Coast Pilot 2](https://nauticalcharts.noaa.gov/publications/coast-pilot/files/cp2/CPB2_WEB.pdf).
The printed date **06 SEP 2026** was verified from the downloaded PDF, not guessed
from receipt time. PDF: 7,905,396 bytes, SHA
`4670561a2662ca82a9374f08e86a028ae5016150d39aab3bb0a010b977b9a2c8`.
Source, receipt and licensing notice remain ignored under
`data/maritime-routing/noaa-semantic-evidence-v1/`; nothing was redistributed.

`review_noaa_165169.py` pins those exact bytes and checks PDF page index 138,
paragraphs 3255-3256. Fourteen chart features are classified CONDITIONAL_RESTRICTION
using only the authorization branch in 33 CFR 165.169(b)(1)-(2). Conditions require
source-backed authorization and instruction-compliance evidence scoped to the
feature, vessel and voyage. None was supplied. Exceptions and accurate live zone
boundaries are not inferred from the broad chart polygon. This conservative branch
can reject passages that a different, not-yet-modeled exception might allow.

Four features remain UNRESOLVED_BLOCK (references 334.85, 165.172, 40 CFR 140,
165.165). Locating a regulation's text does not establish its full applicability.
The configurable 24-hour review window (allowed 1-168 hours) is an engineering
freshness policy, NOT a fabricated law effective/expiry date. A new publication
requires substantive review, not simply updating a hash. PDF extraction uses optional
`pypdf==6.18.0`, wheel hash pinned in `semantic-requirements.txt`.

`noaa-semantic-pilot-v2.zip` is a new isolated artifact, not a replacement graph:

- Version `c3d4e7793a587f97e4b52e88da3dbbe367618bbb7d626572c57b32d0232bc3b9`.
- ZIP SHA `06414d616a50738497331bd0cad4de44267594aff53290045c349d7e1f3d50cb`;
  995,555 bytes; second execution reproduced identical bytes.
- All 2,644 nodes / 14,072 directed base edges preserved; 7,036 reciprocal pairs
  rechecked at 12.5 m subdivision. Shared mask, not independent survey truth.
- 14,072 edges blocked by missing conditional evidence; 900 also intersect
  unresolved features. The class counts overlap and must not be summed.
- Zero curated endpoints. All 19 original terminals preserved. Ninety ordered
  requests among the first ten source terminal IDs rejected for unavailable
  approaches. These are early request rejections, not 90 successful graph searches.
- First execution 13.11 s; repeat 44.15 s under different machine load. Not a
  controlled speed comparison, p95, SLA or isolated memory benchmark.
- 39 Python tests pass: all five classes, conditional positive routes, overlaps,
  expiry, missing/changed evidence, mismatched zone/vessel/voyage, land endpoints,
  absent association evidence and preserved source coordinates.
- Full Maven suite rerun: 68 tests, zero failures/errors/skips, exit 0. Includes
  both global and old regional real-artifact integrations. No production Java edits.
  Latest runtime files were updated by these tests; earlier timings are historical.

## Candidate access review

`suggest_noaa_approaches.py` found 57 original chart vertices (three per terminal)
with clear water-to-mesh connections. These are **review candidates**, not approved
approaches or inferred terminal associations. No coordinates were shifted or snapped.
The 500 m search radius is the existing engineering limit, not proof of access.

`verify_noaa_candidates.py` walked the exact polygon/ring/vertex references and
checked all 57 candidates / 535 connectors at 6.25 m subdivision: coordinate and
geometry PASS; association NOT_VERIFIED; activated endpoints zero. Source attributes,
hashes, restrictions and original terminal coordinates remain available for review.
This is a separate validator sharing the chart mask, not another hydrographic source.

Reports: `noaa-approach-candidates-v1.json` (69.56 s), review hash
`a86eb5a96513fd9cac8d10bfb176fa14d4798c6aaa3ef7c525d49551baeb3791`;
optimized v2 (22.19 s), hash
`a130eaf16405d53f6e013d79bf6119ee1a715f216bd41c8ed139355c494892a4`.
Optimization only avoids densifying geodesics to sort distances; actual connectors
still receive full geometric checks. Candidate proximity does NOT establish which
terminal a point serves. The remaining selection requires association evidence.

## Offline input contracts

- Curation: schemaVersion, sourceSha256, baseGraphVersion, rules, endpoints.
- Reviewed rule: featureId/hash, classification, reviewer, reviewedAt, reason,
  validFrom/Until, sourceIds, regulatoryCitation, regulatoryEvidence.
- Evidence reference: sourceId, document locator and explanation; source receipts
  are loaded with --evidence-receipt and their local file hashes are checked.
- Conditions: key, equals, optional requiredSource (SOURCE_DATA or USER_SUPPLIED)
  and requiredScope (PASSAGE). No executable expressions. Boolean true != numeric 1.
- Context: at, facts, vesselId/voyageId when needed, up to 100 explicit requests.
  Facts require value, source, providedBy/At and validFrom/Until. SOURCE_DATA also
  needs sourceId, matching evidenceSha256 and locator. PASSAGE requires the exact
  featureId/vesselId/voyageId tuple. These establish traceability, not authenticity.
- Endpoint: id, terminalId, WGS84 point, reviewer/reason/review interval/sourceIds,
  coordinateEvidence AND associationEvidence. Being in water is not association
  evidence. The terminal link is metadata, not a navigable land-to-water edge.
- Requests identify two endpoint IDs or two terminal IDs. Missing approaches fail;
  multiple eligible approaches require an explicit endpoint selection. The isolated
  engine uses bounded distance-only Dijkstra, not the global Java K-route API.

The semantic artifact stores terminals/endpoints separately, source/curation/context,
review queue, physical graph, route results, metrics and reproducibility hashes.
Its purpose ISOLATED_SEMANTIC_RESEARCH_NOT_NAVIGATION prevents production loading.

## Reproduction

Use new output names; no overwrites. Frozen context at 2026-09-09T20:02:45Z is a
data-availability audit, not an asserted departure date or refreshed authorization.

```powershell
.venv-maritime/Scripts/python -m pip install --require-hashes --only-binary=:all: -r scripts/maritime/semantic-requirements.txt
.venv-maritime/Scripts/python scripts/maritime/semantic_pilot.py --artifact data/maritime-routing/noaa-new-york-pilot-v1.zip --receipt data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json --curation data/maritime-routing/noaa-semantic-review-v2/curation-reviewed.json --context data/maritime-routing/noaa-semantic-review-v2/context.json --evidence-receipt data/maritime-routing/noaa-semantic-evidence-v1/coast-pilot-2.pdf.receipt.json --output data/maritime-routing/noaa-semantic-pilot-new.zip
.venv-maritime/Scripts/python scripts/maritime/suggest_noaa_approaches.py --artifact data/maritime-routing/noaa-new-york-pilot-v1.zip --receipt data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json --output data/maritime-routing/noaa-approach-candidates-new.json
.venv-maritime/Scripts/python scripts/maritime/verify_noaa_candidates.py --report data/maritime-routing/noaa-approach-candidates-new.json --artifact data/maritime-routing/noaa-new-york-pilot-v1.zip --receipt data/maritime-routing/noaa-new-york-v1/enc-source.zip.receipt.json --output data/maritime-routing/noaa-approach-candidates-new.validation.json
```

For a fresh review: `semantic_pilot.py --prepare-review --at <explicit timestamp>`
plus --artifact/--receipt/--output creates an unresolved registry and terminal queue.
`review_noaa_165169.py` takes --curation, --chart-receipt, --publication-receipt,
--output, --at and optional --review-hours. It refuses changed source bytes and
already reviewed entries. `acquire_noaa_semantics.py --output <new directory>`
downloads only the fixed official NOAA document and license notice, with byte limits.
