package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderAttachment;
import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltinModelItemRenderer.class)
public class BuiltinModelItemRendererReaderMixin {

    @Inject(method = "render(Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("RETURN"))
    private void createsecurity$drawCarriedReader(ItemStack stack, ModelTransformationMode mode,
                                                  MatrixStack matrices,
                                                  VertexConsumerProvider vertexConsumers,
                                                  int light, int overlay, CallbackInfo ci) {
        if (!KeycardReaderAttachment.isLockedShulkerBox(stack)) return;

        matrices.push();

        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        matrices.translate(-0.5, 1, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                KeycardReaderPlacement.mountState(Direction.UP, KeycardReaderAttachment.isArmed(stack),
                        KeycardReaderAttachment.kindOf(stack)),
                matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
