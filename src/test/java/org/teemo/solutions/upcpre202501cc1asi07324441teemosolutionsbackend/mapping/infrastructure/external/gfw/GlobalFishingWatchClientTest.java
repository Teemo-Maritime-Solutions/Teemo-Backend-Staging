package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalFishingWatchClientTest {

    @Test
    void shouldPollLastReportWhenSubmissionReturnsEmptyBody() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> Mono.just(responseFor(request, calls.getAndIncrement(),
                response(HttpStatus.OK, ""),
                response(HttpStatus.OK, """
                        {
                          "uri": "/v3/4wings/report?datasets[0]=public-global-presence:latest",
                          "status": "running",
                          "lastUpdate": "2026-04-17T17:00:00+0000"
                        }
                        """),
                response(HttpStatus.OK, reportBody(11.2, -75.4, 18.5, 4))
        ));

        GlobalFishingWatchClient client = new GlobalFishingWatchClient(
                WebClient.builder().exchangeFunction(exchangeFunction).build(),
                configuredProperties(),
                new ObjectMapper(),
                Duration.ZERO,
                ignored -> { }
        );

        List<GlobalFishingWatchClient.PresenceCell> cells = client.fetchPresenceCells(region());

        assertThat(cells).hasSize(1);
        assertThat(cells.get(0).latitude()).isEqualTo(11.2);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldRetrySubmissionAfterConcurrentReport429() {
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> Mono.just(responseFor(request, calls.getAndIncrement(),
                response(HttpStatus.TOO_MANY_REQUESTS, """
                        {
                          "statusCode": 429,
                          "error": "Too Many Requests",
                          "messages": [
                            {
                              "title": "Too Many Requests",
                              "detail": "Only one concurrent report is allowed"
                            }
                          ]
                        }
                        """),
                response(HttpStatus.OK, """
                        {
                          "uri": "/v3/4wings/report?datasets[0]=public-global-presence:latest",
                          "status": "running",
                          "lastUpdate": "2026-04-17T17:00:00+0000"
                        }
                        """),
                response(HttpStatus.OK, reportBody(1.0, -1.0, 2.0, 1)),
                response(HttpStatus.OK, reportBody(9.1, -79.6, 22.0, 5))
        ));

        GlobalFishingWatchClient client = new GlobalFishingWatchClient(
                WebClient.builder().exchangeFunction(exchangeFunction).build(),
                configuredProperties(),
                new ObjectMapper(),
                Duration.ZERO,
                ignored -> { }
        );

        List<GlobalFishingWatchClient.PresenceCell> cells = client.fetchPresenceCells(region());

        assertThat(cells).hasSize(1);
        assertThat(cells.get(0).latitude()).isEqualTo(9.1);
        assertThat(calls.get()).isEqualTo(4);
    }

    private static ClientResponse responseFor(ClientRequest request, int index, ClientResponse... responses) {
        if (index >= responses.length) {
            throw new AssertionError("Unexpected request: " + request.method() + " " + request.url());
        }
        if (index == 1 || index == 2) {
            assertThat(request.url().getPath()).isEqualTo("/v3/4wings/last-report");
        }
        if (index == 0 || index == 3) {
            assertThat(request.url().getPath()).isEqualTo("/v3/4wings/report");
        }
        return responses[index];
    }

    private static ClientResponse response(HttpStatus status, String body) {
        ClientResponse.Builder builder = ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (body != null) {
            builder.body(body);
        }
        return builder.build();
    }

    private static String reportBody(double lat, double lon, double hours, int vesselIds) {
        return """
                {
                  "entries": [
                    {
                      "public-global-presence:latest": [
                        {
                          "lat": %s,
                          "lon": %s,
                          "hours": %s,
                          "vesselIDs": %s
                        }
                      ]
                    }
                  ]
                }
                """.formatted(lat, lon, hours, vesselIds);
    }

    private static GlobalFishingWatchProperties configuredProperties() {
        GlobalFishingWatchProperties properties = new GlobalFishingWatchProperties();
        properties.setEnabled(true);
        properties.setApiToken("test-token");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setRegions(List.of(region()));
        return properties;
    }

    private static GlobalFishingWatchProperties.RegionProperties region() {
        return new GlobalFishingWatchProperties.RegionProperties(
                "caribbean-panama",
                "Caribbean and Panama",
                5.0,
                -90.0,
                27.0,
                -58.0
        );
    }
}
