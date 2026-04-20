package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteGraphBuilderOverlayTest {

    @Test
    void shouldMergeOverlayNodesIntoDynamicGraph() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        MaritimeNode overlayNode = MaritimeNode.seaNode(
                "GFW:test:1",
                "GFW Test Node",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(9.4, -79.7)
        );
        MaritimeCorridorOverlayProvider overlayProvider = new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay();
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay();
            }

            private MaritimeCorridorOverlay overlay() {
                return new MaritimeCorridorOverlay(
                        "GLOBAL_FISHING_WATCH",
                        Instant.now(),
                        List.of(overlayNode),
                        List.of(
                                new MaritimeNetworkCatalog.EdgeDefinition("GFW:test:1", "PANAMA_PACIFIC", false, false, false)
                        ),
                        List.of()
                );
            }
        };

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()) {
                    @Override
                    public boolean crossesLand(List<Coordinates> path) {
                        return false;
                    }
                },
                overlayProvider
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());

        assertThat(graph.findNode("GFW:test:1")).isPresent();
        assertThat(graph.findNode("PANAMA_PACIFIC")).isPresent();
        assertThat(graph.findEdge(graph.findNode("GFW:test:1").orElseThrow(), graph.findNode("PANAMA_PACIFIC").orElseThrow()))
                .isPresent();
    }

    @Test
    void shouldReplaceLongStaticCorridorWhenOverlayCoversBothSides() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        MaritimeNode overlayWest = MaritimeNode.seaNode(
                "GFW:atlantic:test:west",
                "GFW Atlantic West",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(34.2, -67.8)
        );
        MaritimeNode overlayCentral = MaritimeNode.seaNode(
                "GFW:atlantic:test:central",
                "GFW Atlantic Central",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(36.1, -42.2)
        );

        MaritimeCorridorOverlayProvider overlayProvider = new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay();
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay();
            }

            private MaritimeCorridorOverlay overlay() {
                return new MaritimeCorridorOverlay(
                        "GLOBAL_FISHING_WATCH",
                        Instant.now(),
                        List.of(overlayWest, overlayCentral),
                        List.of(
                                new MaritimeNetworkCatalog.EdgeDefinition("GFW:atlantic:test:west", "NORTH_ATLANTIC_WEST", false, false, false),
                                new MaritimeNetworkCatalog.EdgeDefinition("GFW:atlantic:test:central", "NORTH_ATLANTIC_CENTRAL", false, false, false),
                                new MaritimeNetworkCatalog.EdgeDefinition("GFW:atlantic:test:west", "GFW:atlantic:test:central", false, false, false)
                        ),
                        List.of()
                );
            }
        };

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()),
                overlayProvider
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode west = graph.findNode("NORTH_ATLANTIC_WEST").orElseThrow();
        MaritimeNode central = graph.findNode("NORTH_ATLANTIC_CENTRAL").orElseThrow();

        assertThat(graph.findEdge(west, central)).isEmpty();
        assertThat(graph.findEdge(graph.findNode("GFW:atlantic:test:west").orElseThrow(), west)).isPresent();
        assertThat(graph.findEdge(graph.findNode("GFW:atlantic:test:west").orElseThrow(), graph.findNode("GFW:atlantic:test:central").orElseThrow()))
                .isPresent();
    }

    @Test
    void shouldKeepOffshorePacificBackboneConnected() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()),
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }
                }
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode pacificTropicalEast = graph.findNode("PACIFIC_TROPICAL_EAST").orElseThrow();

        assertThat(graph.findEdge(graph.findNode("PACIFIC_SOUTH_EAST").orElseThrow(), pacificTropicalEast)).isPresent();
    }

    @Test
    void shouldExposeDetailedPanamaCanalChain() {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()),
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }
                }
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode balboaInner = graph.findNode("PANAMA_BALBOA_INNER").orElseThrow();
        MaritimeNode miraflores = graph.findNode("PANAMA_MIRAFLORES_LOCKS").orElseThrow();
        MaritimeNode canalCenter = graph.findNode("PANAMA_CANAL").orElseThrow();
        MaritimeNode colonInner = graph.findNode("PANAMA_COLON_INNER").orElseThrow();
        MaritimeNode panamaCaribbeanOuter = graph.findNode("PANAMA_CARIBBEAN_OUTER").orElseThrow();
        MaritimeNode caribbeanSouthWest = graph.findNode("CARIBBEAN_SW").orElseThrow();
        MaritimeEdge gatunToColon = graph.findEdge(graph.findNode("PANAMA_GATUN_LOCKS").orElseThrow(), colonInner)
                .orElseThrow();

        assertThat(graph.findEdge(balboaInner, miraflores)).isPresent();
        assertThat(graph.findEdge(canalCenter, colonInner)).isEmpty();
        assertThat(gatunToColon.geometry()).isNotEmpty();
        assertThat(graph.findEdge(graph.findNode("PANAMA_ATLANTIC").orElseThrow(), graph.findNode("PANAMA_CARIBBEAN_OUTER").orElseThrow()))
                .isPresent();
        assertThat(graph.findEdge(panamaCaribbeanOuter, caribbeanSouthWest)).isPresent();
    }

    @Test
    void shouldNotConnectBalboaPortDirectlyToOverlayWhenCanalChainExists() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument balboa = new PortDocument();
        balboa.setId("BALBOA-ID");
        balboa.setName("Balboa");
        balboa.setContinent("America");
        balboa.setDisabled(false);
        balboa.setCoordinates(new PortDocument.CoordinatesDocument(8.939008, -79.555637));
        when(portRepository.findAll()).thenReturn(List.of(balboa));

        MaritimeNode overlayNode = MaritimeNode.seaNode(
                "GFW:panama:test",
                "GFW Panama Test",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(7.7, -79.4)
        );
        MaritimeCorridorOverlayProvider overlayProvider = new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay();
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay();
            }

            private MaritimeCorridorOverlay overlay() {
                return new MaritimeCorridorOverlay(
                        "GLOBAL_FISHING_WATCH",
                        Instant.now(),
                        List.of(overlayNode),
                        List.of(),
                        List.of()
                );
            }
        };

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()),
                overlayProvider
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode balboaPort = graph.findPortNode("BALBOA-ID").orElseThrow();

        assertThat(graph.findEdge(balboaPort, overlayNode)).isEmpty();
        assertThat(graph.findEdge(balboaPort, graph.findNode("PANAMA_BALBOA_INNER").orElseThrow())).isPresent();
    }

    @Test
    void shouldConnectCallaoDirectlyToPreferredAisOverlayWhenNoTrustedStaticConnectorExists() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = new PortDocument();
        callao.setId("CALLAO-ID");
        callao.setName("Callao");
        callao.setContinent("America");
        callao.setDisabled(false);
        callao.setCoordinates(new PortDocument.CoordinatesDocument(-12.0564, -77.1319));
        when(portRepository.findAll()).thenReturn(List.of(callao));

        MaritimeNode overlayNode = MaritimeNode.seaNode(
                "GFW:peru-ecuador-coast:m11_20:m78_20",
                "GFW Peru Ecuador Coastal Corridor -11.20, -78.20",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(-11.2, -78.2)
        );
        MaritimeCorridorOverlayProvider overlayProvider = new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay();
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay();
            }

            private MaritimeCorridorOverlay overlay() {
                return new MaritimeCorridorOverlay(
                        "GLOBAL_FISHING_WATCH",
                        Instant.now(),
                        List.of(overlayNode),
                        List.of(
                                new MaritimeNetworkCatalog.EdgeDefinition("GFW:peru-ecuador-coast:m11_20:m78_20", "PACIFIC_SOUTH_EAST", false, false, false)
                        ),
                        List.of()
                );
            }
        };

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()) {
                    @Override
                    public boolean crossesLand(List<Coordinates> path) {
                        return false;
                    }
                },
                overlayProvider
        );

        RouteGraph graph = builder.buildDynamicRouteGraph(Set.of());
        MaritimeNode callaoPort = graph.findPortNode("CALLAO-ID").orElseThrow();

        assertThat(graph.findEdge(callaoPort, overlayNode)).isPresent();
        assertThat(graph.findNode("PERU_APPROACH")).isPresent();
        assertThat(graph.findEdge(callaoPort, graph.findNode("PERU_APPROACH").orElseThrow())).isEmpty();
    }

    @Test
    void shouldReuseCachedDynamicGraphWhenPortsAndOverlayDoNotChange() {
        PortRepository portRepository = mock(PortRepository.class);
        PortDocument callao = new PortDocument();
        callao.setId("CALLAO-ID");
        callao.setName("Callao");
        callao.setContinent("America");
        callao.setDisabled(false);
        callao.setCoordinates(new PortDocument.CoordinatesDocument(-12.051012, -77.154106));
        when(portRepository.findAll()).thenReturn(List.of(callao));

        Instant refreshedAt = Instant.parse("2026-04-20T14:00:00Z");
        MaritimeNode overlayNode = MaritimeNode.seaNode(
                "GFW:peru-ecuador-coast:m12_00:m77_20",
                "GFW Peru Corridor",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(-12.0, -77.2)
        );
        MaritimeCorridorOverlay overlay = new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                refreshedAt,
                List.of(overlayNode),
                List.of(),
                List.of()
        );
        MaritimeCorridorOverlayProvider overlayProvider = new MaritimeCorridorOverlayProvider() {
            @Override
            public MaritimeCorridorOverlay currentOverlay() {
                return overlay;
            }

            @Override
            public MaritimeCorridorOverlay refreshOverlay() {
                return overlay;
            }
        };

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()) {
                    @Override
                    public boolean crossesLand(List<Coordinates> path) {
                        return false;
                    }
                },
                overlayProvider
        );

        RouteGraph firstGraph = builder.buildDynamicRouteGraph(Set.of());
        RouteGraph secondGraph = builder.buildDynamicRouteGraph(Set.of());

        assertThat(secondGraph).isSameAs(firstGraph);
        verify(portRepository, times(2)).findAll();
    }
}
