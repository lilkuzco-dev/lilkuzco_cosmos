package dev.lilkuzco.cosmos.pad;

import dev.lilkuzco.cosmos.CosmosItems;
import dev.lilkuzco.cosmos.CosmosMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The pad's two slots and its readout.
 *
 * <p>The readout travels as {@link ContainerData}, which is a handful of synced ints - status,
 * countdown, fuel, capacity, T/W and the delta-v percentage. That is enough for the screen to
 * render everything a player needs before ignition without a custom packet, and it updates every
 * tick for free.
 */
public class LaunchPadMenu extends AbstractContainerMenu {

	public static final int DATA_STATUS = 0;
	public static final int DATA_COUNTDOWN = 1;
	public static final int DATA_FUEL = 2;
	public static final int DATA_CAPACITY = 3;
	public static final int DATA_TWR_X100 = 4;
	public static final int DATA_BUDGET_PERCENT = 5;
	public static final int DATA_PROPELLANT = 6;

	private static final int SLOT_COUNT = 2;
	private static final int DATA_COUNT = 7;

	private final Container container;
	private final ContainerData data;

	/** Client-side constructor. */
	public LaunchPadMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(SLOT_COUNT),
				new SimpleContainerData(DATA_COUNT));
	}

	public LaunchPadMenu(int containerId, Inventory inventory, Container container,
			ContainerData data) {
		super(CosmosMenus.LAUNCH_PAD, containerId);
		checkContainerSize(container, SLOT_COUNT);
		checkContainerDataCount(data, DATA_COUNT);
		this.container = container;
		this.data = data;

		addSlot(new Slot(container, LaunchPadBlockEntity.SLOT_ROCKET, 44, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CosmosItems.tierOf(stack) != null;
			}
		});
		addSlot(new Slot(container, LaunchPadBlockEntity.SLOT_PAYLOAD, 44, 62) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CosmosItems.payloadOf(stack) != null;
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, 162));
		}

		addDataSlots(data);
	}

	public int status() { return data.get(DATA_STATUS); }

	public int countdownTicks() { return data.get(DATA_COUNTDOWN); }

	public int fuelMb() { return data.get(DATA_FUEL); }

	public int capacityMb() { return data.get(DATA_CAPACITY); }

	public double twr() { return data.get(DATA_TWR_X100) / 100.0; }

	public int budgetPercent() { return data.get(DATA_BUDGET_PERCENT); }

	public int propellantIndex() { return data.get(DATA_PROPELLANT); }

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < SLOT_COUNT) {
			if (!moveItemStackTo(stack, SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
		} else if (CosmosItems.tierOf(stack) != null) {
			if (!moveItemStackTo(stack, LaunchPadBlockEntity.SLOT_ROCKET,
					LaunchPadBlockEntity.SLOT_ROCKET + 1, false)) return ItemStack.EMPTY;
		} else if (CosmosItems.payloadOf(stack) != null) {
			if (!moveItemStackTo(stack, LaunchPadBlockEntity.SLOT_PAYLOAD,
					LaunchPadBlockEntity.SLOT_PAYLOAD + 1, false)) return ItemStack.EMPTY;
		} else {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}
}
