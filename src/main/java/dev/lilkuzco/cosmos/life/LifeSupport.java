package dev.lilkuzco.cosmos.life;

import dev.lilkuzco.cosmos.Cosmos;
import dev.lilkuzco.cosmos.CosmosItems;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Oxygen, and running out of it.
 *
 * <p><b>Oxygen is the pressure suit's durability.</b> That is the whole design. A suit with 6,000
 * uses left is a suit with five minutes of air, the vanilla durability bar becomes an oxygen
 * gauge with no HUD code at all, an anvil-less refill is just item repair, and the value is saved
 * and synced by machinery Minecraft already has. No component, no save data, no packet.
 *
 * <p>Where a player is breathable is decided by <b>kinetics</b>, not by a dimension whitelist here:
 * a dimension registered with a vacuum atmosphere has no air, and one registered with an
 * atmosphere does. The Moon needs no special case; it is simply the dimension whose
 * {@code Atmosphere} is vacuum.
 *
 * <p>Running out is loud and slow. Thirty seconds of warning, then two hearts a second — about
 * ten seconds of life for a healthy player. Never an instant death: a player who steps out of an
 * airlock and immediately dies has learned nothing, and one who has ten seconds to sprint back
 * has learned exactly the right thing.
 */
public final class LifeSupport {

    /** Full suit: five minutes of air. Long enough to build, short enough to plan around. */
    public static final int SUIT_CAPACITY_TICKS = 6000;

    /** A portable tank: two and a half minutes. */
    public static final int TANK_TICKS = 3000;

    /** How fast an oxygen station refills a suit, in ticks of air per tick. */
    public static final int STATION_REFILL_RATE = 20;

    /** How close a player must be to a station to be topped up. */
    public static final double STATION_RANGE = 8.0;

    /** Air remaining at which the warning starts: thirty seconds. */
    public static final int WARNING_TICKS = 600;

    /** Damage once the air is gone, applied every second. */
    public static final float SUFFOCATION_DAMAGE = 2.0F;

    public static final ResourceKey<DamageType> ASPHYXIATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Cosmos.id("asphyxiation"));

    private LifeSupport() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LifeSupport::tick);
    }

    /**
     * Whether a dimension has breathable air.
     *
     * <p>Asked of kinetics rather than answered here. A dimension kinetics knows as vacuum has no
     * drag, no lift, useless parachutes AND no air to breathe - one fact, one source. An
     * unregistered dimension is treated as breathable, so a world without cosmos' dimensions
     * registered never suffocates anybody.
     */
    public static boolean isBreathable(ResourceKey<Level> dimension) {
        KineticsService kinetics = KineticsMod.service();
        if (kinetics == null) return true;
        var environment = kinetics.environmentOf(dimension);
        if (environment == null) return true;
        return environment.atmosphere().isPresent();
    }

    /** Air remaining in a suit stack, in ticks. */
    public static int oxygenOf(ItemStack suit) {
        if (suit.isEmpty() || !(suit.getItem() instanceof CosmosItems.PressureSuitItem)) return 0;
        return Math.max(0, suit.getMaxDamage() - suit.getDamageValue());
    }

    /** Set a suit's remaining air, clamped. */
    public static void setOxygen(ItemStack suit, int ticks) {
        if (suit.isEmpty()) return;
        int max = suit.getMaxDamage();
        int clamped = Math.max(0, Math.min(max, ticks));
        // Never let the suit reach zero durability: a suit that BREAKS when the air runs out
        // punishes the player twice for one mistake, and leaves them with nothing to refill.
        suit.setDamageValue(Math.min(max - 1, max - clamped));
    }

    /** Add air, returning how much was actually taken. */
    public static int refill(ItemStack suit, int ticks) {
        int before = oxygenOf(suit);
        int after = Math.min(suit.getMaxDamage(), before + ticks);
        setOxygen(suit, after);
        return after - before;
    }

    /**
     * The suit a player has, or empty.
     *
     * <p>Anywhere in the inventory counts, not just an equipment slot. A player who has crafted a
     * suit and stepped onto the Moon has done the thing the mechanic is about; making them also
     * discover which slot it goes in before it works would be a puzzle about the UI, not about
     * vacuum.
     */
    public static ItemStack wornSuit(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof CosmosItems.PressureSuitItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Put a line on the action bar.
     *
     * <p>Sent as a packet rather than a chat message: an oxygen gauge that scrolls the chat log
     * away every tick would be worse than no gauge at all.
     */
    public static void actionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            ServerLevel level = player.level();
            if (isBreathable(level.dimension())) continue;
            // Inside a spacecraft. A vehicle that carries you to the Moon has life support in it;
            // charging a suit for the whole four-minute coast would mean arriving with one minute
            // of air and no way to spend it, which is a budget problem invented by bookkeeping
            // rather than by the mission.
            if (inPressurisedVehicle(player)) continue;

            ItemStack suit = wornSuit(player);

            if (suit.isEmpty()) {
                // No suit at all in vacuum. Same treatment as an empty one - warned, then hurt.
                warn(player, 0);
                if (player.tickCount % 20 == 0) suffocate(player, level);
                continue;
            }

            if (nearStation(level, player.blockPosition())) {
                if (oxygenOf(suit) < suit.getMaxDamage()) refill(suit, STATION_REFILL_RATE);
                continue;
            }

            int air = oxygenOf(suit);
            if (air > 0) {
                setOxygen(suit, air - 1);
                if (air <= WARNING_TICKS) warn(player, air);
                continue;
            }

            warn(player, 0);
            if (player.tickCount % 20 == 0) suffocate(player, level);
        }
    }

    /** Whether the player is riding something of ours, and therefore breathing its air. */
    public static boolean inPressurisedVehicle(ServerPlayer player) {
        var vehicle = player.getVehicle();
        return vehicle instanceof dev.lilkuzco.cosmos.moon.TransitEntity
                || vehicle instanceof dev.lilkuzco.cosmos.rocket.RocketEntity
                || vehicle instanceof dev.lilkuzco.cosmos.recovery.CapsuleEntity;
    }

    private static boolean nearStation(ServerLevel level, BlockPos around) {
        int r = (int) Math.ceil(STATION_RANGE);
        // A small cube scan. Cheap, and only runs for players actually standing in vacuum.
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-r, -r, -r),
                around.offset(r, r, r))) {
            if (!level.getBlockState(pos).is(dev.lilkuzco.cosmos.CosmosBlocks.OXYGEN_STATION)) {
                continue;
            }
            if (pos.distSqr(around) <= STATION_RANGE * STATION_RANGE) return true;
        }
        return false;
    }

    /** The warning. Loud, on the action bar, and it speeds up as the air runs out. */
    private static void warn(ServerPlayer player, int air) {
        actionBar(player, air > 0
                ? Component.translatable("cosmos.life.warning", air / 20)
                : Component.translatable("cosmos.life.empty"));

        int interval = air > 0 ? Math.max(4, air / 30) : 8;
        if (player.tickCount % interval == 0) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS,
                    0.7F, air > 0 ? 1.4F : 0.7F);
        }
    }

    private static void suffocate(ServerPlayer player, ServerLevel level) {
        DamageSource source = new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ASPHYXIATION));
        player.hurtServer(level, source, SUFFOCATION_DAMAGE);
    }
}
