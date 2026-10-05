package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Operator-selected local immutable storage; no client paths and no overwrite/delete API. */
@Service
public class PlanStore {
    private static final long MAX_FILE = 8L * 1024 * 1024;
    private final Path root;
    public PlanStore(@Value("${routing.maritime.plans.directory:}") String directory) {
        root = directory == null || directory.isBlank() ? null : Path.of(directory).toAbsolutePath().normalize();
    }
    public synchronized void available() {
        if (root == null) throw new PlanFailure(503, "PLAN_STORE_NOT_CONFIGURED");
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) throw new IOException();
            long bytes = 0; int count = 0;
            try (var paths = Files.list(root)) {
                for (Path p : paths.toList()) { bytes += Files.size(p); count++; }
            }
            if (count >= 128 || bytes > 256L * 1024 * 1024 - MAX_FILE) throw new PlanFailure(507, "PLAN_STORE_QUOTA_REACHED");
        } catch (IOException invalid) { throw new PlanFailure(503, "PLAN_STORE_UNAVAILABLE"); }
    }
    public synchronized JsonNode save(JsonNode content) {
        available(); String id = UUID.randomUUID().toString();
        var envelope = PlanJson.MAPPER.createObjectNode().put("planId", id).put("createdAt", Instant.now().toString())
                .put("contentSha256", PlanJson.sha(content));
        envelope.set("content", content.deepCopy()); byte[] bytes = PlanJson.bytes(envelope);
        if (bytes.length > MAX_FILE) throw new PlanFailure(413, "PLAN_RESULT_TOO_LARGE");
        try {
            Files.write(root.resolve(id + ".json"), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return PlanJson.MAPPER.readTree(bytes);
        }
        catch (IOException invalid) { throw new PlanFailure(503, "PLAN_STORE_WRITE_FAILED"); }
    }
    public synchronized JsonNode get(String id) {
        if (root == null) throw new PlanFailure(503, "PLAN_STORE_NOT_CONFIGURED");
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new PlanFailure(404, "PLAN_NOT_FOUND");
        Path path = root.resolve(id + ".json");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new PlanFailure(404, "PLAN_NOT_FOUND");
        try {
            if (Files.isSymbolicLink(path) || Files.size(path) > MAX_FILE) throw new IOException();
            JsonNode envelope = PlanJson.MAPPER.readTree(Files.readAllBytes(path));
            if (envelope == null || !envelope.isObject() || !id.equals(envelope.path("planId").asText()) || !envelope.has("content")
                    || !PlanJson.sha(envelope.get("content")).equals(envelope.path("contentSha256").asText())) throw new IOException();
            return envelope;
        } catch (IOException | IllegalArgumentException invalid) { throw new PlanFailure(503, "PLAN_INTEGRITY_FAILED"); }
    }
}
