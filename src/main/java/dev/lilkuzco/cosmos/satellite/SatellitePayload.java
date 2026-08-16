package dev.lilkuzco.cosmos.satellite;

/**
 * What a satellite is for. Two payloads ship in Phase A.
 *
 * <p>Both are deliberately <em>information</em> payloads rather than effects. Orbital weapons are
 * a separate design question the campaign fences into Phase C, and building a reconnaissance
 * satellite that quietly also shoots would settle that question by accident.
 */
public enum SatellitePayload {

    /**
     * Ground-track imaging. While the footprint covers a region, the satellite can be tasked to
     * report what is underneath it - terrain, biomes, and anything built by hand.
     *
     * <p>A pass is a real window, not a button: at the reference orbit the footprint is 2,887 m
     * across and the ground track crosses it at 1,819 m/s, giving about 3.2 seconds. Miss it and
     * the next one is a Minecraft day away, because the reference orbit's period equals the day.
     */
    RECON(30.0, 0.9),

    /**
     * Communications relay. While overhead, extends the range of ground sensors that ask cosmos
     * about coverage.
     *
     * <p>A wider antenna cone than the imager's - a relay wants footprint, not resolution - which
     * makes the coverage window meaningfully longer.
     */
    COMMS(55.0, 1.0);

    private final double sensorHalfAngleDeg;
    private final double quality;

    SatellitePayload(double sensorHalfAngleDeg, double quality) {
        this.sensorHalfAngleDeg = sensorHalfAngleDeg;
        this.quality = quality;
    }

    /** Half-angle of the payload's cone. Kinetics projects this into a ground footprint. */
    public double sensorHalfAngleDeg() { return sensorHalfAngleDeg; }

    /** 0..1, how good the payload is. Scales recon detail and comms range. */
    public double quality() { return quality; }

    public String translationKey() {
        return "cosmos.payload." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
