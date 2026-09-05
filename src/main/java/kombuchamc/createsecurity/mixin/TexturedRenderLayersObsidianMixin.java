package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.obsidian.ObsidianChestBlock;
import kombuchamc.createsecurity.block.obsidian.ObsidianEnderChestBlock;
import kombuchamc.createsecurity.client.ObsidianTextures;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TexturedRenderLayers.class)
public class TexturedRenderLayersObsidianMixin {

    @Inject(method = "getChestTextureId(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/block/enums/ChestType;Z)Lnet/minecraft/client/util/SpriteIdentifier;",
            at = @At("HEAD"), cancellable = true)
    private static void createsecurity$obsidianChestTexture(BlockEntity blockEntity, ChestType type,
                                                            boolean christmas,
                                                            CallbackInfoReturnable<SpriteIdentifier> cir) {
        Block block = blockEntity.getCachedState().getBlock();
        if (block instanceof ObsidianEnderChestBlock) {
            cir.setReturnValue(ObsidianTextures.ENDER_CHEST);
        } else if (block instanceof ObsidianChestBlock) {
            cir.setReturnValue(ObsidianTextures.chestFor(type));
        }
    }
}
