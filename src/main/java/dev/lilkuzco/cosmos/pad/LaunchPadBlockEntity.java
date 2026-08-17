package dev.lilkuzco.cosmos.pad;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosBlockEntities;
import dev.lilkuzco.cosmos.CosmosItems;
import dev.lilkuzco.cosmos.propellant.Propellant;
import dev.lilkuzco.cosmos.propellant.Propellants;
import dev.lilkuzco.cosmos.rocket.LaunchPipeline;
import dev.lilkuzco.cosmos.rocket.RocketEntity;
import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.cosmos.satellite.SatellitePayload;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The launch pad controller: assembly, fuelling, countdown and ignition.
 *
 * <p>Two slots — airframe and payload — plus a propellant reservoir whose capacity is set by how
 * many tanks stand beside the apron. The readout a player sees before ignition comes from
 * kinetics' own {@code Propulsion.assess}, so the pad cannot promise a flight the physics will
 * not deliver.
 *
 * <p><b>The pad never refuses a launch it merely disapproves of.</b> It refuses an incomplete
 * structure, an empty tank and a missing airframe, because those are assembly errors. It will
 * happily light an under-fuelled rocket that is going to fall back into the sea, having told the
 * player exactly how many m/s it is short. Failing honestly is the point of RD3, and a pad that
 * quietly prevented every unsuccessful launch would hide the whole design problem.
 */
public class LaunchPadBlockEntity extends BaseContainerBlockEntity {

    public static final int SLOT_ROCKET = 0;
    public static final int SLOT_PAYLOAD = 1;
    private static final int CONTAINER_SIZE = 2;

    /**
     * Propellant density, in kilograms per bucket.
     *
     * <p>A stated abstraction, in the spirit of kinetics' scale audit. Real kerosene is about
     * 0.8 kg/litre, so a real bucket is roughly 4 kg and the orbital vehicle's 17.2 tonnes would
     * be <b>4,300 buckets</b>. One hundred kilograms per bucket makes it 172 - about six tanks -
     * which is a build, not a chore. Nothing physical depends on this number: it converts a
     * player-facing volume into the mass kinetics actually integrates, and that is all.
     */
    public static final double KG_PER_BUCKET = 100.0;

    public static final int BASE_CAPACITY_MB = 16_000;

    // Countdown length. Long enough to be an event and to run away from, short enough that a
    // player does not resent a mistake.
    public static final int COUNTDOWN_TICKS = 200;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_NO_ROCKET = 1;
    public static final int STATUS_BAD_STRUCTURE = 2;
    public static final int STATUS_NO_FUEL = 3;
    public static final int STATUS_READY = 4;
    public static final int STATUS_COUNTDOWN = 5;
    public static final int STATUS_SHORTFALL = 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** Propellant lives here, as a real fluid storage that pipes and buckets can both reach. */
    private final PropellantTank tank = new PropellantTank(this);

    private int countdown = -1;
    private int status = STATUS_IDLE;
    private int lastTwrTimes100;
    private int lastBudgetPercent;
    private int lastCapacityMb = BASE_CAPACITY_MB;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(CosmosBlockEntities.LAUNCH_PAD, pos, state);
    }

    // ---- container --------------------------------------------------------

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) { this.items = replacement; }

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.cosmos.launch_pad");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LaunchPadMenu(containerId, inventory, this, dataAccess);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_ROCKET -> CosmosItems.tierOf(stack) != null;
            case SLOT_PAYLOAD -> CosmosItems.payloadOf(stack) != null
                    || CosmosItems.isLander(stack);
            default -> false;
        };
    }

    // ---- persistence ------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.tank.load(input);
        this.countdown = input.getIntOr("countdown", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.tank.save(output);
        output.putInt("countdown", countdown);
    }

    // ---- state ------------------------------------------------------------

    /** The storage pipes and buckets fill. Exposed for {@code FluidStorage.SIDED}. */
    public PropellantTank tank() { return tank; }

    /** The tier whose apron ring defines this pad's reservoir - the slotted one, else the largest. */
    public RocketTier reservoirTier() {
        RocketTier tier = selectedTier();
        return tier == null ? RocketTier.LADDER.get(RocketTier.LADDER.size() - 1) : tier;
    }

    public int fuelMb() { return tank.millibuckets(); }

    public int capacityMb() { return lastCapacityMb; }

    public double fuelKg() { return fuelMb() / 1000.0 * KG_PER_BUCKET; }

    public Propellant propellant() { return tank.grade(); }

    public int status() { return status; }

    public int countdown() { return countdown; }

    public RocketTier selectedTier() { return CosmosItems.tierOf(items.get(SLOT_ROCKET)); }

    public SatellitePayload selectedPayload() {
        return CosmosItems.payloadOf(items.get(SLOT_PAYLOAD));
    }

    public boolean beginCountdown() {
        if (countdown >= 0) return false;
        if (status != STATUS_READY && status != STATUS_SHORTFALL) return false;
        countdown = COUNTDOWN_TICKS;
        setChanged();
        return true;
    }

    public void abort() {
        if (countdown < 0) return;
        countdown = -1;
        setChanged();
    }

    /**
     * Reservoir size: the base plus every tank standing beside the apron.
     *
     * <p>Measured against the largest tier the pad's ring could serve rather than the rocket
     * currently slotted, because <b>a player fuels before they assemble</b>. Sizing this off the
     * slotted rocket meant an empty pad reported the base capacity, so a pipeline filled sixteen
     * buckets and stopped - and the tanks the player had built did nothing until a rocket was
     * already in the slot.
     */
    private int computeCapacity() {
        if (level == null) return BASE_CAPACITY_MB;
        int tanks = PadStructure.connectedTanks(level, worldPosition, reservoirTier()).size();
        return BASE_CAPACITY_MB + tanks * FuelTankBlock.CAPACITY_PER_TANK_MB;
    }

    // ---- tick -------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  LaunchPadBlockEntity pad) {
        pad.evaluate(level);

        if (pad.countdown >= 0) {
            if (pad.status != STATUS_COUNTDOWN) {
                // Something changed underneath a running countdown - abort rather than fly a
                // vehicle that no longer matches what the player agreed to.
                pad.countdown = -1;
                pad.setChanged();
            } else {
                pad.tickCountdown(level, pos, state);
            }
        }

        boolean lit = pad.countdown >= 0;
        if (state.getValue(LaunchPadBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LaunchPadBlock.LIT, lit), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    private void tickCountdown(Level level, BlockPos pos, BlockState state) {
        // One beep a second, rising as it closes. The audio IS the countdown UX - a number on a
        // screen nobody is looking at is not a countdown.
        if (countdown % 20 == 0 && level instanceof ServerLevel server) {
            int secondsLeft = countdown / 20;
            float pitch = secondsLeft <= 3 ? 1.6F : 0.9F;
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.BLOCKS, 1.4F, pitch);
        }
        countdown--;
        setChanged();

        if (countdown < 0) {
            ignite(level, pos);
        }
    }

    /** Assessment, run every tick so the readout is never stale. */
    private void evaluate(Level level) {
        if (countdown >= 0) { status = STATUS_COUNTDOWN; return; }

        lastCapacityMb = computeCapacity();

        RocketTier tier = selectedTier();
        if (tier == null) { status = STATUS_NO_ROCKET; return; }

        PadStructure.Check check = PadStructure.check(level, worldPosition, tier);
        if (!check.valid()) { status = STATUS_BAD_STRUCTURE; return; }

        if (tank.isEmpty()) { status = STATUS_NO_FUEL; return; }

        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) { status = STATUS_BAD_STRUCTURE; return; }

        LaunchPipeline pipeline = new LaunchPipeline(kinetics.constants());
        double gravity = kinetics.constants().d("gravity.g0");
        LaunchPipeline.Readout readout = pipeline.assess(tier, propellant(), fuelKg(), gravity);

        lastTwrTimes100 = (int) Math.round(readout.twrSeaLevel() * 100.0);
        lastBudgetPercent = (int) Math.round(readout.budgetFraction() * 100.0);
        status = readout.reachesOrbit() ? STATUS_READY : STATUS_SHORTFALL;
    }

    /** Light it. From here the flight belongs to kinetics. */
    private void ignite(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;

        RocketTier tier = selectedTier();
        SatellitePayload payload = selectedPayload();
        if (tier == null) return;

        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) {
            Cosmos.LOG.error("ignition aborted: kinetics service unavailable");
            return;
        }

        // Crew: anyone standing on the apron when the count reaches zero, and only for a flight
        // with a lander aboard. A countdown you can walk away from is what makes the countdown a
        // decision rather than a delay, and a satellite launch should never take passengers.
        boolean crewed = CosmosItems.isLander(items.get(SLOT_PAYLOAD));
        java.util.List<net.minecraft.server.level.ServerPlayer> crew =
                crewed ? PadStructure.playersOnPad(server, pos, tier) : java.util.List.of();

        // Only what the vehicle can actually carry. fuelKg() is the whole reservoir, which on a
        // large pad is many times a small rocket's tankage; buildProfile clamps it, so the flight
        // was always correct - it was the pad that then threw the remainder away.
        double loaded = Math.min(fuelKg(), tier.fuelCapacityKg());

        RocketEntity rocket = RocketEntity.launch(server, pos, tier, propellant(), loaded,
                payload, crewed, crew, this.getBlockState());
        if (rocket == null) {
            Cosmos.LOG.error("ignition failed to create a rocket entity at {}", pos);
            return;
        }

        // Consume everything the launch used. A rocket is not reusable in Phase A.
        items.set(SLOT_ROCKET, ItemStack.EMPTY);
        items.set(SLOT_PAYLOAD, ItemStack.EMPTY);
        tank.consume(loaded, KG_PER_BUCKET);
        countdown = -1;
        setChanged();

        server.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                4.0F, 0.5F);
    }

    // ---- menu data --------------------------------------------------------

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> status;
                case 1 -> Math.max(countdown, 0);
                case 2 -> tank.millibuckets();
                case 3 -> lastCapacityMb;
                case 4 -> lastTwrTimes100;
                case 5 -> lastBudgetPercent;
                case 6 -> Propellants.LADDER.indexOf(propellant());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() { return 7; }
    };

    public ContainerData dataAccess() { return dataAccess; }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }
}
