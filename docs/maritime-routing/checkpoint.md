# Archived checkpoint — superseded

This is historical. Read progress.md for the latest active work, artifacts and commands.

User explicitly requested implementation of phases 0–7 with a hard phase-3
geographical/AIS quality gate. Do not implement contractual optimization before
that gate; do not declare this project complete. Continue from existing edits.
No subagents permitted in this session. Initial worktree was clean. No Git commit
or external deployment made. Do not print application secrets or JVM crash dumps.

## Implemented so far

- Audit/architecture/runbook/evidence documents (need final updates).
- Isolated `.venv-maritime`, pinned offline GIS dependencies.
- Official GSHHG 2.3.7 archive + UN/LOCODE 2025-1 archive with SHA-256 receipts in
  ignored `data/maritime-routing/snapshot-20260908/`. NGA CSV returned 403; modern
  NGA service failed TLS trust; certificates never bypassed.
- `scripts/maritime/build_graph.py`: source receipts/hashes, hybrid H3 resolutions,
  WGS84 geodesic land intersections, conservative explicit invalid-shape envelope
  quarantine, original port coordinates, rejected/conflicting ports, components,
  deterministic ZIP artifact. No AIS/canal/depth data yet.
- `validate_graph.py`: finer shoreline checks, metrics helpers, report; no AIS
  ground truth fabricated. Python synthetic test suite: 8 passing.
- Java `mapping/routing/v2`: immutable physical graph, bounded streaming loader,
  contextual constraints/cost policy, directed A*, bounded Yen, conservative
  geographic separation, local artifact catalog and versioned research-only API.
- Legacy land mask now JTS, includes endpoints, whole sampled-segment intersection,
  no handwritten fallback; spherical dateline-safe GeoUtils interpolation;
  no missing-edge straight-line fallback; no land-crossing port/canal exemptions.
- Legacy estimate HTTP endpoints return explicit 503 through
  UnverifiedLegacyRoutingFilter. Unsafe defaults disabled in application.properties:
  mapping seed, graph warmup, catalogue backbone, GFW automatic use. No DB modified.
  MUST document compatibility migration and tell user; legacy historical reads kept.
- New Java tests for route constraints, Yen, admissibility vs Dijkstra over 64
  closure combinations, land/dateline, API unavailable/research ack and legacy guard.
- Latest regression command below EXITED 0 (43 tests expected; sum actual reports).

```powershell
mvn -q "-DargLine=-Xmx768m" "-Dtest=RouteSearchTest,MaritimeLandMaskRegressionTest,MaritimeRoutingControllerTest,UnverifiedLegacyRoutingFilterTest,RouteCalculatorServiceImplTest,RouteGraphBuilderOverlayTest,RouteServiceMaritimeGraphTest,MaritimeGraphLandSafetyTest,GlobalFishingWatchCorridorOverlayProviderTest,RouteServiceRecalculationTest" test
```

## Artifacts and running work

- `global-research-v1.zip`: high (h) coast, 2 km geodesic step, 46,206 nodes,
  836,904 directed edges, 1,057 connected references, 1,298 components, 21,441,658
  bytes; build 256.03 s. Version c00c5216760a40acada71aac77a58081651d4029cf3c38c8233c01c0cfb08275.
- **REJECTED by finer validation**: `validation-v1.json`, 418,452 reciprocal edge
  pairs, 11,501 full-coast conflicts, zero distance mismatches; 459.92 s.
  This is evidence why visual plausibility/coarse masks are insufficient.
- Full-coast reconstruction running as exec session **31013**, target
  `global-full-coast-v1.zip`, config `full-coast-config.json`, same official inputs,
  full shoreline and 500 m step. Last progress 40,000 mesh nodes / 717,210 edges.
  Poll session before launching another expensive build. Coarse validation done.
- Maven session 26397 completed successfully. Prior unbounded JVM crashed due native
  memory pressure alongside two GIS processes; use `-DargLine=-Xmx768m` and avoid
  concurrent heavy GIS/Java loads. `hs_err_pid14160.log` ignored; don't print it.

## Next concrete tasks / issues found

1. Complete full artifact, run validator and `GlobalArtifactIntegrationTest` with
   `-Dmaritime.graph=data/maritime-routing/global-full-coast-v1.zip`. It loads real
   ports (no handcoded coordinates), runs 8 international pairs and writes runtime
   metrics to `target/maritime-routing/runtime-benchmark.json`.
2. IMPORTANT DATA QUALITY: some UN/LOCODE source coordinates are clearly wrong
   country/longitude (e.g. Austrian/Czech/Hungarian locations in Pacific). Do not
   correct by guessing. Need geographic country sanity checking against a sourced
   global country dataset or quarantine; research port count is not verified real
   terminal coverage. Country QA is not berth validation. Original points retained.
3. Builder currently materializes all JSON tables in memory on writing. Improve
   streaming ZIP/hash writing for global memory use. Record builder/code/dependency
   version hashes in artifact. Runtime needs loader tampering/missing-provenance
   tests, bounded geometry param checks, ideally operator-pinned artifact SHA.
4. New API properties currently use `routing.maritime.v2.artifact` blank default;
   no artifact automatically deployed/promoted. Need document setup and failure
   semantics. No fictitious build endpoint: builds stay CLI/offline.
5. Write `api.md` (filter references it but not yet present), final evidence and
   precise status for gated phases. Document endpoints' nullable time, research
   acknowledgement, USER_SUPPLIED draft/clearance/speed/closures, all missing layers.
6. Run full Maven suite (avoid external secrets/network logs; context test disables
   seed/GFW). Fix regressions without restoring unsafe assumptions. Baseline tests
   needed synthetic masks to explicitly override isOnLand as well as crossesLand.
7. Remaining genuine data gates: verified port approaches, official channels and
   restrictions/depth, licensed directed AIS and held-out validation. Existing GFW
   presence density cannot substitute for tracks. No business-model phases yet.

Latest user: “continua con la tarea, ya reestablci los tokens.” Continue implementing;
earlier pause question was superseded. OpenAI Docs skill was inspected for that
question only; not required for subsequent generic maritime implementation.
