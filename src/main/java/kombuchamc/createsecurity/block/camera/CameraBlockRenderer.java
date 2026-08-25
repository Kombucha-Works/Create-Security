package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class CameraBlockRenderer extends GeoBlockRenderer<CameraBlockEntity> {
    public CameraBlockRenderer(BlockEntityRendererFactory.Context context) {
        super(new CameraBlockModel());
    }

    @Override
    public void actuallyRender(MatrixStack poseStack, CameraBlockEntity animatable, BakedGeoModel model, RenderLayer renderType,
                               VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Direction facing = animatable.getCachedState().get(CameraBlock.FACING);
        if (!facing.getAxis().isHorizontal()) {
            return;
        }

        if (CameraViewManager.isPreRendering()) {
            BlockPos current = CameraViewManager.getCurrentCameraPos();
            if (current != null && current.equals(animatable.getPos())) {
                return;
            }
        }

        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, CameraBlockEntity animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {

        if ("Camera".equals(bone.getName())
                && animatable.getWorld() != null
                && !(animatable.getWorld() instanceof ClientWorld)) {

            float angleRad = (float) (Math.sin(System.currentTimeMillis() / 955.0) * Math.toRadians(30.0));
            bone.setRotY(angleRad);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}

