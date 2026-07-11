package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;

import java.util.ArrayList;
import java.util.List;

@Component
public class GeoUtils {
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double KM_TO_NAUTICAL_MILES = 0.539956803;

    public double calculateHaversineDistance(Port a, Port b) {
        return calculateHaversineDistance(a.getCoordinates(), b.getCoordinates());
    }

    public double calculateHaversineDistance(Coordinates coord1, Coordinates coord2) {
        double lat1 = Math.toRadians(coord1.latitude());
        double lon1 = Math.toRadians(coord1.longitude());
        double lat2 = Math.toRadians(coord2.latitude());
        double lon2 = Math.toRadians(coord2.longitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public double calculateHaversineDistanceNm(Coordinates coord1, Coordinates coord2) {
        return calculateHaversineDistance(coord1, coord2) * KM_TO_NAUTICAL_MILES;
    }

    public List<Coordinates> densifyPath(List<Coordinates> path, double maxSegmentLengthNm) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        if (path.size() == 1 || maxSegmentLengthNm <= 0.0) {
            return List.copyOf(path);
        }

        List<Coordinates> densified = new ArrayList<>();
        densified.add(path.get(0));

        for (int index = 0; index < path.size() - 1; index++) {
            Coordinates from = path.get(index);
            Coordinates to = path.get(index + 1);
            double distanceNm = calculateHaversineDistanceNm(from, to);
            int segments = Math.max(1, (int) Math.ceil(distanceNm / maxSegmentLengthNm));
            for (int step = 1; step <= segments; step++) {
                double ratio = (double) step / segments;
                densified.add(interpolate(from, to, ratio));
            }
        }

        return List.copyOf(densified);
    }

    private Coordinates interpolate(Coordinates from, Coordinates to, double ratio) {
        double latitude = from.latitude() + (to.latitude() - from.latitude()) * ratio;
        double longitude = from.longitude() + (to.longitude() - from.longitude()) * ratio;
        return new Coordinates(latitude, longitude);
    }
}
