package dev.lilkuzco.cosmos.client;

import dev.lilkuzco.cosmos.pad.LaunchPadBlockEntity;
import dev.lilkuzco.cosmos.pad.LaunchPadMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The pad readout, also drawn rather than textured.
 *
 * <p>The delta-v bar is the whole screen. It fills toward the orbital budget and turns from amber
 * to green as it crosses it, so a player learns the relationship between propellant grade and
 * orbit by watching the bar move rather than by reading a wiki. Every number on it comes from
 * kinetics' own launch assessment.
 */
public class LaunchPadScreen extends AbstractContainerScreen<LaunchPadMenu> {

	private static final int PANEL = 0xFF11161F;
	private static final int TEXT = 0xFFBFD4E6;
	private static final int TEXT_DIM = 0xFF6C8299;
	private static final int GOOD = 0xFF57D6A2;
	private static final int WARN = 0xFFE8B25A;
	private static final int BAD = 0xFFE8865A;
	private static final int BAR_BACK = 0xFF1B2836;

	public LaunchPadScreen(LaunchPadMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 186);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY,
			float partialTick) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, 0xF00A0E14);
		g.fill(x + 4, y + 14, x + imageWidth - 4, y + 96, PANEL);

		// The two slots.
		slotFrame(g, x + 43, y + 35);
		slotFrame(g, x + 43, y + 61);
		g.text(font, Component.translatable("cosmos.pad.slot_rocket"), x + 64, y + 39, TEXT_DIM);
		g.text(font, Component.translatable("cosmos.pad.slot_payload"), x + 64, y + 65, TEXT_DIM);

		int status = menu.status();

		// Propellant and fuel.
		int fuel = menu.fuelMb();
		int capacity = Math.max(1, menu.capacityMb());
		g.text(font, Component.translatable("cosmos.pad.fuel",
						fuel / 1000, capacity / 1000), x + 8, y + 18, TEXT_DIM);
		bar(g, x + 8, y + 27, imageWidth - 16, 4, (double) fuel / capacity,
				fuel > 0 ? GOOD : BAD);

		// The delta-v bar, which is the point of the screen.
		int percent = menu.budgetPercent();
		g.text(font, Component.translatable("cosmos.pad.delta_v", percent), x + 8, y + 78, percent >= 100 ? GOOD : WARN);
		bar(g, x + 8, y + 87, imageWidth - 16, 5, percent / 100.0,
				percent >= 100 ? GOOD : WARN);
		// The budget line, so 100% is a place on the bar rather than a number to remember.
		int lineX = x + 8 + (imageWidth - 16) - 1;
		g.fill(lineX, y + 85, lineX + 1, y + 94, TEXT);

		String twr = String.format("T/W %.2f", menu.twr());
		g.text(font, Component.literal(twr), x + imageWidth - 8 - font.width(twr), y + 78, menu.twr() > 1.0 ? TEXT : BAD);

		g.text(font, Component.translatable(statusKey(status)), x + 8, y + 100, statusColour(status));

		if (status == LaunchPadBlockEntity.STATUS_COUNTDOWN) {
			int seconds = (menu.countdownTicks() + 19) / 20;
			String count = "T-" + seconds;
			g.text(font, Component.literal(count), x + imageWidth / 2 - font.width(count) * 2, y + 46, seconds <= 3 ? BAD : WARN);
		}
	}

	private static String statusKey(int status) {
		return switch (status) {
			case LaunchPadBlockEntity.STATUS_NO_ROCKET -> "cosmos.pad.status.no_rocket";
			case LaunchPadBlockEntity.STATUS_BAD_STRUCTURE -> "cosmos.pad.status.bad_structure";
			case LaunchPadBlockEntity.STATUS_NO_FUEL -> "cosmos.pad.status.no_fuel";
			case LaunchPadBlockEntity.STATUS_READY -> "cosmos.pad.status.ready";
			case LaunchPadBlockEntity.STATUS_COUNTDOWN -> "cosmos.pad.status.countdown";
			case LaunchPadBlockEntity.STATUS_SHORTFALL -> "cosmos.pad.status.shortfall";
			default -> "cosmos.pad.status.idle";
		};
	}

	private static int statusColour(int status) {
		return switch (status) {
			case LaunchPadBlockEntity.STATUS_READY, LaunchPadBlockEntity.STATUS_COUNTDOWN -> GOOD;
			case LaunchPadBlockEntity.STATUS_SHORTFALL -> WARN;
			case LaunchPadBlockEntity.STATUS_IDLE -> TEXT_DIM;
			default -> BAD;
		};
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction,
			int colour) {
		g.fill(x, y, x + w, y + h, BAR_BACK);
		int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * w);
		if (filled > 0) g.fill(x, y, x + filled, y + h, colour);
	}

	private static void slotFrame(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2A3644);
		g.fill(x, y, x + 16, y + 16, 0xFF0D1219);
	}

}
