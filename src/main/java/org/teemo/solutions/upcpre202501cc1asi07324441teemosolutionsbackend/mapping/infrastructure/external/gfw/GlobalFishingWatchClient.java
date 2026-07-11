package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GlobalFishingWatchClient {

    private static final Logger logger = LoggerFactory.getLogger(GlobalFishingWatchClient.class);
    private static final String DATASET = "public-global-presence:latest";
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    private final WebClient webClient;
    private final GlobalFishingWatchProperties properties;
    private final ObjectMapper objectMapper;
    private final Duration pollInterval;
    private final PollDelay pollDelay;

    @Autowired
    public GlobalFishingWatchClient(WebClient webClient, GlobalFishingWatchProperties properties) {
        this(webClient, properties, new ObjectMapper(), DEFAULT_POLL_INTERVAL, GlobalFishingWatchClient::sleepUnchecked);
    }

    GlobalFishingWatchClient(WebClient webClient,
                             GlobalFishingWatchProperties properties,
                             ObjectMapper objectMapper,
                             Duration pollInterval,
                             PollDelay pollDelay) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.pollInterval = pollInterval == null || pollInterval.isNegative() ? Duration.ZERO : pollInterval;
        this.pollDelay = pollDelay;
        int maxInMemorySizeBytes = properties.getResponseBufferMb() * 1024 * 1024;
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes))
                .build();
        this.webClient = webClient.mutate()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }

    public List<PresenceCell> fetchPresenceCells(GlobalFishingWatchProperties.RegionProperties region) {
        if (!properties.isConfigured()) {
            return List.of();
        }

        try {
            Instant deadline = Instant.now().plus(properties.getTimeout());
            for (int attempt = 1; attempt <= 3 && Instant.now().isBefore(deadline); attempt++) {
                RawHttpResponse submission = submitReport(region);
                if (submission.statusCode() == 429) {
                    logger.info("gfw.report.busy region={} attempt={} waitingForLastReport=true", region.getId(), attempt);
                    awaitRunningReportToSettle(region, deadline);
                    continue;
                }
                if (submission.statusCode() >= 400) {
                    logFailure(region.getId(), submission);
                    return List.of();
                }

                ReportPayload payload = parsePayload(submission.body());
                if (payload.isSuccess()) {
                    logSuccess(region.getId(), payload.cells());
                    return payload.cells();
                }
                if (payload.isPending()) {
                    return waitForSubmittedReport(region, deadline);
                }

                logger.warn("gfw.report.failed region={} status={} body={}",
                        region.getId(),
                        submission.statusCode(),
                        submission.body());
                return List.of();
            }

            logger.warn("gfw.report.failed region={} message=Timed out waiting to submit report", region.getId());
        } catch (Exception ex) {
            logger.warn("gfw.report.failed region={} message={}", region.getId(), ex.getMessage());
        }
        return List.of();
    }

    private URI buildReportUri(UriBuilder uriBuilder, LocalDate startDate, LocalDate endDate) {
        UriBuilder builder = uriBuilder.path("/v3/4wings/report")
                .queryParam("datasets[0]", DATASET)
                .queryParam("date-range", "%s,%s".formatted(startDate, endDate))
                .queryParam("format", "JSON")
                .queryParam("spatial-resolution", properties.getSpatialResolution())
                .queryParam("temporal-resolution", properties.getTemporalResolution())
                .queryParam("group-by", "FLAG");

        if (!properties.getVesselTypes().isEmpty()) {
            String joinedTypes = properties.getVesselTypes().stream()
                    .map(type -> "\"%s\"".formatted(type))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            builder.queryParam("filters[0]", "vessel_type in (%s)".formatted(joinedTypes));
        }
        return builder.build();
    }

    private List<PresenceCell> waitForSubmittedReport(GlobalFishingWatchProperties.RegionProperties region, Instant deadline) {
        while (Instant.now().isBefore(deadline)) {
            RawHttpResponse lastReport = getLastReport();
            if (lastReport.statusCode() == 404) {
                pause();
                continue;
            }
            if (lastReport.statusCode() >= 400) {
                logFailure(region.getId(), lastReport);
                return List.of();
            }

            ReportPayload payload = parsePayload(lastReport.body());
            if (payload.isPending()) {
                pause();
                continue;
            }
            if (payload.isSuccess()) {
                logSuccess(region.getId(), payload.cells());
                return payload.cells();
            }

            logger.warn("gfw.report.failed region={} status={} body={}",
                    region.getId(),
                    lastReport.statusCode(),
                    lastReport.body());
            return List.of();
        }

        logger.warn("gfw.report.failed region={} message=Timed out waiting for last-report completion", region.getId());
        return List.of();
    }

    private void awaitRunningReportToSettle(GlobalFishingWatchProperties.RegionProperties region, Instant deadline) {
        while (Instant.now().isBefore(deadline)) {
            RawHttpResponse lastReport = getLastReport();
            if (lastReport.statusCode() == 404) {
                pause();
                continue;
            }
            if (lastReport.statusCode() >= 400) {
                logFailure(region.getId(), lastReport);
                return;
            }

            ReportPayload payload = parsePayload(lastReport.body());
            if (payload.isPending()) {
                pause();
                continue;
            }
            return;
        }
    }

    private RawHttpResponse submitReport(GlobalFishingWatchProperties.RegionProperties region) {
        logger.info("gfw.report.request region={} dataset={} dateRange={},{} spatialResolution={} temporalResolution={} groupBy=FLAG vesselTypes={} bbox=[{},{},{},{}]",
                region.getId(),
                DATASET,
                properties.getStartDate(),
                properties.getEndDate(),
                properties.getSpatialResolution(),
                properties.getTemporalResolution(),
                properties.getVesselTypes(),
                region.getMinLon(),
                region.getMinLat(),
                region.getMaxLon(),
                region.getMaxLat());
        return webClient.post()
                .uri(uriBuilder -> buildReportUri(uriBuilder, properties.getStartDate(), properties.getEndDate()))
                .bodyValue(GeoJsonReportBody.forBoundingBox(region))
                .exchangeToMono(this::toRawResponse)
                .block(properties.getTimeout());
    }

    private RawHttpResponse getLastReport() {
        return webClient.get()
                .uri("/v3/4wings/last-report")
                .exchangeToMono(this::toRawResponse)
                .block(properties.getTimeout());
    }

    private Mono<RawHttpResponse> toRawResponse(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new RawHttpResponse(statusCode.value(), body));
    }

    private ReportPayload parsePayload(String body) {
        if (body == null || body.isBlank()) {
            return ReportPayload.pendingState();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("status") && root.get("status").isTextual()
                    && "running".equalsIgnoreCase(root.get("status").asText())) {
                return ReportPayload.pendingState();
            }
            if (root.has("message") && root.has("status") && root.get("status").canConvertToInt()) {
                return ReportPayload.errorState();
            }
            if (!root.has("entries")) {
                return ReportPayload.errorState();
            }

            FourWingsReportResponse response = objectMapper.treeToValue(root, FourWingsReportResponse.class);
            return ReportPayload.success(flatten(response));
        } catch (Exception ex) {
            logger.warn("gfw.report.parse.failed message={}", ex.getMessage());
            return ReportPayload.errorState();
        }
    }

    private void logSuccess(String regionId, List<PresenceCell> cells) {
        if (cells.isEmpty()) {
            logger.info("gfw.report.empty region={} dataset={} temporalResolution={} vesselTypes={}",
                    regionId,
                    DATASET,
                    properties.getTemporalResolution(),
                    properties.getVesselTypes());
            return;
        }

        PresenceCell sample = cells.get(0);
        logger.info("gfw.report.ok region={} cells={} sampleLat={} sampleLon={} sampleHours={} sampleVesselIds={}",
                regionId,
                cells.size(),
                sample.latitude(),
                sample.longitude(),
                sample.hours(),
                sample.vesselIds());
    }

    private void logFailure(String regionId, RawHttpResponse response) {
        logger.warn("gfw.report.failed region={} status={} body={}",
                regionId,
                response.statusCode(),
                response.body());
    }

    private void pause() {
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            return;
        }
        try {
            pollDelay.sleep(pollInterval);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepUnchecked(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private List<PresenceCell> flatten(FourWingsReportResponse response) {
        if (response == null || response.entries == null) {
            return List.of();
        }

        List<PresenceCell> cells = new ArrayList<>();
        for (Map<String, List<PresenceCellEntry>> entry : response.entries) {
            for (List<PresenceCellEntry> datasetRows : entry.values()) {
                for (PresenceCellEntry row : datasetRows) {
                    if (row == null || row.lat == null || row.lon == null || row.hours == null) {
                        continue;
                    }
                    cells.add(new PresenceCell(
                            row.lat,
                            row.lon,
                            row.hours,
                            Optional.ofNullable(row.vesselIds).orElse(0)
                    ));
                }
            }
        }
        return cells;
    }

    public record PresenceCell(double latitude, double longitude, double hours, int vesselIds) {
    }

    private record GeoJsonReportBody(GeoJsonPolygon geojson) {
        private static GeoJsonReportBody forBoundingBox(GlobalFishingWatchProperties.RegionProperties region) {
            List<List<Double>> ring = List.of(
                    List.of(region.getMinLon(), region.getMinLat()),
                    List.of(region.getMaxLon(), region.getMinLat()),
                    List.of(region.getMaxLon(), region.getMaxLat()),
                    List.of(region.getMinLon(), region.getMaxLat()),
                    List.of(region.getMinLon(), region.getMinLat())
            );
            return new GeoJsonReportBody(new GeoJsonPolygon("Polygon", List.of(ring)));
        }
    }

    private record GeoJsonPolygon(String type, List<List<List<Double>>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class FourWingsReportResponse {
        @JsonProperty("entries")
        private List<Map<String, List<PresenceCellEntry>>> entries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PresenceCellEntry {
        @JsonProperty("lat")
        private Double lat;
        @JsonProperty("lon")
        private Double lon;
        @JsonProperty("hours")
        private Double hours;
        @JsonAlias({"vesselIDs", "vesselIds"})
        @JsonProperty("vesselIDs")
        private Integer vesselIds;
    }

    @FunctionalInterface
    interface PollDelay {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record RawHttpResponse(int statusCode, String body) {
    }

    private record ReportPayload(List<PresenceCell> cells, boolean pending, boolean error) {
        private static ReportPayload success(List<PresenceCell> cells) {
            return new ReportPayload(cells == null ? List.of() : List.copyOf(cells), false, false);
        }

        private static ReportPayload pendingState() {
            return new ReportPayload(List.of(), true, false);
        }

        private static ReportPayload errorState() {
            return new ReportPayload(List.of(), false, true);
        }

        private boolean isSuccess() {
            return !pending && !error;
        }

        private boolean isPending() {
            return pending;
        }
    }
}
