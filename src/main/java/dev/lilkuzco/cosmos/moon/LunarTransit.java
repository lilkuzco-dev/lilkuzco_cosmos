package dev.lilkuzco.cosmos.moon;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.rocket.RocketEntity;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.phase.FlightDirector;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The journey to the Moon, which the player rides from end to end.
 *
 * <p>It is deliberately <b>not</b> a screen that fades to a new dimension. A trans-lunar transfer
 * is a real manoeuvre with a real duration - kinetics puts it at 99,747 seconds, 27.7 hours of
 * simulated time, which is the same 3.0 real days scaled by the same factor as everything else -
 * and treating that as a loading bar would throw away the one thing that makes the Moon feel far
 * away.
 *
 * <p>Three stages, each of which can fail:
 *
 * <ol>
 *   <li><b>Coast.</b> The transfer ellipse, played back compressed. Telemetry every second: true
 *       range to the Moon, closing speed, time to arrival. All of it read off kinetics' Hohmann
 *       solution, none of it invented.</li>
 *   <li><b>Arrival.</b> The lander separates high above the Moon with the descent velocity the
 *       transfer gave it, and becomes a real kinetics body in vacuum at 0.165 g.</li>
 *   <li><b>Descent.</b> Kinetics flies the retro-burn (RD6). A lander with the delta-v touches
 *       down; one without it arrives at a few hundred metres a second. Cosmos decides what that
 *       means for the crew - kinetics never does (I10).</li>
 * </ol>
 *
 * <p><b>The compression is the one fiction, and it is stated.</b> The trajectory, the burn, the
 * budget and the arrival speed are all real; only the clock is played faster, because 27.7 hours
 * is a physically honest answer to a question nobody wants answered at that length.
 */
public final class LunarTransit {

	/**
	 * How much faster than real the coast is played.
	 *
	 * <p>A GAME decision, not a physical one, which is exactly why it lives here in cosmos and not
	 * in kinetics' constants: the physics library has no opinion about how long a player should
	 * sit still. Kinetics says the transfer takes 99,747 s; at 415x that is four minutes, which is
	 * long enough to be a journey and short enough to be one you make more than once.
	 */
	public static final double TIME_COMPRESSION = 415.0;

	/** Altitude above the lunar surface at which the lander separates and the descent begins. */
	public static final double ARRIVAL_ALTITUDE = 30_000.0;

	/** Where a mission is in the journey. */
	public enum Stage { COAST, DESCENT }

	/**
	 * One crew's journey. Mutable, because {@code lastSpeed} has to be.
	 *
	 * <p>Kinetics zeroes a body's velocity on impact, so by the time the phase reads LANDED the
	 * arrival speed is already gone - and the arrival speed is the entire question of whether the
	 * crew survived. It has to be remembered from the last tick the lander was still moving.
	 * Cosmos learned this once already, reporting every capsule touchdown as 0.00 m/s.
	 */
	private static final class Mission {
		final UUID crew;
		final Propellant propellant;
		final double startedAt;
		final double coastSeconds;
		final BlockPos origin;
		String landerBodyId;
		Stage stage = Stage.COAST;
		double lastSpeed;

		Mission(UUID crew, Propellant propellant, double startedAt, double coastSeconds,
		        BlockPos origin) {
			this.crew = crew;
			this.propellant = propellant;
			this.startedAt = startedAt;
			this.coastSeconds = coastSeconds;
			this.origin = origin;
		}

		UUID crew() { return crew; }
		Propellant propellant() { return propellant; }
		double startedAt() { return startedAt; }
		double coastSeconds() { return coastSeconds; }
		BlockPos origin() { return origin; }
		String landerBodyId() { return landerBodyId; }
		Stage stage() { return stage; }
	}

	private static final Map<UUID, Mission> MISSIONS = new LinkedHashMap<>();

	private LunarTransit() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LunarTransit::tick);
	}

	public static int inTransit() {
		return MISSIONS.size();
	}

	/**
	 * Begin a transit. Called from {@link dev.lilkuzco.cosmos.rocket.LaunchTracker} when a crewed
	 * flight reaches the trans-lunar budget.
	 */
	public static void begin(ServerPlayer crew, Propellant propellant, BlockPos origin) {
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) return;

		double coast = kinetics.orbits().mechanics().lunarTransferTime() / TIME_COMPRESSION;
		MISSIONS.put(crew.getUUID(), new Mission(crew.getUUID(), propellant,
				kinetics.worldTimeSeconds(), coast, origin.immutable()));

		crew.sendSystemMessage(Component.translatable("cosmos.transit.begin",
				String.format("%.0f", kinetics.orbits().mechanics().lunarTransferTime() / 3600.0),
				String.format("%.0f", coast)));
		crew.level().playSound(null, crew.blockPosition(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 1.0F, 1.2F);
		Cosmos.LOG.info("trans-lunar injection for {}: {} s of transfer played over {} s",
				crew.getGameProfile().name(),
				String.format("%.0f", kinetics.orbits().mechanics().lunarTransferTime()),
				String.format("%.0f", coast));
	}

	private static void tick(MinecraftServer server) {
		if (MISSIONS.isEmpty()) return;
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) { MISSIONS.clear(); return; }

		List<UUID> done = null;
		for (Mission mission : List.copyOf(MISSIONS.values())) {
			ServerPlayer crew = server.getPlayerList().getPlayer(mission.crew());
			if (crew == null) continue;   // logged off mid-transit; the mission waits

			boolean finished = switch (mission.stage()) {
				case COAST -> tickCoast(server, kinetics, crew, mission);
				case DESCENT -> tickDescent(kinetics, crew, mission);
			};
			if (finished) {
				if (done == null) done = new ArrayList<>();
				done.add(mission.crew());
			}
		}
		if (done != null) done.forEach(MISSIONS::remove);
	}

	/** The coast: real geometry, compressed clock, telemetry the player can read. */
	private static boolean tickCoast(MinecraftServer server, KineticsService kinetics,
	                                 ServerPlayer crew, Mission mission) {
		double elapsed = kinetics.worldTimeSeconds() - mission.startedAt();
		double fraction = Math.min(1.0, elapsed / Math.max(1e-6, mission.coastSeconds()));

		if (crew.tickCount % 20 == 0) {
			var m = kinetics.orbits().mechanics();
			// Range shrinks along the transfer ellipse, from the parking orbit out to the Moon.
			double r1 = m.radiusForAltitude(kinetics.constants().d("orbit.reference_orbit_altitude"));
			double range = m.lunarDistance() - (m.lunarDistance() - r1) * fraction;
			double remaining = (mission.coastSeconds() - elapsed) * TIME_COMPRESSION;
			dev.lilkuzco.cosmos.life.LifeSupport.actionBar(crew,
					Component.translatable("cosmos.transit.telemetry",
							String.format("%.0f", (m.lunarDistance() - range) / 1000.0),
							String.format("%.0f", range / 1000.0),
							String.format("%.1f", Math.max(0.0, remaining) / 3600.0)));
		}

		if (fraction < 1.0) return false;
		return arrive(server, kinetics, crew, mission);
	}

	/**
	 * Arrival: separate the lander and hand the descent to kinetics.
	 *
	 * <p>The player is moved to the Moon at the altitude the lander separates, riding it. From
	 * here nothing is scripted - the retro-burn is kinetics' RD6 law spending the lander's own
	 * propellant, and whether it works is arithmetic that was decided when the lander was built.
	 */
	private static boolean arrive(MinecraftServer server, KineticsService kinetics,
	                              ServerPlayer crew, Mission mission) {
		ServerLevel moon = server.getLevel(MoonDimension.MOON);
		if (moon == null) {
			crew.sendSystemMessage(Component.translatable("cosmos.transit.no_moon"));
			Cosmos.LOG.error("transit arrived but this world has no {} dimension",
					MoonDimension.MOON.identifier());
			return true;
		}

		Constants k = kinetics.constants();
		double descentSpeed = k.d("orbit.lunar_descent_delta_v");
		double surfaceY = k.d("world.sea_level_y");
		double y = surfaceY + ARRIVAL_ALTITUDE;

		// Land near the origin's coordinates, so a base built at x,z has its Moon site at x,z.
		double x = mission.origin().getX() + 0.5;
		double z = mission.origin().getZ() + 0.5;

		String bodyId = "cosmos:lander-" + mission.crew();
		var profile = LunarLander.profile(bodyId, mission.propellant(),
				LunarLander.PROPELLANT_KG, k);
		KineticsService.Handle handle = kinetics.spawn(bodyId, profile, MoonDimension.MOON,
				new Vec3(x, y, z), new Vec3(0.0, -descentSpeed, 0.0),
				FlightDirector.Mission.LANDING);
		if (handle == null) {
			crew.sendSystemMessage(Component.translatable("cosmos.transit.no_moon"));
			Cosmos.LOG.error("kinetics refused a lander in {} - is the dimension registered?",
					MoonDimension.MOON.identifier());
			return true;
		}

		RocketEntity lander = RocketEntity.ride(moon, bodyId, x, y, z, crew);
		if (lander == null) {
			kinetics.despawn(bodyId);
			return true;
		}

		crew.sendSystemMessage(Component.translatable("cosmos.transit.arrived",
				String.format("%.0f", descentSpeed), String.format("%.0f", ARRIVAL_ALTITUDE)));
		Cosmos.LOG.info("lunar arrival for {}: {} m at {} m/s, lander delta-v {} m/s against a "
						+ "{} m/s bill", crew.getGameProfile().name(),
				String.format("%.0f", ARRIVAL_ALTITUDE), String.format("%.1f", descentSpeed),
				String.format("%.1f", LunarLander.deltaV(mission.propellant(),
						LunarLander.PROPELLANT_KG, k)),
				String.format("%.1f", LunarLander.arrivalBudget(k)));

		mission.landerBodyId = bodyId;
		mission.stage = Stage.DESCENT;
		return false;
	}

	/** The descent. Kinetics flies it; cosmos only reports it and decides what impact means. */
	private static boolean tickDescent(KineticsService kinetics, ServerPlayer crew,
	                                   Mission mission) {
		KineticsService.Handle handle = kinetics.handle(mission.landerBodyId());
		if (handle == null) {
			// Kinetics let the body go without our seeing a terminal phase. Put the crew down
			// rather than leaving them riding an entity that no longer mirrors anything.
			touchdown(crew, mission, mission.lastSpeed);
			return true;
		}

		var body = handle.body();
		double altitude = body.position().y() - kinetics.constants().d("world.sea_level_y");
		double speed = body.velocity().length();
		if (speed > 0.0) mission.lastSpeed = speed;

		if (body.phase().isTerminal()) {
			kinetics.despawn(mission.landerBodyId());
			touchdown(crew, mission, mission.lastSpeed);
			return true;
		}

		if (crew.tickCount % 10 == 0) {
			dev.lilkuzco.cosmos.life.LifeSupport.actionBar(crew,
					Component.translatable(body.phase() == FlightPhase.LANDING
									? "cosmos.transit.burning" : "cosmos.transit.falling",
							String.format("%.0f", Math.max(0.0, altitude)),
							String.format("%.0f", speed)));
		}
		return false;
	}

	/**
	 * Arrival on the surface, and what it costs.
	 *
	 * <p><b>Kinetics does not decide this and must not (I10).</b> It reports a body that met the
	 * ground at a speed; whether that is a landing or a crater is cosmos' call, and the rule is
	 * the one the physics already implies: below the touchdown speed the lander settles, above it
	 * the crew take the energy they arrived with. A lander that ran dry mid-burn hits at a few
	 * hundred metres per second, and no amount of health survives that - which is the honest
	 * consequence of building a lander that could not afford its own descent.
	 */
	private static void touchdown(ServerPlayer crew, Mission mission, double speed) {
		crew.stopRiding();
		ServerLevel level = crew.level();
		double touchdownLimit = KineticsMod.service() == null ? 2.0
				: KineticsMod.service().constants().d("landing.touchdown_speed") * 3.0;

		if (speed <= touchdownLimit) {
			crew.sendSystemMessage(Component.translatable("cosmos.transit.landed",
					String.format("%.2f", speed)));
			level.playSound(null, crew.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
					SoundSource.PLAYERS, 1.0F, 1.4F);
			dev.lilkuzco.cosmos.moon.LunarSite.deliver(level, crew.blockPosition(), crew);
			Cosmos.LOG.info("lunar landing by {} at {} m/s", crew.getGameProfile().name(),
					String.format("%.2f", speed));
			return;
		}

		crew.sendSystemMessage(Component.translatable("cosmos.transit.crashed",
				String.format("%.0f", speed)));
		level.playSound(null, crew.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.PLAYERS, 3.0F, 0.6F);
		// The crew take the arrival, scaled off the speed they could not cancel. Deliberately
		// lethal above a few tens of metres per second: an impact a player walks away from would
		// make the whole delta-v budget optional.
		float damage = (float) Math.min(200.0, speed * 0.6);
		crew.hurtServer(level, level.damageSources().fall(), damage);
		Cosmos.LOG.info("lunar impact by {} at {} m/s - the lander could not afford its descent",
				crew.getGameProfile().name(), String.format("%.1f", speed));
	}

	/** Whether this player is mid-transit, so life support and commands can say so. */
	public static boolean isInTransit(ServerPlayer player) {
		return MISSIONS.containsKey(player.getUUID());
	}

	/** The Moon's own propellant grade default, used when a mission's grade is somehow lost. */
	static Propellant defaultGrade() {
		return Propellants.KEROSENE;
	}

	/** Drop a mission whose lander has finished, one way or the other. */
	public static void resolved(UUID crew) {
		MISSIONS.remove(crew);
	}

	/** The dimension a transit ends in, for anything that needs to ask without importing. */
	public static net.minecraft.resources.ResourceKey<Level> destination() {
		return MoonDimension.MOON;
	}
}
