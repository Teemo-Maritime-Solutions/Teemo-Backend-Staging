package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlay;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeNetworkCatalog;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;

import java.time.Instant;
import java.util.List;

@Document(collection = "gfw_corridor_overlays")
@CompoundIndex(name = "source_config_idx", def = "{'source': 1, 'configHash': 1}")
@Getter
@Setter
@NoArgsConstructor
public class GlobalFishingWatchOverlayDocument {
    @Id
    private String id;
    private String source;
    private String configHash;
    private Instant refreshedAt;
    private Instant savedAt;
    private List<NodeDocument> nodes = List.of();
    private List<EdgeDocument> edges = List.of();
    private List<String> warnings = List.of();

    public static GlobalFishingWatchOverlayDocument fromOverlay(String configHash, MaritimeCorridorOverlay overlay) {
        GlobalFishingWatchOverlayDocument document = new GlobalFishingWatchOverlayDocument();
        document.id = overlay.source() + ":" + configHash;
        document.source = overlay.source();
        document.configHash = configHash;
        document.refreshedAt = overlay.refreshedAt();
        document.savedAt = Instant.now();
        document.nodes = overlay.nodes().stream().map(NodeDocument::fromNode).toList();
        document.edges = overlay.edges().stream().map(EdgeDocument::fromEdge).toList();
        document.warnings = overlay.warnings() == null ? List.of() : List.copyOf(overlay.warnings());
        return document;
    }

    public MaritimeCorridorOverlay toOverlay() {
        return new MaritimeCorridorOverlay(
                source,
                refreshedAt,
                nodes == null ? List.of() : nodes.stream().map(NodeDocument::toNode).toList(),
                edges == null ? List.of() : edges.stream().map(EdgeDocument::toEdge).toList(),
                warnings == null ? List.of() : List.copyOf(warnings)
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NodeDocument {
        private String id;
        private String name;
        private MaritimeNodeType type;
        private double latitude;
        private double longitude;

        public static NodeDocument fromNode(MaritimeNode node) {
            NodeDocument document = new NodeDocument();
            document.id = node.getId();
            document.name = node.getName();
            document.type = node.getType();
            document.latitude = node.getCoordinates().latitude();
            document.longitude = node.getCoordinates().longitude();
            return document;
        }

        public MaritimeNode toNode() {
            return MaritimeNode.seaNode(id, name, type, new Coordinates(latitude, longitude));
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CoordinateDocument {
        private double latitude;
        private double longitude;

        public CoordinateDocument(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public static CoordinateDocument fromCoordinates(Coordinates coordinates) {
            return new CoordinateDocument(coordinates.latitude(), coordinates.longitude());
        }

        public Coordinates toCoordinates() {
            return new Coordinates(latitude, longitude);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class EdgeDocument {
        private String fromNodeId;
        private String toNodeId;
        private boolean canal;
        private boolean restricted;
        private boolean highRisk;
        private List<CoordinateDocument> geometry = List.of();

        public static EdgeDocument fromEdge(MaritimeNetworkCatalog.EdgeDefinition edge) {
            EdgeDocument document = new EdgeDocument();
            document.fromNodeId = edge.fromNodeId();
            document.toNodeId = edge.toNodeId();
            document.canal = edge.canal();
            document.restricted = edge.restricted();
            document.highRisk = edge.highRisk();
            document.geometry = edge.geometry().stream().map(CoordinateDocument::fromCoordinates).toList();
            return document;
        }

        public MaritimeNetworkCatalog.EdgeDefinition toEdge() {
            List<Coordinates> edgeGeometry = geometry == null
                    ? List.of()
                    : geometry.stream().map(CoordinateDocument::toCoordinates).toList();
            return new MaritimeNetworkCatalog.EdgeDefinition(
                    fromNodeId,
                    toNodeId,
                    canal,
                    restricted,
                    highRisk,
                    edgeGeometry
            );
        }
    }
}
