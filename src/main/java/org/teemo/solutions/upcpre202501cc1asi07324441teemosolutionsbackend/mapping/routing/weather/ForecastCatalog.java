package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ForecastCatalog {
    private final String manifest, sha256;
    private volatile boolean attempted;
    private volatile ForecastSnapshot snapshot;
    private volatile String status = "NOT_LOADED";
    public ForecastCatalog(@Value("${routing.maritime.weather.manifest:}") String manifest,
                           @Value("${routing.maritime.weather.sha256:}") String sha256) {
        this.manifest = manifest; this.sha256 = sha256;
    }
    public Optional<ForecastSnapshot> current() {
        if (!attempted) synchronized (this) {
            if (!attempted) {
                if (manifest == null || manifest.isBlank()) status = "FORECAST_NOT_CONFIGURED";
                else try { snapshot = new ForecastSnapshot(Path.of(manifest), sha256); status = "RESEARCH_FORECAST"; }
                catch (java.io.IOException | RuntimeException invalid) { status = "FORECAST_INVALID_OR_INACCESSIBLE"; }
                attempted = true;
            }
        }
        return Optional.ofNullable(snapshot);
    }
    public String status() { current(); return status; }
}
