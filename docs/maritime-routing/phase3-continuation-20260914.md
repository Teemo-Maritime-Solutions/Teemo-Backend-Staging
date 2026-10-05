# Phase 3 continuation - 2026-09-14

The user requested completion of phase 3. The existing global acceptance gate in
architecture.md remains in force; a successful regional diagnostic cannot replace
global evidence or authorize phases 4-6. No external coordination, purchases or
deployment are required for the public-source work below.

## New source investigation

The official [USACE WCSC infrastructure catalogue](https://www.iwr.usace.army.mil/About/Technical-Centers/WCSC-Waterborne-Commerce-Statistics-Center/WCSC-Navigation-Infrastructure/)
links Navigation Facilities (Dock), item `23d91bd988ac4fc9943128965bddfa37`, and
Waterway Networks, item `ace7645d305647448a84492a3b909d48`. The item owner
`usace_iwr_cac`, exact service URLs, metadata, selection envelope derived from the
existing NOAA coverage, feature membership and original responses are verified.
The index itself returned HTTP 403 to the downloader (also after approved access);
its links were verified in the web reader. This does not prevent downloading the
official linked ArcGIS data and original licence metadata.

USACE explicitly warns that dock coordinates were developed at national scale and
may not suit local mapping. A reference point with a clear water connector is
therefore a review candidate. Several records refer to 1998 surveys; publication or
retrieval in 2026 does not renew those surveys. TOWS statistical references,
anchorages and terminal child records must remain distinguishable.

The [USCG NAVCEN MSI page](https://www.navcen.uscg.gov/msi) publishes safety-zone
GeoJSON files. The line-file link returns HTTP 404; retain the missing-file status.
Publication status `Approved` does not authorize a vessel. Even a complete set of
these files would not prove that permanent, broadcast or conditional restrictions
are absent. They cannot clear the broad NOAA 165.169 polygons automatically.

## Frozen regional AIS experiment - BEFORE acquisition/outcome inspection

Purpose: determine how the unchanged NOAA physical mesh fits previously unexamined
regional observations. Diagnose physical geometry separately from unresolved
terminal-access and passage requirements. Actual route APIs retain all restrictions.

- Source: NOAA 2024-03-01 UTC Zstandard AIS distribution, same official collection
  as the existing January/February datasets. New date, never used for graph tuning.
- Fixed baseline: `noaa-new-york-pilot-v1.zip`, source-selected New York Harbor
  charts. No topology or exclusion-mask tuning during evaluation.
- Cohort: cargo/tanker MMSIs with at least one valid observation inside the chart
  coverage envelope; at most 64 vessels by SHA256 MMSI before geometric outcomes.
- Time-ordered in-envelope records are split at gaps above 600 seconds, jumps
  above 5 km, 30 km accumulated track length or 500 observations. Conflicting
  simultaneous positions are quarantined. Minimum segment length 1 km and three
  observations. The 20 smallest source/vessel/segment hashes form the fixed cases.
- Check every observed segment against the conservative chart water domain.
  Out-of-water tracks, missing anchors, disconnected anchors and resource failures
  remain in the denominator. No replacement of failed cases.
- Both endpoint anchors: at most 24 original mesh nodes within 500 m, each connector
  checked at 6.25 m geodesic subdivision. Directed distance-only Dijkstra examines
  all accepted anchors. Search examines physical topology as a diagnostic; results
  carry `PASSAGE_NOT_EVALUATED` and cannot become operational routes.
- Entire predicted paths rechecked at 6.25 m. Hausdorff/Frechet use 100 m geodesic
  resampling with at most 1000 samples per curve. Report raw errors, sample sizes,
  rejected cases and distance ratios.
- Prospective **regional physical-model** acceptance: at least 10 selected cases,
  at least 80% measurable, all measured cases with both discrete errors <= 1000 m,
  zero predicted chart-water conflicts. These are explicit engineering targets for
  kilometre-scale planning, not shipping safety standards. Their selection predates
  acquisition and may not be relaxed after seeing outcomes.
- Timing, process peak working set on Windows, dependency/code/config hashes and
  data lineage accompany the report. One day is not globally representative.
- The chart snapshot is from 2026 while observations are historical 2024. A mismatch
  may reflect changed conditions, observation errors or model error; this diagnostic
  does not resolve which cause applies, nor establish historical legal passage.

If this diagnostic fails, record the failure and use a distinct future holdout after
any corrective experiment. If it passes, only this regional physical-model
criterion passes; global phase 3 still requires the remaining original evidence.
