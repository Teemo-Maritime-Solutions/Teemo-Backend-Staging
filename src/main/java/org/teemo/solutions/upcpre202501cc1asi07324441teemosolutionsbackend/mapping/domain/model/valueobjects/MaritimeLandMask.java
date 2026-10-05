package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/** Legacy coarse mask only. V2 uses a versioned offline artifact with independent validation. */
@Component
public class MaritimeLandMask {
    private static final String RESOURCE = "data/ne_10m_land.geojson";
    private final GeometryFactory factory = new GeometryFactory();
    private final STRtree index = new STRtree();
    private final GeoUtils geoUtils;
    @Value("${routing.maritime.land.sample-spacing-nm:0.5}")
    private double sampleSpacingNm = 0.5;

    @org.springframework.beans.factory.annotation.Autowired
    public MaritimeLandMask(GeoUtils geoUtils) {
        this.geoUtils = geoUtils;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Land data unavailable: " + RESOURCE);
            load(new ObjectMapper().readTree(input));
        } catch (Exception exception) {
            throw new IllegalStateException("Land data invalid/unavailable; no synthetic fallback", exception);
        }
    }

    /** Explicit source injection for synthetic geometry tests, never a production fallback. */
    public MaritimeLandMask(GeoUtils geoUtils, JsonNode geoJson) {
        this.geoUtils = geoUtils;
        load(geoJson);
    }

    private void load(JsonNode root) {
        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) throw new IllegalArgumentException("Empty land dataset");
        for (JsonNode feature : features) {
            JsonNode geometry = feature.path("geometry");
            switch (geometry.path("type").asText()) {
                case "Polygon" -> addPolygon(geometry.path("coordinates"));
                case "MultiPolygon" -> geometry.path("coordinates").forEach(this::addPolygon);
                default -> throw new IllegalArgumentException("Unsupported land geometry");
            }
        }
        index.build();
    }

    private void addPolygon(JsonNode rings) {
        if (rings.isEmpty()) throw new IllegalArgumentException("Empty polygon");
        LinearRing shell = ring(rings.get(0));
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) holes[i - 1] = ring(rings.get(i));
        Polygon polygon = factory.createPolygon(shell, holes);
        if (!polygon.isValid()) throw new IllegalArgumentException("Invalid land polygon");
        index.insert(polygon.getEnvelopeInternal(), PreparedGeometryFactory.prepare(polygon));
    }

    private LinearRing ring(JsonNode points) {
        Coordinate[] ring = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            if (!points.get(i).isArray() || points.get(i).size() < 2
                    || !points.get(i).get(0).isNumber() || !points.get(i).get(1).isNumber())
                throw new IllegalArgumentException("Invalid land coordinate");
            double lon = points.get(i).get(0).asDouble(), lat = points.get(i).get(1).asDouble();
            check(new Coordinates(lat, lon));
            ring[i] = new Coordinate(lon, lat);
        }
        return factory.createLinearRing(ring);
    }

    public boolean isOnLand(Coordinates point) {
        check(point);
        return intersects(factory.createPoint(new Coordinate(point.longitude(), point.latitude())));
    }

    private boolean intersects(Geometry geometry) {
        for (Object candidate : index.query(geometry.getEnvelopeInternal())) {
            if (((PreparedGeometry) candidate).intersects(geometry)) return true;
        }
        return false;
    }

    public boolean crossesLand(Coordinates start, Coordinates end) {
        check(start);
        check(end);
        if (!Double.isFinite(sampleSpacingNm) || sampleSpacingNm <= 0 || sampleSpacingNm > 8)
            throw new IllegalStateException("land.sample-spacing-nm must be in (0, 8]");
        if (isOnLand(start) || isOnLand(end)) return true;
        List<Coordinates> samples = geoUtils.densifyPath(List.of(start, end), sampleSpacingNm);
        for (int i = 1; i < samples.size(); i++) {
            Coordinates a = samples.get(i - 1), b = samples.get(i);
            double x1 = a.longitude(), x2 = b.longitude();
            if (Math.abs(x2 - x1) <= 180) {
                if (segment(x1, a.latitude(), x2, b.latitude())) return true;
            } else {
                double adjusted = x2 + (x2 < x1 ? 360 : -360);
                double seam = x1 >= 0 ? 180 : -180;
                double latitude = a.latitude() + (b.latitude() - a.latitude()) * (seam - x1) / (adjusted - x1);
                if (segment(x1, a.latitude(), seam, latitude)
                        || segment(-seam, latitude, x2, b.latitude())) return true;
            }
        }
        return false;
    }

    private boolean segment(double x1, double y1, double x2, double y2) {
        return intersects(factory.createLineString(new Coordinate[]{new Coordinate(x1, y1), new Coordinate(x2, y2)}));
    }

    public boolean crossesLand(List<Coordinates> path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing path geometry");
        if (path.size() == 1) return isOnLand(path.get(0));
        for (int i = 1; i < path.size(); i++) if (crossesLand(path.get(i - 1), path.get(i))) return true;
        return false;
    }

    private void check(Coordinates p) {
        if (p == null || !Double.isFinite(p.latitude()) || !Double.isFinite(p.longitude())
                || Math.abs(p.latitude()) > 90 || Math.abs(p.longitude()) > 180)
            throw new IllegalArgumentException("Invalid WGS84 coordinates");
    }
}
