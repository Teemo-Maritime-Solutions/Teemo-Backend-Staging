# Source-model correction: development experiment, 2026-09-14

March 2024 AIS has already been inspected. Every reuse is DEVELOPMENT, not an
untouched holdout. Preserve the original March protocol and reports. No global
phase-3 acceptance, operational passage, terminal access or phase-4 activation
follows from this experiment.

## Source interpretation

IHO S-57 Use of the Object Catalogue edition 4.4.0 (October 2024), sections 4.8.2
and 5.5, identifies DRGARE as Group-1 area and separates bridge water geometry
from bridge decks and PYLONS supports. The old DEPARE-only intersection incorrectly
removed dredged water. Treating every bridge deck as solid also removed underlying
water. Neither correction establishes current depth or vessel clearance.

Primary standard:
https://iho.int/uploads/user/pubs/standards/s-57/S-57%20Appendix%20B.1%20Annex%20A_Ed%204.4.0_FINAL.pdf

Published NOAA feature registry:
https://encdirect.noaa.gov/arcgis/rest/services/encdirect/enc_harbour/MapServer

## Versioned model and controls

- Keep DEPARE_ONLY_V1 and archived sources/artifacts available for replay.
- S57_GROUP1_V2 uses positive finite DEPARE and DRGARE polygons inside declared
  CATCOV. Unknown/nonpositive/inconsistent depths, land and hazards still exclude.
- Require a new complete 40-layer NOAA snapshot of exactly the same two chart
  cells, adding point/area bridge supports, piles, point land/shore/gates/hulks,
  floating docks, hulk areas and caution areas. This is still a limited GIS
  research source, not a complete navigational ENC product.
- Only fixed/suspension bridges with finite positive VERCLR become overhead
  conditions. Retain original feature IDs, geometry and all attributes as
  unresolved restrictions. No deduction of usable height from tide, datum,
  maintenance reduction, chart age or vessel dimensions. Unknown/opening/mixed
  bridges remain hard geometry exclusions. Support geometry remains solid.
- Use distinct NOAA_ENC_REGIONAL_V2 profile, rejected by the existing semantic
  profile reader. The Java catalog separately requires portCountryQa, which this
  artifact lacks. Nothing is configured, activated or connected globally.
- Keep H3 resolution, tolerance, step, AIS cohort selection, denominator, metric
  sampling and the original >=80% measurable / <=1 km error targets unchanged.
- Run original March cases on V2 as development evidence, then diagnose all
  remaining conflicts without moving or deleting observations. A future untouched
  holdout requires a separate prospective frozen protocol after development ends.

Source dates can differ between newly acquired and archived snapshots; report
feature/geometry differences before attributing improvement solely to code.
