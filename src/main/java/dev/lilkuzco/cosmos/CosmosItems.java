package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.rocket.RocketTier;
import dev.lilkuzco.cosmos.satellite.SatellitePayload;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * The things you put in a launch pad. Two families: airframes and payloads.
 *
 * <p>An assembled launch is a rocket item, a payload item, and enough propellant. Splitting the
 * airframe from the payload is what makes the same rocket useful twice - fly a recon satellite
 * today and a comms relay next week without rebuilding the vehicle.
 */
public final class CosmosItems {

    /** An airframe. Its tooltip states the honest performance, including what fuel it needs. */
    public static class RocketItem extends Item {
        private final RocketTier tier;

        public RocketItem(RocketTier tier, Properties properties) {
            super(properties);
            this.tier = tier;
        }

        public RocketTier tier() { return tier; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    TooltipDisplay display, Consumer<Component> lines,
                                    TooltipFlag flag) {
            lines.accept(Component.translatable("cosmos.tooltip.rocket.stages",
                    tier.stages().size()));
            lines.accept(Component.translatable("cosmos.tooltip.rocket.fuel",
                    (int) tier.fuelCapacityKg()));
            lines.accept(Component.translatable("cosmos.tooltip.rocket.payload",
                    (int) tier.payloadCapacityKg()));
            lines.accept(Component.translatable("cosmos.tooltip.rocket.pad",
                    tier.padRadius() * 2 + 1, tier.padHeight()));
        }
    }

    /** A payload. What the satellite will do once it is up there. */
    public static class SatelliteItem extends Item {
        private final SatellitePayload payload;

        public SatelliteItem(SatellitePayload payload, Properties properties) {
            super(properties);
            this.payload = payload;
        }

        public SatellitePayload payload() { return payload; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    TooltipDisplay display, Consumer<Component> lines,
                                    TooltipFlag flag) {
            lines.accept(Component.translatable(payload.translationKey() + ".desc"));
            lines.accept(Component.translatable("cosmos.tooltip.satellite.cone",
                    (int) payload.sensorHalfAngleDeg()));
        }
    }

    public static final Item ROCKET_SOUNDING = register("rocket_sounding",
            props -> new RocketItem(RocketTier.SOUNDING, props.stacksTo(1)));
    public static final Item ROCKET_ORBITAL = register("rocket_orbital",
            props -> new RocketItem(RocketTier.ORBITAL, props.stacksTo(1)));
    public static final Item ROCKET_HEAVY = register("rocket_heavy",
            props -> new RocketItem(RocketTier.HEAVY, props.stacksTo(1)));

    public static final Item SATELLITE_RECON = register("satellite_recon",
            props -> new SatelliteItem(SatellitePayload.RECON, props.stacksTo(1)));
    public static final Item SATELLITE_COMMS = register("satellite_comms",
            props -> new SatelliteItem(SatellitePayload.COMMS, props.stacksTo(1)));

    public static final List<Item> ROCKETS = List.of(ROCKET_SOUNDING, ROCKET_ORBITAL, ROCKET_HEAVY);
    public static final List<Item> SATELLITES = List.of(SATELLITE_RECON, SATELLITE_COMMS);

    /** The tier an item represents, or null if it is not an airframe. */
    public static RocketTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof RocketItem rocket ? rocket.tier() : null;
    }

    /** The payload an item represents, or null if it is not a payload. */
    public static SatellitePayload payloadOf(ItemStack stack) {
        return stack.getItem() instanceof SatelliteItem satellite ? satellite.payload() : null;
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(out -> {
            out.insertAfter(Items.FIREWORK_ROCKET, ROCKET_SOUNDING);
            out.insertAfter(ROCKET_SOUNDING, ROCKET_ORBITAL);
            out.insertAfter(ROCKET_ORBITAL, ROCKET_HEAVY);
            out.insertAfter(ROCKET_HEAVY, SATELLITE_RECON);
            out.insertAfter(SATELLITE_RECON, SATELLITE_COMMS);
        });
    }

    private static Item register(String name, java.util.function.Function<Item.Properties, Item> factory) {
        Identifier id = Cosmos.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                factory.apply(new Item.Properties().setId(key)));
    }

    private CosmosItems() {}
}
