package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.PhysicalGraph.Point;

/** Immutable, hash-pinned memory-mapped forecast. No network or nearest-valid filling. */
public final class ForecastSnapshot implements WeatherField {
    public static final List<String> FIELDS = List.of("swh", "mwd", "mwp", "10u", "10v", "sve", "svn");
    private final JsonNode manifest;
    private final String version;
    private final List<ByteBuffer> frames;
    private final Instant start, end;
    private final int nx, ny, cells, stepSeconds;
    private final double resolution, firstLongitude, maximumCurrent;

    public static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int read;
                while ((read = stream.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public ForecastSnapshot(Path path, String expectedSha256) throws IOException {
        if (expectedSha256 == null || !expectedSha256.matches("[a-f0-9]{64}") || Files.size(path) > 2_000_000
                || !sha256(path).equals(expectedSha256)) throw new IOException("Forecast manifest integrity failed");
        manifest = new ObjectMapper().readTree(path.toFile()); version = expectedSha256;
        if (manifest.path("schemaVersion").asInt() != 1
                || !manifest.path("purpose").asText().equals("REAL_FORECAST_RESEARCH_ONLY")
                || !manifest.path("source").asText().equals("ECMWF_IFS_OPEN_DATA")
                || !manifest.path("layout").asText().equals("FIELD_LAT_LON_FLOAT32_LE_NAN_MISSING")
                || !manifest.path("waveDirectionConvention").asText().equals("FROM_TRUE_NORTH_CLOCKWISE")
                || !manifest.path("spatialSampling").asText().equals("NEAREST_CELL_NO_MISSING_FILL")
                || !manifest.path("temporalSampling").asText().equals("LINEAR_CIRCULAR_DIRECTION"))
            throw new IOException("Unsupported forecast semantics");
        var mapper = new ObjectMapper();
        if (!manifest.path("fields").equals(mapper.valueToTree(FIELDS))
                || !manifest.path("units").equals(mapper.valueToTree(List.of("m", "Degree true", "s", "m s**-1", "m s**-1", "m s**-1", "m s**-1"))))
            throw new IOException("Invalid forecast units/fields");
        nx = manifest.path("nx").asInt(); ny = manifest.path("ny").asInt();
        resolution = manifest.path("resolutionDegrees").asDouble(Double.NaN);
        firstLongitude = manifest.path("longitudeFirst").asDouble(Double.NaN);
        if (nx < 4 || nx > 1440 || ny < 3 || ny > 721 || !Double.isFinite(resolution)
                || Math.abs(nx*resolution-360) > 1e-8 || Math.abs((ny-1)*resolution-180) > 1e-8
                || manifest.path("latitudeFirst").asDouble(Double.NaN) != 90
                || !Double.isFinite(firstLongitude) || firstLongitude < 0 || firstLongitude >= 360)
            throw new IOException("Invalid regular global grid");
        cells = nx*ny; stepSeconds = manifest.path("stepHours").asInt()*3600;
        if (stepSeconds != 21600) throw new IOException("Unsupported time grid");
        start = Instant.parse(manifest.path("runTime").asText());
        maximumCurrent = manifest.path("maximumCurrentMps").asDouble(Double.NaN);
        if (!Double.isFinite(maximumCurrent) || maximumCurrent < 0 || maximumCurrent > 100)
            throw new IOException("Invalid current bound");
        Path root = path.toRealPath().getParent();
        Path receipt = root.resolve("source-receipt.json");
        if (!sha256(receipt).equals(manifest.path("sourceReceiptSha256").asText()))
            throw new IOException("Source receipt integrity failed");
        var source = mapper.readTree(receipt.toFile());
        if (source.path("probeOnly").asBoolean(true) || !source.path("runTime").asText().equals(start.toString())
                || !source.path("source").asText().equals("ECMWF_IFS_OPEN_DATA"))
            throw new IOException("Mixed or incomplete source forecast");
        var entries = manifest.path("frames");
        if (!entries.isArray() || entries.size() < 2 || entries.size() > 61) throw new IOException("Invalid forecast horizon");
        List<ByteBuffer> mapped = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i); String name = entry.path("file").asText();
            if (!name.matches("frame-[0-9]{3}\\.f32") || entry.path("forecastHour").asInt(-1) != i*6)
                throw new IOException("Invalid forecast frame sequence");
            Path frame = root.resolve(name).toRealPath(); long bytes = (long)cells*7*4;
            if (!frame.getParent().equals(root) || Files.size(frame) != bytes || entry.path("bytes").asLong(-1) != bytes
                    || !sha256(frame).equals(entry.path("sha256").asText())) throw new IOException("Forecast frame integrity failed");
            try (var channel = FileChannel.open(frame, StandardOpenOption.READ)) {
                ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, bytes).order(ByteOrder.LITTLE_ENDIAN);
                // Validate the bound used by A*: do not trust an understated manifest maximum.
                for (int cell = 0; cell < cells; cell++) {
                    double e = buffer.getFloat((5*cells+cell)*4), n = buffer.getFloat((6*cells+cell)*4);
                    if (Double.isInfinite(e) || Double.isInfinite(n) || Double.isFinite(e) && Double.isFinite(n)
                            && Math.hypot(e, n) > maximumCurrent) throw new IOException("Invalid forecast current bound");
                }
                mapped.add(buffer);
            }
        }
        frames = List.copyOf(mapped); end = start.plusSeconds((long)(frames.size()-1)*stepSeconds);
    }
    @Override public Sample sample(Point point, Instant time) {
        if (time.isBefore(start) || time.isAfter(end)) throw new Unavailable("FORECAST_HORIZON");
        double seconds = Duration.between(start, time).toNanos()/1e9;
        int before = Math.min(frames.size()-1, (int)(seconds/stepSeconds));
        int after = Math.min(frames.size()-1, before+1);
        double fraction = (seconds-before*(double)stepSeconds)/stepSeconds;
        double lon = ((point.longitude()-firstLongitude)%360+360)%360;
        int x = (int)Math.floor(lon/resolution+.5)%nx;
        int y = Math.max(0, Math.min(ny-1, (int)Math.floor((90-point.latitude())/resolution+.5)));
        int cell = y*nx+x; double[] values = new double[7];
        for (int field = 0; field < 7; field++) {
            int offset = (field*cells+cell)*4;
            double a = frames.get(before).getFloat(offset);
            double b = fraction == 0 ? a : frames.get(after).getFloat(offset);
            if (!Double.isFinite(a) || !Double.isFinite(b)) throw new Unavailable("WEATHER_MISSING");
            if (field == 1) {
                double sx = (1-fraction)*Math.sin(Math.toRadians(a))+fraction*Math.sin(Math.toRadians(b));
                double cy = (1-fraction)*Math.cos(Math.toRadians(a))+fraction*Math.cos(Math.toRadians(b));
                if (Math.hypot(sx, cy) < 1e-8) throw new Unavailable("WAVE_DIRECTION_AMBIGUOUS");
                values[field] = (Math.toDegrees(Math.atan2(sx, cy))+360)%360;
            } else values[field] = a+(b-a)*fraction;
        }
        return new Sample(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }
    public String version() { return version; }
    public JsonNode manifest() { return manifest.deepCopy(); }
    @Override public Instant start() { return start; }
    @Override public Instant end() { return end; }
    @Override public double maximumCurrentMps() { return maximumCurrent; }
}
