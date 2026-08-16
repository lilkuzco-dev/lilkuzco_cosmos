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

/** Cosmos' four blocks. No worldgen, no ores - everything here is built, not found. */
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

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
            out.insertAfter(Items.BEACON, LAUNCH_PAD);
            out.insertAfter(LAUNCH_PAD.asItem(), PAD_FRAME);
            out.insertAfter(PAD_FRAME.asItem(), FUEL_TANK);
            out.insertAfter(FUEL_TANK.asItem(), SATELLITE_CONSOLE);
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
