package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import java.time.Instant;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.PhysicalGraph.Point;

public interface WeatherField {
    record Sample(double waveHeightM, double waveFromDegrees, double wavePeriodSeconds,
                  double windEastMps, double windNorthMps, double currentEastMps, double currentNorthMps) {
        public Sample {
            for (double v : new double[]{waveHeightM, waveFromDegrees, wavePeriodSeconds,
                    windEastMps, windNorthMps, currentEastMps, currentNorthMps})
                if (!Double.isFinite(v)) throw new Unavailable("WEATHER_MISSING");
            if (waveHeightM < 0 || wavePeriodSeconds < 0 || waveFromDegrees < 0 || waveFromDegrees > 360)
                throw new Unavailable("WEATHER_INVALID");
        }
    }
    final class Unavailable extends RuntimeException {
        public Unavailable(String reason) { super(reason); }
    }
    Sample sample(Point point, Instant time);
    Instant start();
    Instant end();
    double maximumCurrentMps();
}
