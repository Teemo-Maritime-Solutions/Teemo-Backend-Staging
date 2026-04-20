package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;

import static org.assertj.core.api.Assertions.assertThat;

class MaritimeNetworkCatalogIntegrityTest {
    private static final java.util.Set<String> ACTIVE_CORRIDOR_NODES = java.util.Set.of(
            "PACIFIC_SOUTH_EAST",
            "PACIFIC_TROPICAL_EAST",
            "PANAMA_PACIFIC",
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
            "PANAMA_CARIBBEAN_OUTER",
            "CARIBBEAN_SW",
            "CARIBBEAN_WEST",
            "CARIBBEAN_COLOMBIA",
            "CARIBBEAN",
            "CARIBBEAN_ARC",
            "CARIBBEAN_EAST",
            "LESSER_ANTILLES_OUTER",
            "NORTH_ATLANTIC_WEST",
            "NORTH_ATLANTIC_CENTRAL",
            "NORTH_ATLANTIC_EAST",
            "AZORES_CORRIDOR",
            "AZORES_SOUTH",
            "MADEIRA_APPROACH",
            "PORTUGAL_APPROACH",
            "IBERIA_WEST",
            "GIBRALTAR_WEST",
            "GIBRALTAR_STRAIT",
            "ALBORAN_SEA",
            "WEST_MEDITERRANEAN"
    );

    @Test
    void shouldKeepManualSeaWaypointsOutsideCoveredLandMaskPolygons() {
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();
        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils());

        for (MaritimeNode node : catalog.coreNodes()) {
            if (!ACTIVE_CORRIDOR_NODES.contains(node.getId())) {
                continue;
            }
            if (node.getType() != MaritimeNodeType.SEA_WAYPOINT && node.getType() != MaritimeNodeType.STRAIT) {
                continue;
            }
            assertThat(landMask.isOnLand(node.getCoordinates()))
                    .as(node.getId())
                    .isFalse();
        }
    }

    @Test
    void shouldKeepNonCanalCoreEdgesOutsideCoveredLandMaskPolygons() {
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();
        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils());

        for (MaritimeNetworkCatalog.EdgeDefinition edge : catalog.coreEdges()) {
            if (!ACTIVE_CORRIDOR_NODES.contains(edge.fromNodeId()) || !ACTIVE_CORRIDOR_NODES.contains(edge.toNodeId())) {
                continue;
            }
            if (edge.canal()) {
                continue;
            }

            MaritimeNode fromNode = catalog.findNode(edge.fromNodeId()).orElseThrow();
            MaritimeNode toNode = catalog.findNode(edge.toNodeId()).orElseThrow();
            java.util.List<Coordinates> geometry = edge.geometry().isEmpty()
                    ? java.util.List.of(fromNode.getCoordinates(), toNode.getCoordinates())
                    : edge.geometry();

            assertThat(landMask.crossesLand(geometry))
                    .as(edge.fromNodeId() + "->" + edge.toNodeId())
                    .isFalse();
        }
    }
}
