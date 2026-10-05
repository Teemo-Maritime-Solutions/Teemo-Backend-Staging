package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MaritimeLandMaskRegressionTest {
    @Test void detectsThinIslandBetweenSamplePointsAndLandEndpoints() throws Exception {
        var mask = new MaritimeLandMask(new GeoUtils(), SyntheticGraphFixture.resource().path("land"));
        assertThat(mask.crossesLand(new Coordinates(0, -.1), new Coordinates(0, .1))).isTrue();
        assertThat(mask.crossesLand(new Coordinates(0, 0), new Coordinates(0, .1))).isTrue();
        assertThat(mask.crossesLand(new Coordinates(.1, -.1), new Coordinates(.1, .1))).isFalse();
    }
    @Test void datelineDoesNotDrawThroughGreenwich() throws Exception {
        var mask = new MaritimeLandMask(new GeoUtils(), SyntheticGraphFixture.resource().path("land"));
        assertThat(mask.crossesLand(new Coordinates(0, 179.9), new Coordinates(0, -179.9))).isFalse();
        var points = new GeoUtils().densifyPath(List.of(new Coordinates(0, 179.9), new Coordinates(0, -179.9)), 1);
        assertThat(points).allMatch(p -> Math.abs(p.longitude()) > 179);
    }
    @Test void emptyGeometryDoesNotBecomeAllWater() throws Exception {
        var empty = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"features\":[]}");
        assertThatThrownBy(() -> new MaritimeLandMask(new GeoUtils(), empty)).isInstanceOf(IllegalArgumentException.class);
    }
}
