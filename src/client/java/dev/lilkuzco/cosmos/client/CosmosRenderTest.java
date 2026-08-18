package dev.lilkuzco.cosmos.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import dev.lilkuzco.cosmos.satellite.ReconImager;
import dev.lilkuzco.cosmos.satellite.SatelliteConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client render battery.
 *
 * <p><b>This exists because headless verification cannot see this class of bug, and a shipped
 * release proved it.</b> Every server-side check passed on a build whose rocket was completely
 * invisible in flight: the physics were right, the satellite deployed, the logs were clean, and
 * there was nothing to look at. Before that, the same blind spot shipped a rocket that hard-crashed
 * the client on ignition — the server logged a perfect launch either way.
 *
 * <p>So this boots a real client, puts each thing cosmos draws in front of the camera, and takes a
 * screenshot of it. Screenshots are the evidence; they are read back and looked at.
 *
 * <p>Runs only under {@code ./gradlew runGametest} ({@code -Dfabric.client.gametest}), never in
 * normal play.
 */
public class CosmosRenderTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(80);
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamemode creative @p");
			server.runCommand("difficulty peaceful");
			server.runCommand("kill @e[type=!minecraft:player]");

			// EVERYTHING USES ABSOLUTE COORDINATES from here on. An earlier version built the
			// stage with `execute at @p run` and then teleported the camera, which resolved every
			// `~` against a player who was still falling - the shots came out at a different height
			// each run. A fixed stage at a fixed origin is reproducible.
			server.runCommand("forceload add -3 -3 3 3");
			server.runCommand("fill -40 99 -40 40 99 40 minecraft:stone");
			server.runCommand("fill -40 100 -40 40 140 40 minecraft:air");
			server.runCommand("tp @p 0 100 0");
			context.waitTicks(40);

			// ---- 1. the blocks --------------------------------------------------
			String[] blocks = { "launch_pad", "pad_frame", "fuel_tank", "satellite_console",
					"oxygen_station", "regolith", "mare_basalt", "lunar_ice", "electrolyser",
					"regolith_kiln", "sintered_regolith", "tholin_sand", "haze_bedrock",
					"ammonia_ice", "ammonia_cracker" };
			for (int i = 0; i < blocks.length; i++) {
				server.runCommand("setblock " + (-10 + i * 2) + " 100 8 cosmos:" + blocks[i]);
			}
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p 0 102 -1 0 20");
			context.waitTicks(40);
			context.takeScreenshot("cosmos_blocks");

			// ---- 2. every item in hand -----------------------------------------
			server.runCommand("gamemode creative @p");
			server.runCommand("tp @p 0 100 0 0 0");
			context.waitTicks(10);
			for (String item : new String[] {
					"rocket_sounding", "rocket_orbital", "rocket_heavy", "rocket_lunar",
					"satellite_recon", "satellite_comms", "lunar_lander", "entry_capsule",
					"pressure_suit", "oxygen_tank" }) {
				server.runCommand("clear @p");
				server.runCommand("give @p cosmos:" + item);
				context.waitTicks(8);
				context.takeScreenshot("cosmos_item_" + item);
			}
			server.runCommand("clear @p");

			// ---- 3. THE ROCKET, which is what all of this is for ----------------
			//
			// Spectator, off to the side and level with the vehicle, looking at the pad. The
			// camera must be spectator: a creative player falls during the render wait, and the
			// shot comes from wherever they landed.
			//
			// Shots are taken EARLY. A launch vehicle clears 250 m inside a couple of seconds, so
			// by twenty ticks it is a dot; the first version waited and photographed an empty sky.
			for (String tier : new String[] { "sounding", "orbital", "heavy", "lunar" }) {
				server.runCommand("kill @e[type=cosmos:rocket]");
				context.waitTicks(5);
				server.runCommand("gamemode spectator @p");
				// Yaw -90 looks toward +X. Yaw 90 looks the other way, and photographed an empty
				// horizon for a whole run before that was noticed.
				server.runCommand("tp @p -9 103 0 -90 8");
				context.waitTicks(10);
				server.runCommand("execute positioned 0 100 0 run cosmos testlaunch " + tier
						+ " kerosene");
				context.waitTicks(2);
				context.takeScreenshot("cosmos_rocket_" + tier + "_ignition");
				context.waitTicks(6);
				context.takeScreenshot("cosmos_rocket_" + tier + "_climbing");
			}

			// ---- 3b. CAMERA SHAKE, proved or not shipped ------------------------
			//
			// A stationary spectator watching a burning first stage. If the shake fires, the camera
			// is nudged every frame and consecutive shots do not line up; if it does not, three
			// frames from a camera that never moved are identical. That is the whole proof, and it
			// is why these three are taken back to back with nothing else changing.
			server.runCommand("kill @e[type=cosmos:rocket]");
			context.waitTicks(10);
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p -6 101 0 -90 0");
			context.waitTicks(10);
			context.takeScreenshot("cosmos_shake_before");
			server.runCommand("execute positioned 0 100 0 run cosmos testlaunch heavy kerosene");
			context.waitTicks(4);
			// Eight frames a tick apart. The shake oscillates, so three samples two ticks apart can
			// all land on the same phase and read as a stationary camera - which they did.
			for (int frame = 0; frame < 8; frame++) {
				context.takeScreenshot(String.format("cosmos_shake_during_%02d", frame));
				context.waitTicks(1);
			}

			// ---- 4. a wider shot, so the plume reads as a plume -----------------
			server.runCommand("kill @e[type=cosmos:rocket]");
			context.waitTicks(10);
			server.runCommand("tp @p -22 106 0 -90 12");
			context.waitTicks(10);
			server.runCommand("execute positioned 0 100 0 run cosmos testlaunch heavy kerosene");
			context.waitTicks(4);
			context.takeScreenshot("cosmos_rocket_wide_ignition");
			context.waitTicks(10);
			context.takeScreenshot("cosmos_rocket_wide_ascent");

			server.runCommand("kill @e[type=cosmos:rocket]");
			context.waitTicks(10);

			// ---- 4b. CLOSE-UP of the capsule model, via the transit vehicle ------
			//
			// The transit vehicle uses the same CapsuleModel and can be summoned next to the
			// camera, so it gives a legible close-up in seconds instead of the two minutes a real
			// entry takes. A descent photographed from thirty blocks cannot settle whether a part
			// is drawn.
			// WELL AWAY FROM THE PAD. The first version stood the model board on the launch pad and
			// photographed it through a rocket plume.
			server.runCommand("kill @e[type=cosmos:rocket]");
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute positioned 30 101 30 run cosmos showcapsule");
			server.runCommand("tp @p 30 102 25 0 -8");   // yaw 0 looks toward +Z
			context.waitTicks(15);
			context.takeScreenshot("cosmos_capsule_and_canopy");
			context.waitTicks(5);

			// ---- 5. the capsule: reentry glow, then the canopy -------------------
			//
			// This needs a satellite in orbit first, so it flies a real launch and waits for the
			// insertion rather than conjuring a capsule. The whole recovery chain is the thing
			// under test; a spawned capsule would prove only that a model exists.
			// The tier launches above already inserted satellites, so there is no need to fly
			// another 1,400 ticks for one. Serial numbers increment across the whole session and a
			// failed insertion does not consume one, so the id cannot be predicted - ask for each in
			// turn and let the registry refuse the ones that are not there. An earlier version
			// hardcoded sat-1, which had never existed, and photographed an empty sky.
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p 0 130 0 0 0");
			// The launches above are still climbing. Insertion happens about a minute into a
			// flight, so the wait is not padding - it is the flight. Removing it produced six
			// "not in the registry" refusals and no capsule at all.
			context.waitTicks(1500);

			// ---- 5a. the console screens ----------------------------------------
			//
			// Placed here, before the deorbits below, because this is the only window in the
			// battery where satellites are actually in orbit - the planetarium has nothing to
			// draw either side of it, and flying another 1,500-tick launch for a roster would
			// double the run time for no extra evidence.

			// (i) The creative menu, as an assertion rather than a picture.
			//
			// CreativeModeTabEvents.insertAfter() is a SILENT no-op when its anchor item is not
			// in the tab, and every cosmos insertion is chained off the one before it: the pad
			// after the beacon, the frame after the pad, the console after the tank. A single
			// missing anchor therefore drops the WHOLE chain out of the menu with no crash, no
			// log line, and every other check in this battery still green.
			server.runCommand("gamemode creative @p");
			context.waitTicks(5);
			String creative = context.computeOnClient(client -> {
				// Tab contents are built lazily - a fresh client has the tab OBJECTS but no items
				// in them until something asks. Opening the creative menu is what normally does
				// this; doing it directly is the same call with no GUI in the way. Without it the
				// scan below sees zero items and the vacuity guard trips, which is how this was
				// found rather than shipped as a check of nothing.
				CreativeModeTabs.tryRebuildTabContents(
						client.level.enabledFeatures(),
						client.options.operatorItemsTab().get(),
						client.level.registryAccess());

				Set<String> shown = new LinkedHashSet<>();
				int total = 0;
				for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
					for (var stack : tab.getDisplayItems()) {
						total++;
						var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
						if (id != null && id.getNamespace().equals("cosmos")) {
							shown.add(id.getPath());
						}
					}
				}
				// Without this, an unbuilt menu would let every membership test below pass by
				// finding nothing missing from nothing.
				if (total == 0) return "!EMPTY";
				List<String> absent = new ArrayList<>();
				for (String want : CREATIVE_BLOCKS) {
					if (!shown.contains(want)) absent.add(want);
				}
				return String.join(", ", absent);
			});
			if ("!EMPTY".equals(creative)) {
				throw new AssertionError("the creative menu built zero items, so this check can "
						+ "see nothing and would pass vacuously - fix the check, not the mod");
			}
			if (!creative.isEmpty()) {
				throw new AssertionError("missing from the creative menu: " + creative
						+ " - the insertAfter chain in CosmosBlocks.register() has lost an anchor");
			}

			// (ii) The planetarium itself, over the real packet path.
			//
			// sendSnapshot is exactly what right-clicking the block calls, so this is the
			// production route rather than a screen constructed in the test.
			server.runCommand("setblock -32 100 -32 cosmos:satellite_console");
			server.runCommand("tp @p -30 100 -32 -90 0");
			context.waitTicks(10);
			server.runOnServer(mc -> SatelliteConsoleBlockEntity.sendSnapshot(
					mc.getPlayerList().getPlayers().get(0), new BlockPos(-32, 100, -32), true));
			context.waitForScreen(PlanetariumScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("cosmos_console_planetarium");

			// (iii) Task real passes and photograph what comes back.
			//
			// Sweep rather than take one shot: whether a pass sees anything depends on where the
			// sub-satellite point is at that moment, and most of a 2.9 km footprint is over
			// chunks nobody has loaded. Some of these frames will be NO COVERAGE, which is a
			// distinct render path and worth having on record too.
			for (int shot = 0; shot < 6; shot++) {
				if (!context.tryClickScreenButton("cosmos.planetarium.image")
						&& !context.tryClickScreenButton("Image")) {
					throw new AssertionError("no Image button on the planetarium - the button "
							+ "row did not build, or its label stopped matching");
				}
				context.waitTicks(20);
				context.takeScreenshot(String.format("cosmos_console_recon_live_%02d", shot));
				context.waitTicks(20);
			}

			// (iv) Model boards for the two report layouts.
			//
			// A real pass cannot be relied on to fill the panel: the imager samples a 15x15 grid
			// across the footprint, so its points are ~206 m apart and nearly all of them land in
			// unloaded chunks. The sweep above proves the PATH; these prove the LAYOUT - four
			// composition rows, five returns, a clipped block id and the blind case - which is
			// the part only a screenshot can judge. This is the showcapsule lesson: build the
			// fast loop rather than chase the slow one.
			String reconId = context.computeOnClient(client -> {
				PlanetariumScreen screen = PlanetariumScreen.open();
				return screen == null ? null : screen.selectedId();
			});
			if (reconId == null) {
				throw new AssertionError("the planetarium closed, or its roster is empty, before "
						+ "the model boards - nothing reached orbit in this run");
			}
			context.runOnClient(client -> PlanetariumScreen.open()
					.showRecon("Eye 1", boardReport(reconId, false)));
			context.waitTicks(5);
			context.takeScreenshot("cosmos_console_recon_board");

			context.runOnClient(client -> PlanetariumScreen.open()
					.showRecon("Eye 1", boardReport(reconId, true)));
			context.waitTicks(5);
			context.takeScreenshot("cosmos_console_recon_board_blind");

			// Hand the camera back. Everything below needs an unobstructed view.
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(5);

			for (int serial = 1; serial <= 6; serial++) {
				server.runCommand("cosmos deorbit cosmos:sat-" + serial);
			}
			// A deorbit is AIMED at the operator, so the capsule lands here - but it enters
			// thousands of blocks downrange and takes about 240 ticks to arrive. Camera at the
			// landing site looking up, shots timed to the last seconds of the descent. Pointing at
			// the horizon from 130 m photographed an empty sky three times.
			// A capsule arrives fast and is in frame for a couple of seconds. Guessing the moment
			// missed it three times, so sweep the whole descent window instead: the camera sits at
			// the landing site and a frame is taken every ten ticks. One of them will have it, and
			// which one is not something worth predicting.
			// Further back and higher, so more of the descent is in frame, and sampled every four
			// ticks through the window where the canopy is out - a chute that inflates low is only
			// deployed for a second or two and a ten-tick sample kept stepping over it.
			// CLOSE. A capsule photographed from thirty blocks is ten pixels tall, and a
			// parachute above it is a smudge - which is how a working canopy read as a
			// missing one through six runs of chasing it.
			server.runCommand("tp @p 12 104 6 -125 -10");
			// WAIT FOR THE CAPSULE, do not guess when it arrives.
			//
			// Entry timing drifts several seconds between runs, and every fixed sample schedule I
			// tried landed either side of the descent at least once - including the run that was
			// supposed to confirm the fix. Polling the client for the entity removes the luck.
			int found = -1;
			for (int poll = 0; poll < 200 && found < 0; poll++) {
				context.waitTicks(2);
				int[] seen = {0};
				context.runOnClient(client -> {
					if (client.level == null) return;
					for (var entity : client.level.entitiesForRendering()) {
						if (entity instanceof dev.lilkuzco.cosmos.recovery.CapsuleEntity capsule) {
							if (capsule.chuteOut()) seen[0] = 1;
						}
					}
				});
				if (seen[0] == 1) found = poll;
			}
			// A burst from the moment the canopy is out, so the whole chute descent is on record.
			for (int frame = 0; frame < 14; frame++) {
				context.takeScreenshot(String.format("cosmos_capsule_chute_%02d", frame));
				context.waitTicks(4);
			}
		}
	}

	/** Every cosmos block that must be reachable from the creative menu. */
	private static final String[] CREATIVE_BLOCKS = {
			"launch_pad", "pad_frame", "fuel_tank", "satellite_console", "oxygen_station",
			"electrolyser", "regolith_kiln", "ammonia_cracker",
			"regolith", "mare_basalt", "lunar_ice", "sintered_regolith",
			"tholin_sand", "haze_bedrock", "ammonia_ice" };

	/**
	 * A report with every field populated, for photographing the panel.
	 *
	 * <p>Deliberately awkward: {@code sintered_regolith} is longer than the column is wide and
	 * must come back clipped, the coverage is partial so the figure renders in the warning
	 * colour, and there are five returns against a panel with room for four.
	 *
	 * @param blind the zero-coverage case, which takes a different path through the panel
	 */
	private static ReconImager.Report boardReport(String satelliteId, boolean blind) {
		if (blind) {
			return new ReconImager.Report(satelliteId, -1280.0, 2432.0, 1443.0,
					0, 173, 0, new LinkedHashMap<>(), List.of());
		}
		Map<String, Integer> composition = new LinkedHashMap<>();
		composition.put("grass_block", 41);
		composition.put("stone", 22);
		composition.put("sintered_regolith", 9);
		composition.put("water", 6);
		return new ReconImager.Report(satelliteId, -1280.0, 2432.0, 1443.0,
				96, 173, 14, composition,
				List.of(new BlockPos(-1312, 71, 2401), new BlockPos(-1298, 68, 2440),
						new BlockPos(-1275, 74, 2418), new BlockPos(-1260, 70, 2455),
						new BlockPos(-1244, 69, 2390)));
	}
}
