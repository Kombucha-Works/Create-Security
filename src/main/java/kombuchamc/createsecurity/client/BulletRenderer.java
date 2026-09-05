package kombuchamc.createsecurity.client;

import kombuchamc.createsecurity.entity.BulletEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class BulletRenderer extends EntityRenderer<BulletEntity> {

    private static final float SCALE = 0.55f;

    public BulletRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(BulletEntity bullet) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }

    @Override
    public void render(BulletEntity bullet, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                MathHelper.lerp(tickDelta, bullet.prevYaw, bullet.getYaw()) - 90f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                MathHelper.lerp(tickDelta, bullet.prevPitch, bullet.getPitch())));
        matrices.scale(SCALE, SCALE, SCALE);
        matrices.translate(-0.5, -0.5, -0.5);

        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                bullet.ammo().projectileBlock().getDefaultState(), matrices, vertexConsumers,
                light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
        super.render(bullet, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
