package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/** Lazy, single immutable snapshot per process; no request-time reconstruction or data downloads. */
@Service
public class GraphCatalog {
    private final String artifact;
    private final long maxEntryBytes;
    private final String expectedSha256;
    private final String validationReport;
    private volatile PhysicalGraph graph;
    private volatile boolean attempted;
    private volatile String status = "NOT_LOADED";

    @org.springframework.beans.factory.annotation.Autowired
    public GraphCatalog(@Value("${routing.maritime.v2.artifact:}") String artifact,
                        @Value("${routing.maritime.v2.max-entry-bytes:536870912}") long maxEntryBytes,
                        @Value("${routing.maritime.v2.sha256:}") String expectedSha256,
                        @Value("${routing.maritime.v2.validation-report:}") String validationReport) {
        this.artifact = artifact; this.maxEntryBytes = maxEntryBytes;
        this.expectedSha256 = expectedSha256; this.validationReport = validationReport;
    }
    public GraphCatalog(String artifact, long maxEntryBytes) { this(artifact, maxEntryBytes, "", ""); }
    public Optional<PhysicalGraph> current() {
        if (!attempted) synchronized (this) {
            if (!attempted) {
                if (artifact == null || artifact.isBlank()) status = "ARTIFACT_NOT_CONFIGURED";
                else if (expectedSha256 == null || !expectedSha256.matches("[a-f0-9]{64}")) status = "ARTIFACT_SHA256_NOT_CONFIGURED";
                else if (validationReport == null || validationReport.isBlank()) status = "GEOMETRIC_VALIDATION_REPORT_REQUIRED";
                else try {
                    if (Files.size(Path.of(validationReport)) > 2_000_000) throw new IOException("Oversized validation report");
                    var validation = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Path.of(validationReport).toFile());
                    if (!"PASS".equals(validation.path("geometricCheck").asText())) throw new IOException("Geometric gate not passed");
                    if (!validation.path("reciprocalGeometryVerified").asBoolean(false)) throw new IOException("Complete reciprocal mesh check required");
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (var input = Files.newInputStream(Path.of(artifact))) {
                        byte[] buffer = new byte[65536]; int read;
                        while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
                    }
                    if (!HexFormat.of().formatHex(digest.digest()).equals(expectedSha256)) throw new IOException("Artifact digest mismatch");
                    var loaded = new GraphArtifactLoader(maxEntryBytes).load(Path.of(artifact));
                    if (!loaded.version().equals(validation.path("graphVersion").asText())) throw new IOException("Validation version mismatch");
                    if (loaded.manifest().path("schemaVersion").asInt() == 2) {
                        var controls = loaded.manifest().path("regionalControls");
                        if (!"PASS".equals(validation.path("regionalControlCheck").asText())
                                || !expectedSha256.equals(validation.path("artifactSha256").asText())
                                || !controls.equals(validation.path("regionalControls"))
                                || !loaded.manifest().path("tables").path("regional-evidence.jsonl")
                                    .equals(validation.path("regionalEvidenceSha256"))
                                || validation.path("regionalControlledEdges").asLong(-1)
                                    != loaded.edges().stream().filter(e -> !e.regionalControlIds().isEmpty()).count())
                            throw new IOException("Complete matching regional validation required");
                    }
                    if (validation.path("checkedReciprocalPairs").asLong(-1) * 2 != loaded.edges().size()) throw new IOException("Incomplete edge validation");
                    if (loaded.manifest().path("portCountryQa").path("sourceId").asText().isBlank()) throw new IOException("Port coordinate quality audit required");
                    long coveredPorts = loaded.ports().values().stream().filter(p -> "CONNECTED_REFERENCE_POINT".equals(p.status())).count();
                    if (coveredPorts != validation.path("coveredPortCount").asLong(-1)) throw new IOException("Port coverage report mismatch");
                    if (validation.has("unchangedGeometryTables")) {
                        for (String table : java.util.List.of("nodes.jsonl", "edges.jsonl"))
                            if (!loaded.manifest().path("tables").path(table).asText().equals(validation.path("unchangedGeometryTables").path(table).asText()))
                                throw new IOException("Inherited validation geometry mismatch");
                    }
                    validateCanalEvidence(loaded, validation);
                    validateSuezEvidence(loaded, validation);
                    graph = loaded; status = "RESEARCH_ONLY";
                }
                catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                catch (IOException | RuntimeException failure) { status = "ARTIFACT_INVALID_OR_INACCESSIBLE"; }
                // Never leak local paths, URLs or untrusted parser exception details in an API.
                attempted = true;
            }
        }
        return Optional.ofNullable(graph);
    }
    public String status() { current(); return status; }

    static void validateCanalEvidence(PhysicalGraph loaded, com.fasterxml.jackson.databind.JsonNode validation) throws IOException {
        long canalEdges = loaded.edges().stream().filter(e -> "PANAMA_CANAL_RESEARCH".equals(e.specialZone())).count();
        var control = loaded.manifest().path("panamaControls");
        if (control.isMissingNode() && canalEdges == 0) return;
        if (!"OSM_EXACT_CENTERLINE_AND_WATER_GSHHG_OUTSIDE_V1".equals(control.path("policy").asText())
                || !"PASS".equals(validation.path("panamaControlCheck").asText())
                || !control.equals(validation.path("panamaControls"))
                || canalEdges == 0 || canalEdges != control.path("addedDirectedEdges").asLong(-1)
                || !loaded.manifest().path("tables").path("panama-evidence.jsonl").asText().matches("[a-f0-9]{64}")
                || !loaded.manifest().path("tables").path("panama-evidence.jsonl").equals(validation.path("panamaEvidenceSha256"))
                || !validation.path("allAddedNodesResearchPolicyReachable").asBoolean(false)
                || validation.path("sourceGeometryConflicts").asInt(-1) != 0)
            throw new IOException("Complete matching Panama geometry validation required");
    }

    static void validateSuezEvidence(PhysicalGraph loaded, com.fasterxml.jackson.databind.JsonNode validation) throws IOException {
        long canalEdges = loaded.edges().stream().filter(e -> "SUEZ_CANAL_RESEARCH".equals(e.specialZone())).count();
        var control = loaded.manifest().path("suezControls");
        if (control.isMissingNode() && canalEdges == 0) return;
        if (!"OSM_EXACT_SUEZ_CENTERLINE_GSHHG_OUTSIDE_V1".equals(control.path("policy").asText())
                || !"PASS".equals(validation.path("suezControlCheck").asText())
                || !control.equals(validation.path("suezControls"))
                || canalEdges == 0 || canalEdges != control.path("addedDirectedEdges").asLong(-1)
                || !loaded.manifest().path("tables").path("suez-evidence.jsonl").asText().matches("[a-f0-9]{64}")
                || !loaded.manifest().path("tables").path("suez-evidence.jsonl").equals(validation.path("suezEvidenceSha256"))
                || !validation.path("allAddedNodesResearchPolicyReachable").asBoolean(false)
                || validation.path("sourceGeometryConflicts").asInt(-1) != 0)
            throw new IOException("Complete matching Suez geometry validation required");
    }
}
