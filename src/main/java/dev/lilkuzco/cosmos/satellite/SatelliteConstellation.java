package dev.lilkuzco.cosmos.satellite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Quat;
import dev.lilkuzco.kinetics.orbit.Attitude;
import dev.lilkuzco.kinetics.orbit.Orbit;
import dev.lilkuzco.kinetics.orbit.OrbitalRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The player-facing satellite roster, and the thing that survives a restart.
 *
 * <p><b>Cosmos persists the elements; kinetics propagates them.</b> Kinetics is a library and
 * deliberately has no save data - a physics library that wrote to the world folder would be a
 * physics library with opinions about worlds. So on server start cosmos reads its roster back and
 * re-registers every orbit into kinetics' registry, epoch and all.
 *
 * <p>Because kinetics propagates from epoch rather than by ticking, a satellite that was inserted
 * before the server went down comes back exactly where it should be, having "moved" the whole time
 * the server was off. There is nothing to catch up and nothing to correct.
 */
public class SatelliteConstellation extends SavedData {

    /** The serialisable half of a kinetics {@link Orbit}. */
    public record OrbitData(double epochSeconds, double semiMajorAxis, double eccentricity,
                            double inclinationDeg, double raanDeg, double argumentOfLatitudeDeg) {

        public static final Codec<OrbitData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("epoch").forGetter(OrbitData::epochSeconds),
                Codec.DOUBLE.fieldOf("sma").forGetter(OrbitData::semiMajorAxis),
                Codec.DOUBLE.optionalFieldOf("ecc", 0.0).forGetter(OrbitData::eccentricity),
                Codec.DOUBLE.fieldOf("inc").forGetter(OrbitData::inclinationDeg),
                Codec.DOUBLE.fieldOf("raan").forGetter(OrbitData::raanDeg),
                Codec.DOUBLE.fieldOf("arg_lat").forGetter(OrbitData::argumentOfLatitudeDeg))
                .apply(i, OrbitData::new));

        public Orbit toOrbit(String id) {
            return new Orbit(id, epochSeconds, semiMajorAxis, eccentricity,
                    inclinationDeg, raanDeg, argumentOfLatitudeDeg);
        }

        public static OrbitData from(Orbit orbit) {
            return new OrbitData(orbit.epochSeconds(), orbit.semiMajorAxisAtEpoch(),
                    orbit.eccentricity(), orbit.inclinationDeg(), orbit.raanDeg(),
                    orbit.argumentOfLatitudeAtEpochDeg());
        }
    }

    /** One roster line: cosmos metadata plus the elements needed to restore it. */
    public record Entry(SatelliteRecord record, OrbitData orbit) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(e -> e.record().id()),
                Codec.STRING.fieldOf("name").forGetter(e -> e.record().name()),
                Codec.STRING.fieldOf("payload").forGetter(e -> e.record().payload().name()),
                Codec.STRING.optionalFieldOf("owner_id", "").forGetter(
                        e -> e.record().ownerId() == null ? "" : e.record().ownerId().toString()),
                Codec.STRING.optionalFieldOf("owner_name", "").forGetter(
                        e -> e.record().ownerName() == null ? "" : e.record().ownerName()),
                Codec.DOUBLE.fieldOf("launched_at").forGetter(e -> e.record().launchedAt()),
                Codec.STRING.optionalFieldOf("tier", "").forGetter(e -> e.record().launchTier()),
                OrbitData.CODEC.fieldOf("orbit").forGetter(Entry::orbit))
                .apply(i, (id, name, payload, ownerId, ownerName, launchedAt, tier, orbit) ->
                        new Entry(new SatelliteRecord(id, name,
                                SatellitePayload.valueOf(payload),
                                ownerId.isEmpty() ? null : UUID.fromString(ownerId),
                                ownerName.isEmpty() ? null : ownerName,
                                launchedAt, tier), orbit)));
    }

    private static final Codec<SatelliteConstellation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("satellites").forGetter(c -> List.copyOf(c.byId.values())),
            Codec.LONG.optionalFieldOf("next_serial", 1L).forGetter(c -> c.nextSerial))
            .apply(i, SatelliteConstellation::new));

    public static final SavedDataType<SatelliteConstellation> TYPE = new SavedDataType<>(
            Cosmos.id("constellation"),
            SatelliteConstellation::new,
            CODEC,
            DataFixTypes.LEVEL);

    // Insertion-ordered so the planetarium lists satellites in launch order rather than at the
    // whim of a hash - the same determinism argument kinetics makes about its own registry.
    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private long nextSerial = 1L;

    public SatelliteConstellation() {}

    private SatelliteConstellation(List<Entry> entries, long nextSerial) {
        for (Entry e : entries) byId.put(e.record().id(), e);
        this.nextSerial = nextSerial;
    }

    public static SatelliteConstellation of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** A fresh satellite id. Serials rather than UUIDs so flight logs stay readable. */
    public String nextId() {
        String id = "cosmos:sat-" + nextSerial++;
        setDirty();
        return id;
    }

    public void add(SatelliteRecord record, Orbit orbit) {
        byId.put(record.id(), new Entry(record, OrbitData.from(orbit)));
        setDirty();
    }

    public boolean remove(String id) {
        boolean removed = byId.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    public Optional<Entry> get(String id) { return Optional.ofNullable(byId.get(id)); }

    public List<Entry> all() { return List.copyOf(byId.values()); }

    public List<Entry> ownedBy(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) {
            if (owner.equals(e.record().ownerId())) out.add(e);
        }
        return out;
    }

    public int size() { return byId.size(); }

    public boolean rename(String id, String name) {
        Entry e = byId.get(id);
        if (e == null) return false;
        byId.put(id, new Entry(e.record().withName(name), e.orbit()));
        setDirty();
        return true;
    }

    /**
     * Re-register every stored orbit into kinetics. Called on server start; safe to call again.
     */
    public void restoreInto(OrbitalRegistry registry, double maxSlewRateDeg) {
        for (Entry e : byId.values()) {
            if (registry.contains(e.record().id())) continue;
            registry.register(e.orbit().toOrbit(e.record().id()),
                    Attitude.threeAxis(Quat.IDENTITY, maxSlewRateDeg));
        }
    }

    /** Drop roster lines whose orbit kinetics no longer holds - they decayed and came down. */
    public List<SatelliteRecord> pruneDeorbited(OrbitalRegistry registry) {
        List<SatelliteRecord> gone = new ArrayList<>();
        for (Entry e : List.copyOf(byId.values())) {
            if (!registry.contains(e.record().id())) {
                gone.add(e.record());
                byId.remove(e.record().id());
            }
        }
        if (!gone.isEmpty()) setDirty();
        return gone;
    }

    // ---- lifecycle --------------------------------------------------------

    public static void register() {
        // Deferred to the first server tick, NOT to SERVER_STARTED.
        //
        // Fabric gives no ordering guarantee between two mods' SERVER_STARTED handlers, and in
        // practice cosmos's fired first: kinetics had not created its service yet, so the
        // restore found nothing and every satellite in the save was silently dropped from the
        // registry. Waiting a tick costs 50 ms and removes the race entirely - and retrying
        // rather than giving up means a slow start cannot lose a constellation either.
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            private boolean restored;

            @Override
            public void onEndTick(MinecraftServer server) {
                if (restored) return;
                KineticsService kinetics = KineticsMod.service();
                if (kinetics == null) return;   // try again next tick

                restored = true;
                SatelliteConstellation constellation = of(server.overworld());
                constellation.restoreInto(kinetics.orbits(),
                        kinetics.constants().d("limits.max_slew_rate_default"));
                if (constellation.size() > 0) {
                    Cosmos.LOG.info("restored {} satellite(s) into the kinetics registry; "
                            + "they propagated from epoch while the server was down",
                            constellation.size());
                }
            }
        });

        // A new world starts with nothing to restore, so reset the latch per server.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { });
    }
}
