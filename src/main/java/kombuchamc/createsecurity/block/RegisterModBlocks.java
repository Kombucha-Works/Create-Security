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

    public static final Block CODE_LOCK = registerBlockOnly("code_lock",
            new KeycardReaderBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).nonOpaque()));

    public static final Block KEYCARD_EDITOR = registerBlockWithTooltip("keycard_editor",
            new KeycardEditorBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f)));

    public static final Block TURRET = registerBlockWithTooltip("turret",
            new TurretBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()
                    .luminance(state -> state.get(TurretBlock.LIGHT))));

    public static final Block TURRET_MAST_LOWER = registerBlockOnly("turret_mast_lower",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_CRADLE = registerBlockOnly("turret_cradle",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_SHAFT = registerBlockOnly("turret_shaft",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_GUN_SHOTGUN = registerBlockOnly("turret_gun_shotgun",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_GUN_MINIGUN = registerBlockOnly("turret_gun_minigun",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_GUN_AUTOMATIC = registerBlockOnly("turret_gun_automatic",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_GUN_MINIGUN_ROTOR = registerBlockOnly("turret_gun_minigun_rotor",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block TURRET_GUN_ROCKET = registerBlockOnly("turret_gun_rocket",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_REGULAR_556 = registerBlockOnly("regular_5.56mm_ammo",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_INCENDIARY_556 = registerBlockOnly("incendiary_5.56mm_ammo",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_REGULAR_762 = registerBlockOnly("regular_7.62x51mm_ammo",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_SLUG_12G = registerBlockOnly("12_gauge_slug",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_BUCKSHOT_12G = registerBlockOnly("12_gauge_buckshot",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_INCENDIARY_BUCKSHOT_12G = registerBlockOnly("12_gauge_incendiary_buckshot",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_HE_ROCKET = registerBlockOnly("he_rocket",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block PROJECTILE_ROCKET = registerBlockOnly("rocket",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(2f).nonOpaque()));

    public static final Block ALARM = registerBlockWithTooltip("alarm",
            new Alarm(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1f).luminance(state -> state.get(Properties.LIT) ? 15 : 0)));

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
