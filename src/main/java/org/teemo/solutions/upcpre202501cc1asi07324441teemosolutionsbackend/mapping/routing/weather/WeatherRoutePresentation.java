package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import java.util.*;
import net.sf.geographiclib.Geodesic;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.PhysicalGraph;

/** Display-only geodesic densification; splitting does not create physical edges. */
public final class WeatherRoutePresentation {
    private WeatherRoutePresentation() { }
    public record Geometry(String type, List<List<List<Double>>> coordinates) { }
    public static Geometry geometry(PhysicalGraph graph, TemporalRouteSearch.Result route) {
        List<List<List<Double>>> parts = new ArrayList<>(); List<List<Double>> part = new ArrayList<>();
        for (var leg : route.legs()) {
            if (leg.edgeId() == null) continue;
            var edge = graph.edges().get(leg.edgeId());
            if (part.isEmpty()) { var a = edge.geometry().get(0); part.add(List.of(a.longitude(), a.latitude())); }
            for (int i = 1; i < edge.geometry().size(); i++) {
                var a = edge.geometry().get(i-1); var b = edge.geometry().get(i);
                var inverse = Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), b.latitude(), b.longitude());
                int segments = Math.max(1, (int)Math.ceil(inverse.s12/10000));
                for (int j = 1; j <= segments; j++) {
                    var position = Geodesic.WGS84.Direct(a.latitude(), a.longitude(), inverse.azi1, inverse.s12*j/segments);
                    List<Double> next = j == segments ? List.of(b.longitude(), b.latitude()) : List.of(position.lon2, position.lat2);
                    List<Double> previous = part.get(part.size()-1);
                    if (Math.abs(next.get(0)-previous.get(0)) > 180) {
                        double unwrapped = next.get(0)+(next.get(0) < previous.get(0) ? 360 : -360);
                        double seam = previous.get(0) >= 0 ? 180 : -180;
                        double latitude = previous.get(1)+(next.get(1)-previous.get(1))*(seam-previous.get(0))/(unwrapped-previous.get(0));
                        part.add(List.of(seam, latitude)); parts.add(List.copyOf(part));
                        part = new ArrayList<>(); part.add(List.of(-seam, latitude));
                    }
                    part.add(next);
                }
            }
        }
        if (!part.isEmpty()) parts.add(List.copyOf(part));
        return new Geometry("MultiLineString", List.copyOf(parts));
    }
}
