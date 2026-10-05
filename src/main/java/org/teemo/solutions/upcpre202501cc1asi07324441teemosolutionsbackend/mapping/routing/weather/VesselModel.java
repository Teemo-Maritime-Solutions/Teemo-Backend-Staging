package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

/** User-declared research response; no calibrated vessel or fuel curve is implied. */
public record VesselModel(double calmSpeedKnots, double headWaveLossKnotsPerM2,
                          double beamWaveLossKnotsPerM2, double followingWaveLossKnotsPerM2,
                          double headWindLossKnotsPerMps2, double maximumWaveHeightM, double maximumWindMps) {
    public static final double KNOT_MPS = 1852.0/3600;
    public VesselModel {
        for (double v : new double[]{calmSpeedKnots, headWaveLossKnotsPerM2, beamWaveLossKnotsPerM2,
                followingWaveLossKnotsPerM2, headWindLossKnotsPerMps2, maximumWaveHeightM, maximumWindMps})
            if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Vessel parameters must be finite and nonnegative");
        if (calmSpeedKnots <= 0 || calmSpeedKnots > 60 || maximumWaveHeightM > 50 || maximumWindMps > 150
                || headWaveLossKnotsPerM2 > 100 || beamWaveLossKnotsPerM2 > 100
                || followingWaveLossKnotsPerM2 > 100 || headWindLossKnotsPerMps2 > 100)
            throw new IllegalArgumentException("Vessel parameters outside research bounds");
    }
    public void checkLimits(WeatherField.Sample weather) {
        if (weather.waveHeightM() > maximumWaveHeightM) throw new WeatherField.Unavailable("WAVE_LIMIT");
        if (Math.hypot(weather.windEastMps(), weather.windNorthMps()) > maximumWindMps)
            throw new WeatherField.Unavailable("WIND_LIMIT");
    }
    public double groundSpeedMps(WeatherField.Sample weather, double headingDegrees) {
        if (!Double.isFinite(headingDegrees)) throw new IllegalArgumentException("Invalid heading");
        checkLimits(weather);
        double angle = Math.abs(Math.IEEEremainder(headingDegrees-weather.waveFromDegrees(), 360));
        double coefficient = angle <= 90
                ? headWaveLossKnotsPerM2+(beamWaveLossKnotsPerM2-headWaveLossKnotsPerM2)*angle/90
                : beamWaveLossKnotsPerM2+(followingWaveLossKnotsPerM2-beamWaveLossKnotsPerM2)*(angle-90)/90;
        double sin = Math.sin(Math.toRadians(headingDegrees)), cos = Math.cos(Math.toRadians(headingDegrees));
        double headWind = Math.max(0, -(weather.windEastMps()*sin+weather.windNorthMps()*cos));
        double throughWater = (calmSpeedKnots-coefficient*weather.waveHeightM()*weather.waveHeightM()
                -headWindLossKnotsPerMps2*headWind*headWind)*KNOT_MPS;
        double along = weather.currentEastMps()*sin+weather.currentNorthMps()*cos;
        double cross = weather.currentEastMps()*cos-weather.currentNorthMps()*sin;
        if (throughWater <= Math.abs(cross)) throw new WeatherField.Unavailable("CANNOT_HOLD_COURSE");
        double ground = Math.sqrt(throughWater*throughWater-cross*cross)+along;
        if (!Double.isFinite(ground) || ground < .05) throw new WeatherField.Unavailable("NO_FORWARD_PROGRESS");
        return ground;
    }
}
