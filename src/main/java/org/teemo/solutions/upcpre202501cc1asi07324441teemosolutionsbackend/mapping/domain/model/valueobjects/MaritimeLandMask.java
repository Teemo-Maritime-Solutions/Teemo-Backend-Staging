package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class MaritimeLandMask {
    private static final Logger logger = LoggerFactory.getLogger(MaritimeLandMask.class);
    private static final String NATURAL_EARTH_LAND_RESOURCE = "data/ne_10m_land.geojson";
    private static final double SAMPLE_SPACING_NM = 8.0;

    private final GeoUtils geoUtils;
    private final List<LandPolygon> landPolygons;

    public MaritimeLandMask(GeoUtils geoUtils) {
        this.geoUtils = geoUtils;
        this.landPolygons = loadLandPolygons();
    }

    public boolean isOnLand(Coordinates coordinates) {
        return landPolygons.stream().anyMatch(polygon -> polygon.contains(coordinates));
    }

    public boolean crossesLand(Coordinates start, Coordinates end) {
        List<Coordinates> samples = geoUtils.densifyPath(List.of(start, end), SAMPLE_SPACING_NM);
        for (int index = 1; index < samples.size() - 1; index++) {
            if (isOnLand(samples.get(index))) {
                return true;
            }
        }
        return false;
    }

    public boolean crossesLand(List<Coordinates> path) {
        if (path == null || path.size() < 2) {
            return false;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (crossesLand(path.get(index), path.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private List<LandPolygon> loadLandPolygons() {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(NATURAL_EARTH_LAND_RESOURCE)) {
            if (inputStream == null) {
                logger.warn("land.mask.resource.missing resource={}", NATURAL_EARTH_LAND_RESOURCE);
                return fallbackLandPolygons();
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                logger.warn("land.mask.resource.empty resource={}", NATURAL_EARTH_LAND_RESOURCE);
                return fallbackLandPolygons();
            }

            List<LandPolygon> polygons = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                parseGeometry(polygons, geometry);
            }
            if (polygons.isEmpty()) {
                logger.warn("land.mask.resource.no-polygons resource={}", NATURAL_EARTH_LAND_RESOURCE);
                return fallbackLandPolygons();
            }
            logger.info("land.mask.loaded resource={} polygons={}", NATURAL_EARTH_LAND_RESOURCE, polygons.size());
            return List.copyOf(polygons);
        } catch (Exception exception) {
            logger.warn("land.mask.resource.failed resource={} message={}", NATURAL_EARTH_LAND_RESOURCE, exception.getMessage());
            return fallbackLandPolygons();
        }
    }

    private void parseGeometry(List<LandPolygon> polygons, JsonNode geometry) {
        String type = geometry.path("type").asText("");
        JsonNode coordinates = geometry.path("coordinates");
        if ("Polygon".equalsIgnoreCase(type)) {
            addPolygon(polygons, coordinates);
            return;
        }
        if ("MultiPolygon".equalsIgnoreCase(type) && coordinates.isArray()) {
            for (JsonNode polygonCoordinates : coordinates) {
                addPolygon(polygons, polygonCoordinates);
            }
        }
    }

    private void addPolygon(List<LandPolygon> polygons, JsonNode polygonCoordinates) {
        if (!polygonCoordinates.isArray() || polygonCoordinates.isEmpty()) {
            return;
        }

        JsonNode outerRing = polygonCoordinates.get(0);
        List<Coordinates> points = new ArrayList<>();
        if (outerRing == null || !outerRing.isArray()) {
            return;
        }
        for (JsonNode point : outerRing) {
            if (point.isArray() && point.size() >= 2) {
                points.add(new Coordinates(point.get(1).asDouble(), point.get(0).asDouble()));
            }
        }
        if (points.size() >= 4) {
            polygons.add(new LandPolygon(points));
        }
    }

    private List<LandPolygon> fallbackLandPolygons() {
        return List.of(
                polygon(
                        22.0, 28.0,
                        22.0, 35.5,
                        32.6, 35.5,
                        32.6, 28.0
                ),
                polygon(
                        12.0, 34.0,
                        12.0, 44.0,
                        15.0, 51.0,
                        22.0, 57.5,
                        30.5, 56.0,
                        31.8, 44.0,
                        29.5, 36.0
                ),
                polygon(
                        7.0, 68.0,
                        7.0, 77.0,
                        10.0, 87.0,
                        22.0, 91.0,
                        29.5, 86.0,
                        31.5, 76.0,
                        24.0, 70.0
                ),
                polygon(
                        1.0, 97.0,
                        1.0, 105.5,
                        8.0, 109.0,
                        21.5, 108.5,
                        23.0, 101.0,
                        15.0, 97.0
                ),
                polygon(
                        8.8, -92.5,
                        8.6, -91.0,
                        8.2, -89.0,
                        8.1, -87.0,
                        8.2, -85.0,
                        8.5, -83.5,
                        8.8, -82.0,
                        8.9, -80.7,
                        8.9, -79.5,
                        9.2, -77.8,
                        9.5, -79.0,
                        9.6, -80.3,
                        10.0, -81.5,
                        11.5, -82.6,
                        13.3, -83.6,
                        14.5, -84.6,
                        15.2, -85.8,
                        15.8, -87.2,
                        16.0, -88.8,
                        15.8, -90.4,
                        15.0, -91.8,
                        12.0, -92.5
                ),
                polygon(
                        30.0, 26.0,
                        30.0, 39.5,
                        37.5, 43.0,
                        41.5, 38.0,
                        41.5, 29.0,
                        37.0, 26.0
                )
        );
    }

    private LandPolygon polygon(double... latLonPairs) {
        ArrayList<Coordinates> points = new ArrayList<>();
        for (int index = 0; index < latLonPairs.length; index += 2) {
            points.add(new Coordinates(latLonPairs[index], latLonPairs[index + 1]));
        }
        return new LandPolygon(points);
    }

    private static final class LandPolygon {
        private final List<Coordinates> points;
        private final double minLatitude;
        private final double maxLatitude;
        private final double minLongitude;
        private final double maxLongitude;

        private LandPolygon(List<Coordinates> points) {
            this.points = List.copyOf(points);
            double minLat = Double.POSITIVE_INFINITY;
            double maxLat = Double.NEGATIVE_INFINITY;
            double minLon = Double.POSITIVE_INFINITY;
            double maxLon = Double.NEGATIVE_INFINITY;
            for (Coordinates point : points) {
                minLat = Math.min(minLat, point.latitude());
                maxLat = Math.max(maxLat, point.latitude());
                minLon = Math.min(minLon, point.longitude());
                maxLon = Math.max(maxLon, point.longitude());
            }
            this.minLatitude = minLat;
            this.maxLatitude = maxLat;
            this.minLongitude = minLon;
            this.maxLongitude = maxLon;
        }

        private boolean contains(Coordinates candidate) {
            if (candidate.latitude() < minLatitude || candidate.latitude() > maxLatitude
                    || candidate.longitude() < minLongitude || candidate.longitude() > maxLongitude) {
                return false;
            }

            boolean inside = false;
            double x = candidate.longitude();
            double y = candidate.latitude();

            for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
                double xi = points.get(i).longitude();
                double yi = points.get(i).latitude();
                double xj = points.get(j).longitude();
                double yj = points.get(j).latitude();

                boolean intersects = ((yi > y) != (yj > y))
                        && (x < ((xj - xi) * (y - yi) / ((yj - yi) + 1.0e-12)) + xi);
                if (intersects) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }
}
