package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import lombok.Getter;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;

import java.util.Objects;

@Getter
public class MaritimeNode {
    private final String id;
    private final String name;
    private final MaritimeNodeType type;
    private final Coordinates coordinates;
    private final String portId;
    private final Port linkedPort;

    public MaritimeNode(String id, String name, MaritimeNodeType type, Coordinates coordinates, String portId, Port linkedPort) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.coordinates = coordinates;
        this.portId = portId;
        this.linkedPort = linkedPort;
    }

    public static MaritimeNode forPort(Port port) {
        return new MaritimeNode(
                "PORT:%s".formatted(port.getId()),
                port.getName(),
                MaritimeNodeType.PORT,
                port.getCoordinates(),
                port.getId(),
                port
        );
    }

    public static MaritimeNode seaNode(String id, String name, MaritimeNodeType type, Coordinates coordinates) {
        return new MaritimeNode(id, name, type, coordinates, null, null);
    }

    public boolean isPort() {
        return type == MaritimeNodeType.PORT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaritimeNode that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
