# Offline build and research activation

## Combined global and New York research artifact

The new pipeline uses separate files; no frozen AIS implementation is edited.
Accepted research artifact: `global-regional-research-v4-20260921.zip`, closure
PASS on 2026-09-22; see [results](phase3-research-results-20260921.md). Versions v1 and v2
are development builds with no regional attachment. Version v3 has attachment but
was superseded after a long-geodesic filter regression was found. Do not activate
these development versions. The v3 engineering protocol keeps the original nine
O/D cases and registers both corrections before evaluating the v4 candidate.

```powershell
.venv-maritime/Scripts/python scripts/maritime/build_combined_research.py --global-artifact data/maritime-routing/global-coastal-r4-audited-v1.zip --regional-artifact data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip --chart-receipt data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --global-validation data/maritime-routing/global-coastal-r4-audited-v1.validation.json --output data/maritime-routing/global-regional-research-new.zip
.venv-maritime/Scripts/python scripts/maritime/validate_combined_research.py --artifact data/maritime-routing/global-regional-research-new.zip --global-artifact data/maritime-routing/global-coastal-r4-audited-v1.zip --regional-artifact data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip --chart-receipt data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --global-validation data/maritime-routing/global-coastal-r4-audited-v1.validation.json --output data/maritime-routing/global-regional-research-new.validation.json
```

Choose fresh output names; scripts reject overwrites. Validation exhaustively
matches retained global records to their previously validated geometry, rechecks
regional geometry/restrictions and new transition edges, checks reciprocity and
recomputes research-policy connectivity for all connected reference ports. Inherited
coast checks are explicitly reported, not presented as a second hydrographic survey.

After the GIS process finishes, run Java integration separately with a bounded heap:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx1800m" "-Dtest=GlobalArtifactIntegrationTest,GraphArtifactLoaderTest,MaritimeRoutingControllerTest,RouteSearchTest" "-Dmaritime.max-entry-bytes=1000000000" "-Dmaritime.graph=data/maritime-routing/global-regional-research-v4-20260921.zip" "-Dmaritime.validation=data/maritime-routing/global-regional-research-v4-20260921.validation.json" "-Dmaritime.protocol=docs/maritime-routing/phase3-research-protocol-v3-20260921.json" "-Dmaritime.report-directory=target/maritime-routing/combined-20260921" test
```

The engineering registration binds source IDs, input hashes and implementation.
If code changes, create a new registration and keep previous results. These are
known regression cases, not independent AIS holdouts. Verify results with
`summarize_research_phase3.py`; the original `phase3-status-20260921.json` is never
overwritten. Full Python suite and original experiment hash checks remain required.
No environment/production artifact selection is changed by these commands.

Consolidate the registered evidence to a new report path:

```powershell
.venv-maritime/Scripts/python scripts/maritime/summarize_research_phase3.py --protocol docs/maritime-routing/phase3-research-protocol-v3-20260921.json --validation data/maritime-routing/global-regional-research-v4-20260921.validation.json --runtime target/maritime-routing/combined-20260921/runtime-benchmark.json --python-log target/maritime-phase3-python-tests-v2.log --output data/maritime-routing/phase3-research-status-new.json
```

The accepted report is `phase3-research-status-v2-20260922.json`. Manifest
`report.validationGate` is the immutable build-time state; subsequent validation
and phase closure are separate hash-bound evidence files, not retroactive changes
to the artifact. Activating schema 2 still needs its pinned digest and matching
validation report. Phase closure itself does not change production configuration.

Python 3.11, Java 17 and Maven were used. No commands below modify MongoDB, deploy
production or create external accounts. All paths are repository-relative. Keep
datasets, receipts, environment and artifacts out of Git. Preserve snapshots;
choose new output names when a previous run already produced evidence.

## Acquisition

```powershell
python -m venv .venv-maritime
.venv-maritime/Scripts/python -m pip install -r scripts/maritime/requirements.txt
.venv-maritime/Scripts/python scripts/maritime/acquire.py gshhg --output data/maritime-routing/snapshot-20260908
.venv-maritime/Scripts/python scripts/maritime/acquire.py unlocode --output data/maritime-routing/snapshot-20260908
.venv-maritime/Scripts/python scripts/maritime/acquire.py country-qa --output data/maritime-routing/snapshot-20260908
```

Network permission may be required. TLS is always verified. WPI was evaluated but
could not be downloaded; actual UN/LOCODE records are used instead. No handwritten
port list. Receipts record licence, source/version, acquisition UTC, units, coverage,
limitations and exact bytes/SHA-256. Fresh acquisition timestamps change provenance
and thus graph identity even if upstream bytes are identical.

## Build, audit and validate

These are new output names, not claims that those files already exist:

```powershell
.venv-maritime/Scripts/python scripts/maritime/build_graph.py --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --ports-receipt data/maritime-routing/snapshot-20260908/unlocode-2025-1.zip.receipt.json --config scripts/maritime/full-coast-config.json --output data/maritime-routing/rebuild-full.zip
.venv-maritime/Scripts/python scripts/maritime/audit_ports.py --artifact data/maritime-routing/rebuild-full.zip --country-receipt data/maritime-routing/snapshot-20260908/ne_10m_admin_0_map_units-5.1.1.zip.receipt.json --max-country-distance-m 100000 --output data/maritime-routing/rebuild-audited.zip
.venv-maritime/Scripts/python scripts/maritime/validate_graph.py --artifact data/maritime-routing/rebuild-audited.zip --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --output data/maritime-routing/rebuild-audited.validation.json
.venv-maritime/Scripts/python scripts/maritime/validate_connectivity.py --artifact data/maritime-routing/rebuild-audited.zip --output data/maritime-routing/rebuild-audited.connectivity.json
```

`full-coast-config.json`: H3 levels 2/3/5, full GSHHG, 500 m geodesic sampling,
0.00001 degree conservative tolerance, explicit 0 m extra buffer, 20 km connector
radius, 24 nearest candidates, at most 3 clear connectors. These are engineering
parameters, **not safety margins or vessel capabilities**. Country QA's explicit
100 km threshold against coarse land units is not a berth certificate.

Invalid polygons default to rejection. Measured builds explicitly use
`BLOCK_ENVELOPE`, blocking source-derived extents and recording exclusions. Do not
silently repair geometry or cut hand-drawn canals through land.

`validate_connectivity.py` separately recomputes directed strong components, checks
edge/port coordinates against their indexed nodes, and compares counts/statuses
with the manifest. It proves all covered ordered pairs by component membership,
not a small sample of searches. A second pass excludes prohibited or unresolved
restrictions (unknown legal status remains explicit research-only). This report
does not replace land validation or certify berths, depth or current permissions.
Resource bounds are configurable: `--max-nodes` (default 500000, maximum 1000000),
`--max-edges` (default/maximum 10000000), `--max-entry-bytes` (default 1000000000,
maximum 2000000000). Limits fail explicitly; no truncation into a passing report.
Algorithm semantics: [SciPy directed strong components](https://docs.scipy.org/doc/scipy/reference/generated/scipy.sparse.csgraph.connected_components.html).

The current writer streams tables and records builder/dependency versions. Older
artifacts built before that change retain their original identity. Exact-byte
reproduction requires the same source receipts, code, dependencies and parameters;
new code/provenance should not be expected to reproduce an older manifest hash.
Keep GSHHG README/LICENSE/LGPL notices with source/derived distributions. Review
combined-source attribution/redistribution obligations before publishing anything.

## Regional AIS diagnostic

```powershell
.venv-maritime/Scripts/python scripts/maritime/acquire.py noaa-ais-validation --output data/maritime-routing/snapshot-20260908
.venv-maritime/Scripts/python scripts/maritime/validate_ais.py --artifact data/maritime-routing/global-audited-v2.zip --ais-receipt data/maritime-routing/snapshot-20260908/ais-2024-01-01.csv.zst.receipt.json --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --config scripts/maritime/ais-validation-config.json --output data/maritime-routing/ais-validation-new.json
```

Official NOAA daily Zstandard source: 196,492,824 bytes, no credentials, regional
historical coverage only. Configuration defines sampling, gap, connector and metric
resource bounds. Selection happens before route outcomes; failures remain in the
denominator. No guessed acceptance threshold. Discrete geodesic Hausdorff/Frechet
depend on sampling; they are not exact continuous metrics or voyage certificates.
Do not tune on these outcomes and keep calling this an untouched holdout.

For the registered refinement experiment, use `noaa-ais-holdout` to acquire
2024-02-01, and run the SAME current validator/config on both artifacts. It records
helper-code hashes and dependency versions, and considers all clear bounded
connectors rather than only the nearest. Compare the resulting reports with:

```powershell
.venv-maritime/Scripts/python scripts/maritime/compare_ais.py --before data/maritime-routing/ais-holdout-old.json --after data/maritime-routing/ais-holdout-r4.json --output data/maritime-routing/ais-paired-comparison.json
```

Comparison refuses changed samples, source bytes, validator/helpers/dependencies
or parameters. It keeps failure-to-success transitions separate from geometric
error changes on cases measurable in both versions. These commands do not establish
an operational acceptance threshold.

## Tests

Run expensive GIS/Java work sequentially; use bounded heaps:

```powershell
.venv-maritime/Scripts/python -m unittest discover -s scripts/maritime/tests -v
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx768m" test
mvn -q "-DargLine=-Xmx1200m" "-Dtest=GlobalArtifactIntegrationTest" "-Dmaritime.graph=data/maritime-routing/global-audited-v2.zip" "-Dmaritime.validation=data/maritime-routing/global-audited-v2.checked.validation.json" test
mvn -q "-DargLine=-Xmx1800m" "-Dmaritime.max-entry-bytes=1000000000" "-Dmaritime.graph=data/maritime-routing/global-coastal-r4-audited-v1.zip" "-Dmaritime.validation=data/maritime-routing/global-coastal-r4-audited-v1.validation.json" test
git diff --check
```

The default Maven run skips the conditional real-artifact test; no synthetic
substitution. Explicit integration covers the catalog, eight source-derived
international pairs, alternatives and HTTP constraints/serialization. Benchmark:
`target/maritime-routing/runtime-benchmark.json`. Actual results: `evidence.md`.
The integration test also saves a graph-version-named report. For a larger artifact,
inspect uncompressed ZIP entry sizes first; `-Dmaritime.max-entry-bytes=<explicit bound>`
sets the test's loader limit (production equivalent: MARITIME_GRAPH_MAX_ENTRY_BYTES).
Do not silently raise limits or skip integrity checks after a size failure.
The refined artifact's edge entry is 845,769,688 bytes, hence its explicit
1,000,000,000-byte test bound above. Neither that limit nor an 1800 MB heap is
a production sizing/SLA promise. Run on a machine with sufficient free memory;
do not overlap the refined Java run with the large GIS jobs.

## Explicit research activation

### Dated regional V2/global regression evidence

Use a new output directory for each run to preserve earlier benchmark evidence.
The NOAA test defaults to V1; explicitly request V2 when passing the V2 artifact.
This is test-only configuration, not production activation. Example:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q -l target/phase3-routing-tests.log "-DargLine=-Xmx1800m" "-Dtest=GlobalArtifactIntegrationTest,NoaaPilotArtifactIntegrationTest,GraphArtifactLoaderTest,RouteSearchTest,MaritimeRoutingControllerTest" "-Dmaritime.max-entry-bytes=1000000000" "-Dmaritime.graph=data/maritime-routing/global-coastal-r4-audited-v1.zip" "-Dmaritime.validation=data/maritime-routing/global-coastal-r4-audited-v1.validation.json" "-Dmaritime.enc-pilot=data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip" "-Dmaritime.enc-validation=data/maritime-routing/noaa-new-york-pilot-v2-geometry-20260914.json" "-Dmaritime.enc-profile=NOAA_ENC_REGIONAL_V2" "-Dmaritime.report-directory=target/maritime-routing/new-run" test
```

The global test records actual geometry dateline/equator crossings, not just endpoint
country labels. V2 must retain bridge restrictions and unavailable source berths.
Frozen AIS experiment commands, results and hashes are documented in
[corridor-results-20260915.md](corridor-results-20260915.md). Never overwrite its
registered implementation/protocol or use evaluated holdouts to claim new validation.

### Operator-only activation (unchanged)

The new isolated semantic workflow is documented in [semantic-pilot.md](semantic-pilot.md).
Its artifacts and review candidates must NOT be passed to production activation.

The NOAA regional pilot is **not eligible** for this activation path. Its acquisition,
offline build, verification and negative findings are documented in [noaa-pilot.md](noaa-pilot.md).
To include both the established global integration and the new regional negative test:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx1200m" "-Dmaritime.graph=data/maritime-routing/global-audited-v2.zip" "-Dmaritime.validation=data/maritime-routing/global-audited-v2.checked.validation.json" "-Dmaritime.enc-pilot=data/maritime-routing/noaa-new-york-pilot-v1.zip" "-Dmaritime.enc-validation=data/maritime-routing/noaa-new-york-pilot-v1.validation.json" test
```

Protect local secrets in test logs: do not publish full Spring context output.
Inspect Maven exit status and aggregate Surefire XML counts; share only filtered
`[ERROR]`/`MARITIME_BENCHMARK` output. The optional NOAA test has no synthetic fallback.

Default state is unavailable. An operator must configure matching artifact, trusted
whole-file hash and completed reciprocal-check report before application restart:

```powershell
$env:MARITIME_GRAPH_ARTIFACT = 'C:/Ciclo8/Teemo-Backend-Staging-master/data/maritime-routing/global-audited-v2.zip'
$env:MARITIME_GRAPH_SHA256 = 'fdc1229b166406d6f42890ae56200a79e105c48396ceb881eeed737ca489d81a'
$env:MARITIME_GRAPH_VALIDATION_REPORT = 'C:/Ciclo8/Teemo-Backend-Staging-master/data/maritime-routing/global-audited-v2.checked.validation.json'
Get-FileHash -Algorithm SHA256 -LiteralPath $env:MARITIME_GRAPH_ARTIFACT
```

Adjust paths on another machine and first check that validation completed (see
evidence.md). Do not activate rejected coarse snapshots or older unchecked reports.
Failed first load/configuration changes require restart; one immutable snapshot
per process. Optional `MARITIME_GRAPH_MAX_ENTRY_BYTES` defaults to 536870912; this
is a decompression-entry bound, not a Java heap bound.

No activation/deployment was performed by this task. Every route POST needs research
acknowledgement (`api.md`). No public build/download/file/URL input endpoint exists.
Existing security permits all requests: a separate authorization/rate/resource-limit
review is required before exposing the server publicly.

## Prospective transit replay (2026-09-16)

This experiment has already completed and failed; July/August are not untouched
future evaluation data. Never rerun its freeze/acquisition commands over existing
files. The original registration and replay bundle bind all 22 implementation
files and the exact model/environment. Preserve these and all previous reports.
See `transit-results-20260916.md` for hashes, census and diagnosis.

Original one-time registration (shown for audit, not to repeat locally):

```powershell
.venv-maritime/Scripts/python scripts/maritime/freeze_transit_experiment.py --artifact data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip --chart-receipt data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json --model data/maritime-routing/march-directed-corridor-model-v1-20260915.zip --protocol docs/maritime-routing/transit-holdout-protocol-20260916.md --geometry-report data/maritime-routing/noaa-new-york-pilot-v2-geometry-20260914.json --source-root data/maritime-routing --output docs/maritime-routing/transit-holdout-registration-20260916.json --bundle data/maritime-routing/transit-frozen-implementation-20260916.zip
```

Original acquirer arguments, after registration and network approval:
`acquire_transit_holdout.py july` / `august`, with
`--registration docs/maritime-routing/transit-holdout-registration-20260916.json`
and `--output data/maritime-routing/ais-<month>-transit-holdout-20260916`.
Do not substitute another date or forge an acquisition timestamp on a replay.

Replay with the original receipts/registration and **new output paths**:

```powershell
$transitArgs = @(
  '--artifact', 'data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip',
  '--chart-receipt', 'data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json',
  '--model', 'data/maritime-routing/march-directed-corridor-model-v1-20260915.zip',
  '--protocol', 'docs/maritime-routing/transit-holdout-protocol-20260916.md',
  '--registration', 'docs/maritime-routing/transit-holdout-registration-20260916.json'
)
foreach ($transitMonth in @('july','august')) {
  $transitDate = if ($transitMonth -eq 'july') { '07' } else { '08' }
  $transitReceipt = "data/maritime-routing/ais-$transitMonth-transit-holdout-20260916/ais-2024-$transitDate-01.csv.zst.receipt.json"
  foreach ($transitObjective in @('DISTANCE_V1','DIRECTED_AIS_BALANCED_V1')) {
    .venv-maritime/Scripts/python scripts/maritime/validate_transit_ais.py @transitArgs --ais-receipt $transitReceipt --routing-objective $transitObjective --output "data/maritime-routing/transit-$transitMonth-$transitObjective-replay-new.json"
    if ($LASTEXITCODE -ne 0) { throw 'Transit replay failed' }
  }
}
.venv-maritime/Scripts/python -m unittest discover -s scripts/maritime/tests
```

`compare_transit_runs.py --before <distance-report> --after <directed-report>
--registration <original-registration> --output <new-paired-report>` audits the
registration, original candidate census, selected rows and recomputed acceptance.
`diagnose_transit_preferences.py` takes `--before`, `--after`, `--artifact`,
`--chart-receipt`, `--model`, `--output` and reconstructs costs without new routing.
`diagnose_corridor_failures.py` takes `--report`, `--ais-receipt`, `--chart-receipt`,
`--artifact`, `--output` and verifies original rows before chart attribution.
Diagnostics are post hoc explanations, never alternative acceptance outputs.

## Coherent O/D experiment and global source audit (2026-09-21)

Completed results and immutable hashes: `od-results-20260921.md`. September/October
passed the regional temporal criterion; global phase 3 remains NOT_PASSED. Do not
repeat acquisition/freezing over existing files or modify the 27 registered files.

The extraction command uses `od_corridor_model.py --artifact <NOAA-V2.zip>
--chart-receipt <V2-receipt> --training-receipt <receipt>` (repeat that flag for all
six original March–August dates) `--output <new-model.json>`. It scans original AIS,
preserves the full census/source rows/vessel groups and rejects other dates.
The existing immutable result is `data/maritime-routing/od-coherent-model-v1-20260921.json`.

Grouped development replay uses the evaluation command below without
`--ais-receipt`, `--protocol` and `--registration`, and a **new output filename**.
That role is explicitly development, never an independent temporal result.

Original registration (audit record only, already completed):

```powershell
.venv-maritime/Scripts/python scripts/maritime/freeze_od_experiment.py --artifact data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip --chart-receipt data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json --model data/maritime-routing/od-coherent-model-v1-20260921.json --protocol docs/maritime-routing/od-holdout-protocol-20260921.md --geometry-report data/maritime-routing/noaa-new-york-pilot-v2-geometry-20260914.json --source-root data/maritime-routing --output docs/maritime-routing/od-holdout-registration-20260921.json --bundle data/maritime-routing/od-frozen-implementation-20260921.zip
```

Original acquisition used `acquire_od_holdout.py september` / `october`, with
`--registration docs/maritime-routing/od-holdout-registration-20260921.json` and
`--output data/maritime-routing/ais-<month>-od-holdout-20260921`. Do not forge new
acquisition times or substitute dates for a replay; reuse the existing receipts.

Replay both objectives sequentially for each date, using new output paths:

```powershell
$odArgs = @(
  '--artifact', 'data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip',
  '--chart-receipt', 'data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json',
  '--model', 'data/maritime-routing/od-coherent-model-v1-20260921.json',
  '--protocol', 'docs/maritime-routing/od-holdout-protocol-20260921.md',
  '--registration', 'docs/maritime-routing/od-holdout-registration-20260921.json'
)
foreach ($odMonth in @('september','october')) {
  $odDate = if ($odMonth -eq 'september') { '09' } else { '10' }
  .venv-maritime/Scripts/python scripts/maritime/evaluate_od_corridors.py @odArgs --ais-receipt "data/maritime-routing/ais-$odMonth-od-holdout-20260921/ais-2024-$odDate-01.csv.zst.receipt.json" --output "data/maritime-routing/od-$odMonth-replay-new.json"
  if ($LASTEXITCODE -ne 0) { throw 'O/D evaluation replay failed' }
}
```

`compare_od_runs.py --report <report> --model <model> --registration <registration>
--output <new-paired-report>` recomputes acceptance and checks paired census and
frozen bindings. Omit `--registration` only for grouped development. The separate
`diagnose_od_cases.py` takes `--report`, `--ais-receipt`, `--chart-receipt`,
`--artifact`, `--view <new-derived-view>` and `--output <new-diagnosis>`; it verifies
original source rows with the existing diagnostic, retaining the parent report hash.

The two-date summary is also reproducible to a **new** output:

```powershell
.venv-maritime/Scripts/python scripts/maritime/summarize_phase3_od.py --model data/maritime-routing/od-coherent-model-v1-20260921.json --registration docs/maritime-routing/od-holdout-registration-20260921.json --report data/maritime-routing/od-september-holdout-20260921.json --report data/maritime-routing/od-october-holdout-20260921.json --ukho-audit data/maritime-routing/ukho-global-mesh-audit-20260921.json --output data/maritime-routing/phase3-status-replay-new.json
.venv-maritime/Scripts/python -m unittest discover -s scripts/maritime/tests
```

Global source acquisition helpers are separate, bounded, read-only and require
network access. `acquire_passage_sources.py --output <new.zip>` preserves original
official index/guidance responses. `acquire_ukho_routeing.py --output <new.zip>` pins
the official item/service/owner, checks feature membership and retains raw responses
and original licence metadata. Existing snapshots must not be overwritten.

Reproduce the UKHO coverage audit without network access:

```powershell
.venv-maritime/Scripts/python scripts/maritime/audit_ukho_routeing.py --source data/maritime-routing/ukho-routeing-research-20260921.zip --artifact data/maritime-routing/global-coastal-r4-audited-v1.zip --geometry-report data/maritime-routing/global-coastal-r4-audited-v1.validation.json --output data/maritime-routing/ukho-global-mesh-audit-replay-new.json
```

This streams graph tables and checks the existing full reciprocal report; it is
not a new coast rebuild or a navigational-compliance test. UKHO source suitability
limitations remain attached, and no feature is imported into operational routing.
# Phase 4 continuation

For original forecast acquisition, decoding, pinned API configuration and the
weather/temporal validation commands, see [phase4-runbook.md](phase4-runbook.md).
Use its separate `.venv-weather` environment; the frozen geometry/AIS environment
and historical reproduction instructions below remain unchanged.
