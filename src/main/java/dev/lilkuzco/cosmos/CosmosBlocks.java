package dev.lilkuzco.cosmos;

import dev.lilkuzco.cosmos.pad.FuelTankBlock;
import dev.lilkuzco.cosmos.pad.LaunchPadBlock;
import dev.lilkuzco.cosmos.satellite.SatelliteConsoleBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/** Cosmos' blocks: a launch complex, a lunar surface, and the plants that turn one into the other. */
public final class CosmosBlocks {

    /** The controller. Everything about a launch is decided here. */
    public static final Block LAUNCH_PAD = register(
            "launch_pad", LaunchPadBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 8.0F)
                    .sound(SoundType.METAL));

    /** The apron. Cheap and dull on purpose - the pad's cost should be its size, not its recipe. */
    public static final Block PAD_FRAME = register(
            "pad_frame", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL));

    /** Holds propellant. Stands beside the apron, never on it. */
    public static final Block FUEL_TANK = register(
            "fuel_tank", FuelTankBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL));

    /** The planetarium. Live registry view: orbit, next pass, attitude. */
    public static final Block SATELLITE_CONSOLE = register(
            "satellite_console", SatelliteConsoleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .lightLevel(state -> 7)
                    .sound(SoundType.METAL));

    // ---- the Moon --------------------------------------------------------

    /**
     * Regolith. Soft, mineable by hand, and the surface of most of the Moon.
     *
     * <p>Gravel-like {@code strength(0.5F)} on purpose: a body with no atmosphere and no water
     * has nothing to cement its surface, so the top layer is loose dust rather than rock. It
     * being trivially diggable is also what makes a lunar base a build rather than a quarry.
     */
    public static final Block REGOLITH = register(
            "regolith", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.SAND));

    /** The dark seas. Solidified basalt - proper rock, needs a pickaxe. */
    public static final Block MARE_BASALT = register(
            "mare_basalt", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(1.4F, 4.2F)
                    .sound(SoundType.BASALT));

    /** Polar ice, in the permanently shadowed floors of the polar craters. */
    public static final Block LUNAR_ICE = register(
            "lunar_ice", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(0.9F)
                    .sound(SoundType.GLASS));

    /** Refills a pressure suit while a player stands near it. */
    public static final Block OXYGEN_STATION = register(
            "oxygen_station", dev.lilkuzco.cosmos.life.OxygenStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .lightLevel(state -> 5)
                    .sound(SoundType.METAL));

    // ---- in-situ resource utilisation ------------------------------------

    /**
     * Melts polar ice, splits the water, and mixes propellant. Only runs where the ice is.
     */
    public static final Block ELECTROLYSER = register(
            "electrolyser",
            properties -> new dev.lilkuzco.cosmos.isru.IsruBlock(properties,
                    dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.ELECTROLYSER),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .lightLevel(state -> state.getValue(
                            dev.lilkuzco.cosmos.isru.IsruBlock.LIT) ? 10 : 0)
                    .sound(SoundType.METAL));

    /** Bakes the ground for air and building material. Works anywhere, and works badly. */
    public static final Block REGOLITH_KILN = register(
            "regolith_kiln",
            properties -> new dev.lilkuzco.cosmos.isru.IsruBlock(properties,
                    dev.lilkuzco.cosmos.isru.IsruRegistry.Kind.KILN),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .lightLevel(state -> state.getValue(
                            dev.lilkuzco.cosmos.isru.IsruBlock.LIT) ? 13 : 0)
                    .sound(SoundType.METAL));

    /** What a base is built out of: the ground it stands on, baked until it holds together. */
    public static final Block SINTERED_REGOLITH = register(
            "sintered_regolith", Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 8.0F)
                    .sound(SoundType.STONE));

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
            out.insertAfter(Items.BEACON, LAUNCH_PAD);
            out.insertAfter(LAUNCH_PAD.asItem(), PAD_FRAME);
            out.insertAfter(PAD_FRAME.asItem(), FUEL_TANK);
            out.insertAfter(FUEL_TANK.asItem(), SATELLITE_CONSOLE);
            out.insertAfter(SATELLITE_CONSOLE.asItem(), OXYGEN_STATION);
            out.insertAfter(OXYGEN_STATION.asItem(), ELECTROLYSER);
            out.insertAfter(ELECTROLYSER.asItem(), REGOLITH_KILN);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(out -> {
            out.insertAfter(Items.GRAVEL, REGOLITH);
            out.insertAfter(REGOLITH.asItem(), MARE_BASALT);
            out.insertAfter(MARE_BASALT.asItem(), LUNAR_ICE);
            out.insertAfter(LUNAR_ICE.asItem(), SINTERED_REGOLITH);
        });
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        Identifier id = Cosmos.id(name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                factory.apply(properties.setId(blockKey)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
        return block;
    }

    private CosmosBlocks() {}
}
