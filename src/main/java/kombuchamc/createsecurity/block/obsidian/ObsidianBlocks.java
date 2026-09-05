package kombuchamc.createsecurity.block.obsidian;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.items.TooltipBlockItem;
import kombuchamc.createsecurity.mixin.BlockEntityTypeAccessor;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

public class ObsidianBlocks {

    private static final String TOOLTIP = "tooltip.create-security.obsidian_security";

    public static final Block OBSIDIAN_DOOR = register("obsidian_door",
            new ObsidianDoorBlock(base().nonOpaque()));

    public static final Block OBSIDIAN_TRAPDOOR = register("obsidian_trapdoor",
            new ObsidianTrapdoorBlock(base().nonOpaque()));

    public static final Block OBSIDIAN_CHEST = register("obsidian_chest",
            new ObsidianChestBlock(base().nonOpaque()));

    public static final Block OBSIDIAN_ENDER_CHEST = register("obsidian_ender_chest",
            new ObsidianEnderChestBlock(base().nonOpaque().luminance(state -> 7)));

    public static final Block OBSIDIAN_BARREL = register("obsidian_barrel",
            new ObsidianBarrelBlock(base()));

    public static final Block OBSIDIAN_FURNACE = register("obsidian_furnace",
            new ObsidianFurnaceBlock(base().luminance(litLevel())));

    public static final Block OBSIDIAN_SMOKER = register("obsidian_smoker",
            new ObsidianSmokerBlock(base().luminance(litLevel())));

    public static final Block OBSIDIAN_BLAST_FURNACE = register("obsidian_blast_furnace",
            new ObsidianBlastFurnaceBlock(base().luminance(litLevel())));

    public static final List<Block> ALL = List.of(OBSIDIAN_DOOR, OBSIDIAN_TRAPDOOR, OBSIDIAN_CHEST,
            OBSIDIAN_ENDER_CHEST, OBSIDIAN_BARREL, OBSIDIAN_FURNACE, OBSIDIAN_SMOKER,
            OBSIDIAN_BLAST_FURNACE);

    public static boolean isSecured(BlockState state) {
        return ALL.contains(state.getBlock());
    }

    private static AbstractBlock.Settings base() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN)
                .strength(50f, 3600000f)
                .requiresTool()
                .pistonBehavior(PistonBehavior.BLOCK);
    }

    private static ToIntFunction<BlockState> litLevel() {
        return state -> state.get(AbstractFurnaceBlock.LIT) ? 13 : 0;
    }

    private static Block register(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(CreateSecurity.MOD_ID, name),
                new TooltipBlockItem(block, new FabricItemSettings(), TOOLTIP));
        return Registry.register(Registries.BLOCK, new Identifier(CreateSecurity.MOD_ID, name), block);
    }

    public static void registerBlockEntitySupport() {
        allow(BlockEntityType.CHEST, OBSIDIAN_CHEST);
        allow(BlockEntityType.ENDER_CHEST, OBSIDIAN_ENDER_CHEST);
        allow(BlockEntityType.BARREL, OBSIDIAN_BARREL);
        allow(BlockEntityType.FURNACE, OBSIDIAN_FURNACE);
        allow(BlockEntityType.SMOKER, OBSIDIAN_SMOKER);
        allow(BlockEntityType.BLAST_FURNACE, OBSIDIAN_BLAST_FURNACE);
    }

    private static void allow(BlockEntityType<?> type, Block block) {
        BlockEntityTypeAccessor accessor = (BlockEntityTypeAccessor) type;
        Set<Block> blocks = new HashSet<>(accessor.getBlocks());
        blocks.add(block);
        accessor.setBlocks(blocks);
    }

    public static void registerObsidianBlocks() {
        registerBlockEntitySupport();
        CreateSecurity.LOGGER.info("Registering Obsidian Blocks for " + CreateSecurity.MOD_ID);
    }
}
