package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteGraphBuilderOverlayTest {

    @Test
    void shouldMergeAisOverlayNodesIntoDynamicGraph() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        MaritimeNode west = overlayNode("GFW:pacific:test:west", 17.5, -148.0);
        MaritimeNode east = overlayNode("GFW:pacific:test:east", 8.0, -110.0);
        MaritimeCorridorOverlay overlay = overlayOf(
                List.of(west, east),
                List.of(new MaritimeNetworkCatalog.EdgeDefinition(west.getId(), east.getId(), false, false, false))
        );

        RouteGraphBuilder builder = builder(portRepository, alwaysWaterMask(), overlay);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());

        assertThat(graph.findNode(west.getId())).isPresent();
        assertThat(graph.findNode(east.getId())).isPresent();
        assertThat(graph.findEdge(west, east)).isPresent();
    }

    @Test
    void shouldIgnoreOverlayEdgesThatReferenceNodesOutsideTheAisOverlay() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        MaritimeNode onlyNode = overlayNode("GFW:single:test", 9.2, -79.8);
        MaritimeCorridorOverlay overlay = overlayOf(
                List.of(onlyNode),
                List.of(new MaritimeNetworkCatalog.EdgeDefinition(onlyNode.getId(), "PANAMA_PACIFIC", false, false, false))
        );

        RouteGraphBuilder builder = builder(portRepository, alwaysWaterMask(), overlay);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());

        assertThat(graph.findNode(onlyNode.getId())).isPresent();
        assertThat(graph.findNode("PANAMA_PACIFIC")).isEmpty();
        assertThat(graph.getAdjacentEdges(onlyNode)).isEmpty();
    }

    @Test
    void shouldConnectPortsToNearestNavigableAisNodes() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = portDocument("CALLAO-ID", "Callao", -12.0564, -77.1319);
        when(portRepository.findAll()).thenReturn(List.of(callao));

        MaritimeNode nearNode = overlayNode("GFW:peru:test:near", -12.30, -78.10);
        MaritimeNode farNode = overlayNode("GFW:peru:test:far", -9.00, -85.00);
        MaritimeCorridorOverlay overlay = overlayOf(
                List.of(nearNode, farNode),
                List.of(new MaritimeNetworkCatalog.EdgeDefinition(nearNode.getId(), farNode.getId(), false, false, false))
        );

        RouteGraphBuilder builder = builder(portRepository, alwaysWaterMask(), overlay);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode callaoPort = graph.findPortNode("CALLAO-ID").orElseThrow();

        assertThat(graph.findEdge(callaoPort, nearNode)).isPresent();
        assertThat(graph.findEdge(callaoPort, farNode)).isPresent();
    }

    @Test
    void shouldConnectNewYorkThroughCatalogConnectorWhenAisOverlayHasNoNearbyEastCoastCell() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument newYork = portDocument("NEW-YORK-ID", "New York", 40.7128, -74.0060);
        PortDocument callao = portDocument("CALLAO-ID", "Callao", -12.0564, -77.1319);
        when(portRepository.findAll()).thenReturn(List.of(newYork, callao));

        MaritimeNode oldAtlanticCell = overlayNode("GFW:atlantic-north-west:test", 29.0, -74.5);
        MaritimeNode callaoApproach = overlayNode("GFW:peru-ecuador-coast:callao", -12.4, -78.4);
        MaritimeCorridorOverlay overlay = overlayOf(
                List.of(oldAtlanticCell, callaoApproach),
                List.of(new MaritimeNetworkCatalog.EdgeDefinition(oldAtlanticCell.getId(), callaoApproach.getId(), false, false, false))
        );

        MaritimeLandMask mask = new MaritimeLandMask(new GeoUtils()) {
            @Override
            public boolean crossesLand(List<Coordinates> path) {
                return path.stream().anyMatch(point -> point.equals(new Coordinates(40.7128, -74.0060)));
            }

            @Override
            public boolean isOnLand(Coordinates coordinates) {
                return false;
            }
        };
        RouteGraphBuilder builder = builder(portRepository, mask, overlay);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode newYorkPort = graph.findPortNode("NEW-YORK-ID").orElseThrow();
        MaritimeNode eastCoastConnector = graph.findNode("US_EAST_COAST").orElseThrow();

        assertThat(graph.findEdge(newYorkPort, oldAtlanticCell)).isEmpty();
        assertThat(graph.findEdge(newYorkPort, eastCoastConnector)).isPresent();
        assertThat(graph.findEdge(eastCoastConnector, graph.findNode("NORTH_ATLANTIC_WEST").orElseThrow())).isPresent();
    }

    @Test
    void shouldNotUseCatalogBackboneWhenDisabled() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = portDocument("CALLAO-ID", "Callao", -12.0564, -77.1319);
        when(portRepository.findAll()).thenReturn(List.of(callao));

        MaritimeNode callaoAisCell = overlayNode("GFW:peru-ecuador-coast:callao", -12.30, -78.10);
        MaritimeCorridorOverlay overlay = overlayOf(List.of(callaoAisCell), List.of());

        RouteGraphBuilder builder = builder(portRepository, alwaysWaterMask(), overlay);
        ReflectionTestUtils.setField(builder, "catalogBackboneEnabled", false);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode callaoPort = graph.findPortNode("CALLAO-ID").orElseThrow();

        assertThat(graph.findNode("PERU_APPROACH")).isEmpty();
        assertThat(graph.findEdge(callaoPort, callaoAisCell)).isPresent();
    }

    @Test
    void shouldUseCatalogBackboneWithoutBlockingOnAisRefreshWhenOverlayIsEmpty() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument gotoPort = portDocument("GOTO-ID", "Goto", 32.6953, 128.8419);
        gotoPort.setContinent("Asia");
        PortDocument shanghai = portDocument("SHANGHAI-ID", "Shanghai", 31.2304, 121.4737);
        shanghai.setContinent("Asia");
        when(portRepository.findAll()).thenReturn(List.of(gotoPort, shanghai));

        AtomicInteger refreshCalls = new AtomicInteger();
        MaritimeCorridorOverlay emptyOverlay = MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH");
        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                alwaysWaterMask(),
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return emptyOverlay;
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        refreshCalls.incrementAndGet();
                        return emptyOverlay;
                    }
                }
        );

        RouteGraph graph = builder.buildDynamicRouteGraphForPorts(Set.of(), Set.of("GOTO-ID", "SHANGHAI-ID"));
        MaritimeNode gotoNode = graph.findPortNode("GOTO-ID").orElseThrow();
        MaritimeNode shanghaiNode = graph.findPortNode("SHANGHAI-ID").orElseThrow();

        assertThat(refreshCalls).hasValue(0);
        assertThat(graph.findEdge(gotoNode, graph.findNode("JAPAN_EAST_APPROACH").orElseThrow())).isPresent();
        assertThat(graph.findEdge(shanghaiNode, graph.findNode("EAST_CHINA_SEA_COAST").orElseThrow())).isPresent();
    }

    @Test
    void shouldSkipPortConnectionsThatCrossLand() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = portDocument("CALLAO-ID", "Callao", -12.0564, -77.1319);
        when(portRepository.findAll()).thenReturn(List.of(callao));

        MaritimeNode blockedNode = overlayNode("GFW:peru:test:blocked", -11.8, -76.9);
        MaritimeNode safeNode = overlayNode("GFW:peru:test:safe", -12.4, -78.4);
        MaritimeCorridorOverlay overlay = overlayOf(List.of(blockedNode, safeNode), List.of());

        MaritimeLandMask mask = new MaritimeLandMask(new GeoUtils()) {
            @Override
            public boolean crossesLand(List<Coordinates> path) {
                return path.contains(blockedNode.getCoordinates());
            }
        };

        RouteGraphBuilder builder = builder(portRepository, mask, overlay);

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode callaoPort = graph.findPortNode("CALLAO-ID").orElseThrow();

        assertThat(graph.findEdge(callaoPort, blockedNode)).isEmpty();
        assertThat(graph.findEdge(callaoPort, safeNode)).isPresent();
    }

    @Test
    void shouldReuseCachedDynamicGraphWhenPortsAndOverlayDoNotChange() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = portDocument("CALLAO-ID", "Callao", -12.051012, -77.154106);
        when(portRepository.findAll()).thenReturn(List.of(callao));

        MaritimeNode overlayNode = overlayNode("GFW:peru:test:cache", -12.30, -78.10);
        MaritimeCorridorOverlay overlay = new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.parse("2026-04-20T14:00:00Z"),
                List.of(overlayNode),
                List.of(),
                List.of()
        );

        RouteGraphBuilder builder = builder(portRepository, alwaysWaterMask(), overlay);

        RouteGraph firstGraph = builder.buildDynamicRouteGraph(Set.of());
        RouteGraph secondGraph = builder.buildDynamicRouteGraph(Set.of());

        assertThat(secondGraph).isSameAs(firstGraph);
        verify(portRepository, times(2)).findAll();
    }

    private RouteGraphBuilder builder(PortRepository portRepository,
                                      MaritimeLandMask landMask,
                                      MaritimeCorridorOverlay overlay) {
        return new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                landMask,
                overlayProvider(overlay)
        );
    }

    private MaritimeCorridorOverlayProvider overlayProvider(MaritimeCorridorOverlay overlay) {
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

    private MaritimeLandMask alwaysWaterMask() {
        return new MaritimeLandMask(new GeoUtils()) {
            @Override
            public boolean crossesLand(List<Coordinates> path) {
                return false;
            }
        };
    }

    private MaritimeCorridorOverlay overlayOf(List<MaritimeNode> nodes,
                                              List<MaritimeNetworkCatalog.EdgeDefinition> edges) {
        return new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.parse("2026-04-21T10:00:00Z"),
                nodes,
                edges,
                List.of()
        );
    }

    private MaritimeNode overlayNode(String id, double latitude, double longitude) {
        return MaritimeNode.seaNode(id, id, MaritimeNodeType.SEA_WAYPOINT, new Coordinates(latitude, longitude));
    }

    private PortDocument portDocument(String id, String name, double latitude, double longitude) {
        PortDocument document = new PortDocument();
        document.setId(id);
        document.setName(name);
        document.setContinent("America");
        document.setDisabled(false);
        document.setCoordinates(new PortDocument.CoordinatesDocument(latitude, longitude));
        return document;
    }
}
