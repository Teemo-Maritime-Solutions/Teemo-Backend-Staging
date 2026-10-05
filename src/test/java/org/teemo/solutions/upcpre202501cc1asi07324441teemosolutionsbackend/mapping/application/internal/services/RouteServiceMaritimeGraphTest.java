package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlay;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlayProvider;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeNetworkCatalog;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteCalculatorServiceImpl;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteGraphBuilder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates.AStarPathfinder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.NavigationConditionsProvider;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.RouteCalculatorService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.SafetyValidator;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.RouteRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.MaritimeWaypointResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteCalculationResource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteServiceMaritimeGraphTest {

    private static final String TOKYO_ID = "PORT-TOKYO";
    private static final String HONOLULU_ID = "PORT-HONOLULU";
    private static final String CALLAO_ID = "PORT-CALLAO";
    private static final String NEW_YORK_ID = "PORT-NEW-YORK";

    private PortRepository portRepository;
    private RouteService routeService;
    private GeoUtils geoUtils;
    private MaritimeLandMask landMask;

    @BeforeEach
    void setUp() {
        portRepository = mock(PortRepository.class);
        RouteRepository routeRepository = mock(RouteRepository.class);
        SafetyValidator safetyValidator = mock(SafetyValidator.class);
        NavigationConditionsProvider navigationConditionsProvider = mock(NavigationConditionsProvider.class);
        RouteHistoryService routeHistoryService = mock(RouteHistoryService.class);
        RoutePopularityService routePopularityService = mock(RoutePopularityService.class);

        geoUtils = new GeoUtils();
        landMask = new MaritimeLandMask(geoUtils);
        MaritimeLandMask routingLandMask = new MaritimeLandMask(geoUtils) {
            @Override
            public boolean isOnLand(Coordinates point) { return false; } // Synthetic topology fixture, not coastal evidence.
            @Override
            public boolean crossesLand(List<Coordinates> path) {
                return false;
            }
        };
        PortMapper portMapper = new PortMapper();
        RouteGraphBuilder routeGraphBuilder = new RouteGraphBuilder(
                portRepository,
                portMapper,
                new MaritimeNetworkCatalog(),
                geoUtils,
                routingLandMask,
                overlayProvider()
        );
        AStarPathfinder pathfinder = new AStarPathfinder(
                safetyValidator,
                navigationConditionsProvider,
                geoUtils,
                Clock.systemUTC()
        );
        RouteCalculatorService routeCalculatorService = new RouteCalculatorServiceImpl(pathfinder, routeGraphBuilder, geoUtils);

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
        when(routeRepository.findByHomePortAndDestinationPort(anyString(), anyString())).thenReturn(Optional.empty());
        when(portRepository.findByDisabled(true)).thenReturn(List.of());

        PortDocument tokyo = portDocument(TOKYO_ID, "Tokyo", 35.651981, 139.764228, "Asia");
        PortDocument honolulu = portDocument(HONOLULU_ID, "Honolulu", 21.3069, -157.8583, "America");
        PortDocument callao = portDocument(CALLAO_ID, "Callao", -12.051012, -77.154106, "America");
        PortDocument newYork = portDocument(NEW_YORK_ID, "New York", 40.7128, -74.0060, "America");

        when(portRepository.findAll()).thenReturn(List.of(tokyo, honolulu, callao, newYork));
        when(portRepository.findById(TOKYO_ID)).thenReturn(Optional.of(tokyo));
        when(portRepository.findById(HONOLULU_ID)).thenReturn(Optional.of(honolulu));
        when(portRepository.findById(CALLAO_ID)).thenReturn(Optional.of(callao));
        when(portRepository.findById(NEW_YORK_ID)).thenReturn(Optional.of(newYork));
    }

    @Test
    void shouldCalculateRouteUsingAisOverlayWaypoints() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(TOKYO_ID, CALLAO_ID);

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Callao");
        assertThat(response.totalDistance()).isGreaterThan(0.0);
        assertThat(response.metadata().graphMode()).isEqualTo("AIS_OVERLAY");
        assertThat(response.metadata().waypoints()).isNotEmpty();
        assertThat(response.metadata().waypoints()).allMatch(waypoint -> waypoint.id().startsWith("GFW:"));

        List<Double> firstGeometryPoint = response.metadata().geometry().get(0);
        List<Double> lastGeometryPoint = response.metadata().geometry().get(response.metadata().geometry().size() - 1);

        assertThat(geoUtils.calculateHaversineDistanceNm(
                new Coordinates(firstGeometryPoint.get(1), firstGeometryPoint.get(0)),
                new Coordinates(35.651981, 139.764228)
        )).isLessThan(120.0);
        assertThat(geoUtils.calculateHaversineDistanceNm(
                new Coordinates(lastGeometryPoint.get(1), lastGeometryPoint.get(0)),
                new Coordinates(-12.051012, -77.154106)
        )).isLessThan(120.0);
        assertThat(response.metadata().geometry().subList(1, response.metadata().geometry().size() - 1))
                .allMatch(point -> !landMask.isOnLand(new Coordinates(point.get(1), point.get(0))));
    }

    @Test
    void shouldHonorMandatoryIntermediatePort() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(HONOLULU_ID),
                true,
                Set.of(),
                null
        );

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Honolulu", "Callao");
        assertThat(response.metadata().portIds()).containsExactly(TOKYO_ID, HONOLULU_ID, CALLAO_ID);
        assertThat(response.metadata().requestedViaPortIds()).containsExactly(HONOLULU_ID);
        assertThat(response.metadata().appliedViaPortIds()).containsExactly(HONOLULU_ID);
        assertThat(response.warnings()).contains("La ruta incluye puertos intermedios obligatorios.");
    }

    @Test
    void shouldIgnoreIntermediatePortsUnlessExplicitlyEnforced() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(HONOLULU_ID),
                false,
                Set.of(),
                null
        );

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Callao");
        assertThat(response.metadata().portIds()).containsExactly(TOKYO_ID, CALLAO_ID);
        assertThat(response.metadata().requestedViaPortIds()).containsExactly(HONOLULU_ID);
        assertThat(response.metadata().appliedViaPortIds()).isEmpty();
        assertThat(response.warnings()).contains("Se ignoraron puertos intermedios porque no se solicitaron como obligatorios.");
    }

    @Test
    void shouldRejectBlockedMandatoryIntermediatePort() {
        assertThatThrownBy(() -> routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(HONOLULU_ID),
                true,
                Set.of(HONOLULU_ID),
                null
        )).isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("intermedio");
    }

    @Test
    void shouldKeepGeometryAndWaypointsInMatchingOrder() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(TOKYO_ID, CALLAO_ID);

        List<List<Double>> geometry = response.metadata().geometry();
        List<MaritimeWaypointResource> waypoints = response.metadata().waypoints();

        assertThat(geometry.size()).isGreaterThan(waypoints.size() + 1);
        int geometryCursor = 1;
        for (MaritimeWaypointResource waypoint : waypoints) {
            while (geometryCursor < geometry.size() - 1
                    && !geometry.get(geometryCursor).equals(List.of(waypoint.longitude(), waypoint.latitude()))) {
                geometryCursor++;
            }
            assertThat(geometryCursor).isLessThan(geometry.size());
        }
    }

    @Test
    void shouldCalculateNewYorkToCallaoThroughCatalogBackboneWhenAisRegionsAreDisconnected() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(NEW_YORK_ID, CALLAO_ID);

        assertThat(response.optimalRoute()).containsExactly("New York", "Callao");
        assertThat(response.totalDistance()).isGreaterThan(0.0);
        assertThat(response.metadata().graphMode()).isEqualTo("AIS_OVERLAY");
        assertThat(response.metadata().waypoints()).extracting(MaritimeWaypointResource::id)
                .contains("US_EAST_COAST", "PANAMA_CANAL", "PERU_APPROACH");
    }

    private MaritimeCorridorOverlayProvider overlayProvider() {
        MaritimeNode tokyoApproach = overlayNode("GFW:north-pacific:tokyo-approach", 34.7, 141.2);
        MaritimeNode pacificWest = overlayNode("GFW:north-pacific:central-west", 31.0, 170.0);
        MaritimeNode hawaiiApproach = overlayNode("GFW:north-pacific:hawaii-approach", 21.0, -158.7);
        MaritimeNode pacificEast = overlayNode("GFW:east-pacific:north", 8.0, -130.0);
        MaritimeNode pacificSouth = overlayNode("GFW:east-pacific:south", -2.0, -105.0);
        MaritimeNode callaoApproach = overlayNode("GFW:peru-ecuador-coast:callao-approach", -12.5, -78.4);
        MaritimeNode atlanticApproach = overlayNode("GFW:atlantic-north-west:new-york-approach", 29.0, -74.5);

        MaritimeCorridorOverlay overlay = new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.parse("2026-04-21T08:00:00Z"),
                List.of(tokyoApproach, pacificWest, hawaiiApproach, pacificEast, pacificSouth, callaoApproach, atlanticApproach),
                List.of(
                        edge(tokyoApproach, pacificWest),
                        edge(pacificWest, hawaiiApproach),
                        edge(hawaiiApproach, pacificEast),
                        edge(pacificEast, pacificSouth),
                        edge(pacificSouth, callaoApproach)
                ),
                List.of()
        );

        return new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay;
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay;
            }
        };
    }

    private MaritimeNetworkCatalog.EdgeDefinition edge(MaritimeNode from, MaritimeNode to) {
        return new MaritimeNetworkCatalog.EdgeDefinition(from.getId(), to.getId(), false, false, false);
    }

    private MaritimeNode overlayNode(String id, double latitude, double longitude) {
        return MaritimeNode.seaNode(id, id, MaritimeNodeType.SEA_WAYPOINT, new Coordinates(latitude, longitude));
    }

    private PortDocument portDocument(String id, String name, double latitude, double longitude, String continent) {
        PortDocument document = new PortDocument();
        document.setId(id);
        document.setName(name);
        document.setCoordinates(new PortDocument.CoordinatesDocument(latitude, longitude));
        document.setContinent(continent);
        document.setDisabled(false);
        return document;
    }
}
