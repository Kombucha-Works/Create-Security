package kombuchamc.createsecurity.client;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.obsidian.ObsidianBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class ObsidianTextures {

    public static final SpriteIdentifier CHEST = chest("obsidian");
    public static final SpriteIdentifier CHEST_LEFT = chest("obsidian_left");
    public static final SpriteIdentifier CHEST_RIGHT = chest("obsidian_right");
    public static final SpriteIdentifier ENDER_CHEST = chest("obsidian_ender");

    public static final ChestBlockEntity CHEST_ITEM = new ChestBlockEntity(
            BlockPos.ORIGIN, ObsidianBlocks.OBSIDIAN_CHEST.getDefaultState());
    public static final EnderChestBlockEntity ENDER_CHEST_ITEM = new EnderChestBlockEntity(
            BlockPos.ORIGIN, ObsidianBlocks.OBSIDIAN_ENDER_CHEST.getDefaultState());

    private ObsidianTextures() {}

    private static SpriteIdentifier chest(String name) {
        return new SpriteIdentifier(TexturedRenderLayers.CHEST_ATLAS_TEXTURE,
                new Identifier(CreateSecurity.MOD_ID, "entity/chest/" + name));
    }

    public static SpriteIdentifier chestFor(ChestType type) {
        return switch (type) {
            case LEFT -> CHEST_LEFT;
            case RIGHT -> CHEST_RIGHT;
            default -> CHEST;
        };
    }
}
