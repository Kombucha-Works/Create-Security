package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.obsidian.ObsidianChestBlock;
import kombuchamc.createsecurity.block.obsidian.ObsidianEnderChestBlock;
import kombuchamc.createsecurity.client.ObsidianTextures;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltinModelItemRenderer.class)
public class BuiltinModelItemRendererObsidianMixin {

    @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Inject(method = "render(Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("HEAD"), cancellable = true)
    private void createsecurity$renderObsidianContainer(ItemStack stack, ModelTransformationMode mode,
                                                        MatrixStack matrices,
                                                        VertexConsumerProvider vertexConsumers,
                                                        int light, int overlay, CallbackInfo ci) {
        Block block = stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        BlockEntity standIn = null;
        if (block instanceof ObsidianChestBlock) {
            standIn = ObsidianTextures.CHEST_ITEM;
        } else if (block instanceof ObsidianEnderChestBlock) {
            standIn = ObsidianTextures.ENDER_CHEST_ITEM;
        }
        if (standIn == null) return;

        blockEntityRenderDispatcher.renderEntity(standIn, matrices, vertexConsumers, light, overlay);
        ci.cancel();
    }
}
