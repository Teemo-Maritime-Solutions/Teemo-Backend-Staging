package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RoutePath;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.RouteCalculatorService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.SafetyValidator;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RouteDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.RouteRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteRecalculationResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteServiceRecalculationTest {

    private static final String TOKYO_ID = "PORT-1";
    private static final String BALBOA_ID = "PORT-7";
    private static final String CALLAO_ID = "PORT-2";

    private RouteRepository routeRepository;
    private PortRepository portRepository;
    private RouteCalculatorService routeCalculatorService;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeRepository = mock(RouteRepository.class);
        portRepository = mock(PortRepository.class);
        routeCalculatorService = mock(RouteCalculatorService.class);
        SafetyValidator safetyValidator = mock(SafetyValidator.class);
        RouteHistoryService routeHistoryService = mock(RouteHistoryService.class);
        RoutePopularityService routePopularityService = mock(RoutePopularityService.class);

        GeoUtils geoUtils = new GeoUtils();
        PortMapper portMapper = new PortMapper();
        routeService = new RouteService(
                routeRepository,
                portRepository,
                routeCalculatorService,
                safetyValidator,
                geoUtils,
                portMapper,
                routeHistoryService,
                routePopularityService
        );

        when(safetyValidator.validateFullRoute(anyList())).thenReturn(List.of());

        PortDocument tokyoDocument = portDocument(TOKYO_ID, "Tokyo", 35.651981, 139.764228, "Asia", false);
        PortDocument callaoDocument = portDocument(CALLAO_ID, "Callao", -12.0564, -77.1319, "America", false);
        PortDocument balboaDocument = portDocument(BALBOA_ID, "Balboa", 8.939008, -79.555637, "America", true);
        balboaDocument.setDisabledReason("maintenance");
        balboaDocument.setDisabledAt(Instant.parse("2026-04-16T10:00:00Z"));

        when(routeRepository.findById("route-1")).thenReturn(Optional.of(routeDocument()));
        when(portRepository.findByNameAndContinent("Tokyo", "Asia")).thenReturn(Optional.of(tokyoDocument));
        when(portRepository.findByNameAndContinent("Callao", "America")).thenReturn(Optional.of(callaoDocument));
        when(portRepository.findById(TOKYO_ID)).thenReturn(Optional.of(tokyoDocument));
        when(portRepository.findById(CALLAO_ID)).thenReturn(Optional.of(callaoDocument));
        when(portRepository.findById(BALBOA_ID)).thenReturn(Optional.of(balboaDocument));
        when(portRepository.findByDisabled(true)).thenReturn(List.of(balboaDocument));

        Port tokyo = portMapper.toDomain(tokyoDocument);
        Port callao = portMapper.toDomain(callaoDocument);
        Port balboa = new Port(
                BALBOA_ID,
                "Balboa",
                new Coordinates(8.939008, -79.555637),
                "America",
                true,
                "maintenance",
                Instant.parse("2026-04-16T10:00:00Z"),
                "ops"
        );

        RoutePath currentPath = pathWithIntermediatePort(tokyo, balboa, callao);
        RoutePath reroutedPath = directPath(tokyo, callao);

        when(routeCalculatorService.calculateOptimalRoute(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Set<String> avoidedPortIds = invocation.getArgument(2);
                    return avoidedPortIds != null && avoidedPortIds.contains(BALBOA_ID) ? reroutedPath : currentPath;
                });
    }

    @Test
    void shouldRecalculateRouteAvoidingDisabledPort() {
        RouteRecalculationResource response = routeService.recalculateRouteAvoidingDisabledPorts("route-1");

        assertThat(response.recalculated()).isTrue();
        assertThat(response.avoidedPortIds()).containsExactly(BALBOA_ID);
        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Callao");
        assertThat(response.metadata().geometry()).isNotEmpty();
    }

    @Test
    void shouldDeduplicateConcurrentIdenticalRouteCalculations() throws Exception {
        CountDownLatch firstInvocationStarted = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();

        Port tokyo = new Port(TOKYO_ID, "Tokyo", new Coordinates(35.651981, 139.764228), "Asia");
        Port callao = new Port(CALLAO_ID, "Callao", new Coordinates(-12.0564, -77.1319), "America");
        RoutePath reroutedPath = directPath(tokyo, callao);

        when(routeCalculatorService.calculateOptimalRoute(any(), any(), anySet()))
                .thenAnswer(invocation -> {
                    invocationCount.incrementAndGet();
                    firstInvocationStarted.countDown();
                    Thread.sleep(750);
                    return reroutedPath;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<String>> task = () -> routeService.calculateOptimalRoute(TOKYO_ID, CALLAO_ID).optimalRoute();
            Future<List<String>> first = executor.submit(task);
            assertThat(firstInvocationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<List<String>> second = executor.submit(task);

            assertThat(first.get(2, TimeUnit.SECONDS)).containsExactly("Tokyo", "Callao");
            assertThat(second.get(2, TimeUnit.SECONDS)).containsExactly("Tokyo", "Callao");
            assertThat(invocationCount.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        verify(routeCalculatorService, times(1)).calculateOptimalRoute(any(), any(), anySet());
    }

    private RoutePath pathWithIntermediatePort(Port start, Port middle, Port end) {
        MaritimeNode startNode = MaritimeNode.forPort(start);
        MaritimeNode canalNode = MaritimeNode.seaNode(
                "PANAMA_CANAL",
                "Panama Canal Transit",
                MaritimeNodeType.CANAL,
                new Coordinates(9.15, -79.75)
        );
        MaritimeNode middleNode = MaritimeNode.forPort(middle);
        MaritimeNode endNode = MaritimeNode.forPort(end);

        return new RoutePath(
                List.of(startNode, canalNode, middleNode, endNode),
                List.of(start, middle, end),
                List.of(canalNode),
                List.of(start.getCoordinates(), canalNode.getCoordinates(), middle.getCoordinates(), end.getCoordinates()),
                8230.5,
                457.0,
                List.of()
        );
    }

    private RoutePath directPath(Port start, Port end) {
        MaritimeNode startNode = MaritimeNode.forPort(start);
        MaritimeNode waypointNode = MaritimeNode.seaNode(
                "PACIFIC_TROPICAL_EAST",
                "Eastern Tropical Pacific Corridor",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(12.0, -100.0)
        );
        MaritimeNode endNode = MaritimeNode.forPort(end);

        return new RoutePath(
                List.of(startNode, waypointNode, endNode),
                List.of(start, end),
                List.of(waypointNode),
                List.of(start.getCoordinates(), waypointNode.getCoordinates(), end.getCoordinates()),
                7800.0,
                430.0,
                List.of()
        );
    }

    private RouteDocument routeDocument() {
        RouteDocument document = new RouteDocument();
        document.setId("route-1");
        document.setHomePort("Tokyo");
        document.setHomePortContinent("Asia");
        document.setDestinationPort("Callao");
        document.setDestinationPortContinent("America");
        document.setDistance(8230.5);
        return document;
    }

    private PortDocument portDocument(String id, String name, double latitude, double longitude, String continent, boolean disabled) {
        PortDocument document = new PortDocument();
        document.setId(id);
        document.setName(name);
        document.setCoordinates(new PortDocument.CoordinatesDocument(latitude, longitude));
        document.setContinent(continent);
        document.setDisabled(disabled);
        return document;
    }
}
