package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.camera.CameraBlock;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import kombuchamc.createsecurity.items.TooltipBlockItem;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;

public class RegisterModBlocks {

    public static final Block CAMERA_DISPLAY_BLOCK = registerBlock("camera_display",
            new CameraDisplay(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f)));

    public static final Block CAMERA_BLOCK = registerBlock("camera",
            new CameraBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).nonOpaque()));

    public static final Block CAMERA_LINK_BLOCK = registerCameraLink("camera_link",
            new CameraLinkBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).nonOpaque()));

    public static final Block MONITOR = registerBlockWithTooltip("monitor",
            new MonitorBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).nonOpaque()));

    public static final Block KEYCARD_READER = registerBlockOnly("keycard_reader",
            new KeycardReaderBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).nonOpaque()));

    public static final Block ALARM = registerBlockWithTooltip("alarm",
            new Alarm(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).luminance(state -> state.get(Properties.POWERED) ? 15 : 0)));

    private static Block registerBlock(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(CreateSecurity.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
        return Registry.register(Registries.BLOCK, new Identifier(CreateSecurity.MOD_ID, name), block);
    }

    private static Block registerCameraLink(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(CreateSecurity.MOD_ID, name),
                new kombuchamc.createsecurity.items.CameraLinkBlockItem(block, new FabricItemSettings()));
        return Registry.register(Registries.BLOCK, new Identifier(CreateSecurity.MOD_ID, name), block);
    }

    private static Block registerBlockOnly(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(CreateSecurity.MOD_ID, name), block);
    }

    private static Block registerBlockWithTooltip(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(CreateSecurity.MOD_ID, name),
                new TooltipBlockItem(block, new FabricItemSettings(), "tooltip.create-security." + name));
        return Registry.register(Registries.BLOCK, new Identifier(CreateSecurity.MOD_ID, name), block);
    }

    public static void registerModBlocks() {
        CreateSecurity.LOGGER.info("Registering Mod Blocks for " + CreateSecurity.MOD_ID);
    }

}

