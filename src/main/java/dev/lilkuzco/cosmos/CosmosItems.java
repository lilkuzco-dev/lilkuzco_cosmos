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

    /**
     * A pressure suit. Its DURABILITY is its oxygen.
     *
     * <p>Which means the vanilla durability bar is an oxygen gauge for free, refilling is item
     * repair, and the value is saved and synced by machinery that already exists. No data
     * component, no HUD code, no packet.
     */
    public static class PressureSuitItem extends Item {
        public PressureSuitItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    TooltipDisplay display, Consumer<Component> lines,
                                    TooltipFlag flag) {
            int air = dev.lilkuzco.cosmos.life.LifeSupport.oxygenOf(stack);
            lines.accept(Component.translatable("cosmos.tooltip.suit.oxygen",
                    air / 20, stack.getMaxDamage() / 20));
        }
    }

    /** A portable tank. Right-click to pour it into a worn suit. */
    public static class OxygenTankItem extends Item {
        public OxygenTankItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    TooltipDisplay display, Consumer<Component> lines,
                                    TooltipFlag flag) {
            lines.accept(Component.translatable("cosmos.tooltip.tank.capacity",
                    dev.lilkuzco.cosmos.life.LifeSupport.TANK_TICKS / 20));
        }
    }

    /**
     * The lander. Goes in the payload slot where a satellite would, and changes what a launch is.
     *
     * <p>A rocket with a satellite aboard is something you send. A rocket with this aboard is
     * somewhere you go: the pad puts anyone standing on the apron into the vehicle at ignition.
     */
    public static class LanderItem extends Item {
        public LanderItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    TooltipDisplay display, Consumer<Component> lines,
                                    TooltipFlag flag) {
            lines.accept(Component.translatable("cosmos.tooltip.lander.desc"));
            lines.accept(Component.translatable("cosmos.tooltip.lander.mass",
                    (int) dev.lilkuzco.cosmos.moon.LunarLander.WET_MASS_KG,
                    (int) dev.lilkuzco.cosmos.moon.LunarLander.PROPELLANT_KG));
        }
    }

    public static final Item LUNAR_LANDER = register("lunar_lander",
            props -> new LanderItem(props.stacksTo(1)));

    /**
     * The entry capsule: a heat shield and two chutes, for the outer moon.
     *
     * <p>No engine, and that is the design. Where the Moon charges 444 m/s to land because there
     * is nothing to brake against, the outer moon's atmosphere does it for free — so the vehicle
     * that goes there is a blunt body and a canopy, and an engine would be dead weight.
     *
     * <p>Which payload sits in the pad decides where the flight goes. You pack for the destination.
     */
    public static final Item ENTRY_CAPSULE = register("entry_capsule",
            props -> new LanderItem(props.stacksTo(1)));

    public static final Item ROCKET_LUNAR = register("rocket_lunar",
            props -> new RocketItem(RocketTier.LUNAR, props.stacksTo(1)));

    public static final Item PRESSURE_SUIT = register("pressure_suit",
            props -> new PressureSuitItem(props.stacksTo(1)
                    .durability(dev.lilkuzco.cosmos.life.LifeSupport.SUIT_CAPACITY_TICKS)));

    public static final Item OXYGEN_TANK = register("oxygen_tank",
            props -> new OxygenTankItem(props.stacksTo(16)));

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

    public static final List<Item> ROCKETS =
            List.of(ROCKET_SOUNDING, ROCKET_ORBITAL, ROCKET_HEAVY, ROCKET_LUNAR);
    public static final List<Item> SATELLITES = List.of(SATELLITE_RECON, SATELLITE_COMMS);

    /** The tier an item represents, or null if it is not an airframe. */
    public static RocketTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof RocketItem rocket ? rocket.tier() : null;
    }

    /** Whether this stack is a crewed vehicle of any kind. */
    public static boolean isLander(ItemStack stack) {
        return stack.getItem() instanceof LanderItem;
    }

    /**
     * Where this payload is packed to go.
     *
     * <p>The item is the itinerary. A lander has engines and no heat shield, so it can only go
     * somewhere airless; a capsule has a heat shield and no engines, so it can only go somewhere
     * with air. Neither can do the other's job, which is why one item answers the question.
     */
    public static dev.lilkuzco.cosmos.moon.Destination destinationOf(ItemStack stack) {
        if (stack.getItem() == ENTRY_CAPSULE) return dev.lilkuzco.cosmos.moon.Destination.HAZE;
        if (stack.getItem() == LUNAR_LANDER) return dev.lilkuzco.cosmos.moon.Destination.MOON;
        return null;
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
            out.insertAfter(ROCKET_HEAVY, ROCKET_LUNAR);
            out.insertAfter(ROCKET_LUNAR, SATELLITE_RECON);
            out.insertAfter(SATELLITE_RECON, SATELLITE_COMMS);
            out.insertAfter(SATELLITE_COMMS, LUNAR_LANDER);
            out.insertAfter(LUNAR_LANDER, ENTRY_CAPSULE);
            out.insertAfter(ENTRY_CAPSULE, PRESSURE_SUIT);
            out.insertAfter(PRESSURE_SUIT, OXYGEN_TANK);
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
