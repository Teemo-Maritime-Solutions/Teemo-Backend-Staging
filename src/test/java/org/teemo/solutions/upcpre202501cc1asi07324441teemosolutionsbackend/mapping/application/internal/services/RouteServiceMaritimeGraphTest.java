package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeNetworkCatalog;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlay;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlayProvider;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteCalculatorServiceImpl;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteGraphBuilder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates.AStarPathfinder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
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

    private static final String TOKYO_ID = "PORT-1";
    private static final String BALBOA_ID = "PORT-7";
    private static final String CALLAO_ID = "PORT-2";
    private static final String COLON_ID = "PORT-8";
    private static final String LISBOA_ID = "PORT-9";
    private static final String PUERTO_MONTT_ID = "PORT-10";
    private static final String USHUAIA_ID = "PORT-11";
    private static final String LAGOS_ID = "PORT-12";

    private PortRepository portRepository;
    private RouteRepository routeRepository;
    private RouteService routeService;
    private GeoUtils geoUtils;
    private MaritimeLandMask landMask;

    @BeforeEach
    void setUp() {
        portRepository = mock(PortRepository.class);
        routeRepository = mock(RouteRepository.class);
        SafetyValidator safetyValidator = mock(SafetyValidator.class);
        NavigationConditionsProvider navigationConditionsProvider = mock(NavigationConditionsProvider.class);
        RouteHistoryService routeHistoryService = mock(RouteHistoryService.class);
        RoutePopularityService routePopularityService = mock(RoutePopularityService.class);

        geoUtils = new GeoUtils();
        landMask = new MaritimeLandMask(geoUtils);
        PortMapper portMapper = new PortMapper();
        MaritimeNetworkCatalog networkCatalog = new MaritimeNetworkCatalog();
        RouteGraphBuilder routeGraphBuilder = new RouteGraphBuilder(portRepository, portMapper, networkCatalog, geoUtils, landMask, new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return MaritimeCorridorOverlay.empty("STATIC");
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return MaritimeCorridorOverlay.empty("STATIC");
            }
        });
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

        when(safetyValidator.getUnsafePortNames()).thenReturn(Set.of());
        when(safetyValidator.validateFullRoute(anyList())).thenReturn(List.of());
        when(routeRepository.findByHomePortAndDestinationPort(anyString(), anyString())).thenReturn(Optional.empty());
        when(portRepository.findByDisabled(true)).thenReturn(List.of());

        PortDocument tokyo = portDocument(TOKYO_ID, "Tokyo", 35.651981, 139.764228, "Asia", false);
        PortDocument balboa = portDocument(BALBOA_ID, "Balboa", 8.939008, -79.555637, "America", false);
        PortDocument callao = portDocument(CALLAO_ID, "Callao", -12.051012, -77.154106, "America", false);
        PortDocument colon = portDocument(COLON_ID, "Colón", 9.359174, -79.901389, "America", false);

        PortDocument lisboa = portDocument(LISBOA_ID, "Lisboa", 38.7223, -9.1393, "Europe", false);
        PortDocument puertoMontt = portDocument(PUERTO_MONTT_ID, "Puerto Montt", -41.4718, -72.9396, "America", false);
        PortDocument ushuaia = portDocument(USHUAIA_ID, "Ushuaia", -54.810668, -68.296487, "America", false);
        PortDocument lagos = portDocument(LAGOS_ID, "Lagos", 6.4541, 3.3947, "Africa", false);

        List<PortDocument> ports = List.of(tokyo, balboa, callao, colon, lisboa, puertoMontt, ushuaia, lagos);
        when(portRepository.findAll()).thenReturn(ports);
        when(portRepository.findById(TOKYO_ID)).thenReturn(Optional.of(tokyo));
        when(portRepository.findById(BALBOA_ID)).thenReturn(Optional.of(balboa));
        when(portRepository.findById(CALLAO_ID)).thenReturn(Optional.of(callao));
        when(portRepository.findById(COLON_ID)).thenReturn(Optional.of(colon));
        when(portRepository.findById(LISBOA_ID)).thenReturn(Optional.of(lisboa));
        when(portRepository.findById(PUERTO_MONTT_ID)).thenReturn(Optional.of(puertoMontt));
        when(portRepository.findById(USHUAIA_ID)).thenReturn(Optional.of(ushuaia));
        when(portRepository.findById(LAGOS_ID)).thenReturn(Optional.of(lagos));
    }

    @Test
    void shouldCalculateRouteUsingMaritimeWaypoints() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(TOKYO_ID, CALLAO_ID);

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Callao");
        assertThat(response.totalDistance()).isGreaterThan(0.0);
        assertThat(response.metadata().waypoints()).isNotEmpty();

        List<Double> firstGeometryPoint = response.metadata().geometry().get(0);
        List<Double> lastGeometryPoint = response.metadata().geometry().get(response.metadata().geometry().size() - 1);

        assertThat(landMask.isOnLand(new Coordinates(
                firstGeometryPoint.get(1),
                firstGeometryPoint.get(0)
        ))).isFalse();
        assertThat(landMask.isOnLand(new Coordinates(
                lastGeometryPoint.get(1),
                lastGeometryPoint.get(0)
        ))).isFalse();
        assertThat(geoUtils.calculateHaversineDistanceNm(
                new Coordinates(firstGeometryPoint.get(1), firstGeometryPoint.get(0)),
                new Coordinates(35.651981, 139.764228)
        )).isLessThan(80.0);
        assertThat(geoUtils.calculateHaversineDistanceNm(
                new Coordinates(lastGeometryPoint.get(1), lastGeometryPoint.get(0)),
                new Coordinates(-12.051012, -77.154106)
        )).isLessThan(80.0);
    }

    @Test
    void shouldHonorMandatoryIntermediatePort() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(BALBOA_ID),
                true,
                Set.of(),
                null
        );

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Balboa", "Callao");
        assertThat(response.metadata().portIds()).containsExactly(TOKYO_ID, BALBOA_ID, CALLAO_ID);
        assertThat(response.metadata().requestedViaPortIds()).containsExactly(BALBOA_ID);
        assertThat(response.metadata().appliedViaPortIds()).containsExactly(BALBOA_ID);
        assertThat(response.warnings()).contains("La ruta incluye puertos intermedios obligatorios.");
    }

    @Test
    void shouldIgnoreIntermediatePortsUnlessExplicitlyEnforced() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(BALBOA_ID),
                false,
                Set.of(),
                null
        );

        assertThat(response.optimalRoute()).containsExactly("Tokyo", "Callao");
        assertThat(response.metadata().portIds()).containsExactly(TOKYO_ID, CALLAO_ID);
        assertThat(response.metadata().requestedViaPortIds()).containsExactly(BALBOA_ID);
        assertThat(response.metadata().appliedViaPortIds()).isEmpty();
        assertThat(response.warnings()).contains("Se ignoraron puertos intermedios porque no se solicitaron como obligatorios.");
    }

    @Test
    void shouldRejectBlockedMandatoryIntermediatePort() {
        assertThatThrownBy(() -> routeService.calculateOptimalRoute(
                TOKYO_ID,
                CALLAO_ID,
                List.of(BALBOA_ID),
                true,
                Set.of(BALBOA_ID),
                null
        )).isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("intermedio");
    }

    @Test
    void shouldKeepGeometryAndWaypointsInMatchingOrder() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(TOKYO_ID, CALLAO_ID);

        List<List<Double>> geometry = response.metadata().geometry();
        List<MaritimeWaypointResource> waypoints = response.metadata().waypoints();

        assertThat(geometry.size()).isGreaterThan(waypoints.size() + 2);
        int geometryCursor = 1;
        for (MaritimeWaypointResource waypoint : waypoints) {
            while (geometryCursor < geometry.size() - 1
                    && !geometry.get(geometryCursor).equals(List.of(waypoint.longitude(), waypoint.latitude()))) {
                geometryCursor++;
            }
            assertThat(geometryCursor).isLessThan(geometry.size() - 1);
        }
    }

    @Test
    void shouldUseDetailedPanamaCanalCorridorBetweenBalboaAndColon() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(BALBOA_ID, COLON_ID);

        List<String> waypointIds = response.metadata().waypoints().stream()
                .map(MaritimeWaypointResource::id)
                .toList();

        assertThat(waypointIds).containsSubsequence(
                "PANAMA_BALBOA_INNER",
                "PANAMA_MIRAFLORES_LOCKS",
                "PANAMA_PEDRO_MIGUEL_LOCKS",
                "PANAMA_GAILLARD_CUT_SOUTH",
                "PANAMA_CANAL",
                "PANAMA_GAILLARD_CUT_NORTH",
                "PANAMA_GATUN_LAKE_SOUTH",
                "PANAMA_GATUN_LAKE_CENTER",
                "PANAMA_GATUN_LAKE_NORTH",
                "PANAMA_GATUN_LOCKS",
                "PANAMA_COLON_INNER"
        );
        assertThat(response.metadata().geometry())
                .contains(List.of(-79.579, 8.982))
                .contains(List.of(-79.648, 9.108))
                .contains(List.of(-79.886, 9.334));
        assertThat(waypointIds).doesNotContain("GFW:caribbean-panama-west:9_10:m79_60");
    }

    @Test
    void shouldRouteCallaoToLisboaWithoutTraversingIntermediatePortsOrCapeHorn() {
        RouteCalculationResource response = routeService.calculateOptimalRoute(CALLAO_ID, LISBOA_ID);

        assertThat(response.optimalRoute()).containsExactly("Callao", "Lisboa");
        assertThat(response.metadata().portIds()).containsExactly(CALLAO_ID, LISBOA_ID);

        List<String> waypointIds = response.metadata().waypoints().stream()
                .map(MaritimeWaypointResource::id)
                .toList();

        assertThat(waypointIds).containsSubsequence(
                "PANAMA_PACIFIC_OUTER",
                "PANAMA_BALBOA_INNER",
                "PANAMA_MIRAFLORES_LOCKS",
                "PANAMA_PEDRO_MIGUEL_LOCKS",
                "PANAMA_GAILLARD_CUT_SOUTH",
                "PANAMA_CANAL",
                "PANAMA_GAILLARD_CUT_NORTH",
                "PANAMA_GATUN_LAKE_SOUTH",
                "PANAMA_GATUN_LAKE_CENTER",
                "PANAMA_GATUN_LAKE_NORTH",
                "PANAMA_GATUN_LOCKS",
                "PANAMA_COLON_INNER",
                "PANAMA_ATLANTIC",
                "PANAMA_CARIBBEAN_OUTER"
        );
        assertThat(waypointIds).doesNotContain("CAPE_HORN_WEST", "CAPE_HORN", "CAPE_HORN_EAST");
        assertThat(waypointIds).doesNotContain(
                "PACIFIC_TROPICAL_EAST",
                "BAJA_OFFSHORE",
                "CALIFORNIA_OFFSHORE",
                "NORTH_PACIFIC_EAST",
                "NORTH_PACIFIC_CENTRAL",
                "NORTH_PACIFIC_WEST",
                "JAPAN_EAST_APPROACH",
                "JAPAN_SOUTH_APPROACH",
                "CELEBES_SEA",
                "INDIAN_OCEAN_EAST",
                "CAPE_GOOD_HOPE"
        );
        assertThat(waypointIds.stream().filter("PANAMA_PACIFIC_OUTER"::equals).count()).isEqualTo(1);
        assertThat(waypointIds.stream().filter("PANAMA_ATLANTIC"::equals).count()).isEqualTo(1);
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
