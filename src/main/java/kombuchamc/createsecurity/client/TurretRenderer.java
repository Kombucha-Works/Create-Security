package kombuchamc.createsecurity.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.entity.TurretBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {

    private static final float EXTEND_PHASE = 0.62f;
    private static final float LOWER_END = 0.5f;
    private static final float UPPER_START = 0.35f;

    private static final double LOWER_TRAVEL = 6 / 16.0;
    private static final double UPPER_TRAVEL = 25 / 16.0;
    private static final double PIVOT_Y = 26 / 16.0;
    private static final float STOWED_PITCH = -90f;

    public TurretRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(TurretBlockEntity turret, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(
                KineticBlockEntityRenderer.getAngleForBe(turret, turret.getPos(), Direction.Axis.Y)));
        matrices.translate(-0.5, 0, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                RegisterModBlocks.TURRET_SHAFT.getDefaultState(), matrices, vertexConsumers, light,
                OverlayTexture.DEFAULT_UV);
        matrices.pop();

        float deploy = turret.deployProgress(tickDelta);
        if (deploy <= 0.001f) return;

        float extend = MathHelper.clamp(deploy / EXTEND_PHASE, 0f, 1f);
        float pitch = MathHelper.clamp((deploy - EXTEND_PHASE) / (1f - EXTEND_PHASE), 0f, 1f);
        float lower = phase(extend, 0f, LOWER_END);
        float upper = phase(extend, UPPER_START, 1f);

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(turret.renderYaw(tickDelta)));
        matrices.translate(-0.5, 0, -0.5);

        part(RegisterModBlocks.TURRET_MAST_LOWER.getDefaultState(), matrices, vertexConsumers, light,
                -(1f - lower) * LOWER_TRAVEL, 0f, 0f);
        part(RegisterModBlocks.TURRET_CRADLE.getDefaultState(), matrices, vertexConsumers, light,
                -(1f - upper) * UPPER_TRAVEL, 0f, 0f);

        double gunRise = -(1f - upper) * UPPER_TRAVEL;
        float gunPitch = STOWED_PITCH * (1f - pitch) + turret.renderTilt(tickDelta) * pitch;
        net.minecraft.block.Block gun = turret.barrel().gunBlock();
        if (gun != null) {
            part(gun.getDefaultState(), matrices, vertexConsumers, light, gunRise, gunPitch, 0f);
        }
        net.minecraft.block.Block rotor = turret.barrel().rotorBlock();
        if (rotor != null) {
            part(rotor.getDefaultState(), matrices, vertexConsumers, light, gunRise, gunPitch,
                    turret.renderSpin(tickDelta));
        }

        matrices.pop();
    }

    private void part(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                      int light, double rise, float pitchDegrees, float spinDegrees) {
        matrices.push();
        matrices.translate(0, rise, 0);
        if (pitchDegrees != 0f) {
            matrices.translate(0.5, PIVOT_Y, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pitchDegrees));
            matrices.translate(-0.5, -PIVOT_Y, -0.5);
        }
        if (spinDegrees != 0f) {
            matrices.translate(0.5, PIVOT_Y, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spinDegrees));
            matrices.translate(-0.5, -PIVOT_Y, -0.5);
        }
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    private static float phase(float progress, float start, float end) {
        return MathHelper.clamp((progress - start) / (end - start), 0f, 1f);
    }

    @Override
    public boolean rendersOutsideBoundingBox(TurretBlockEntity turret) {
        return true;
    }
}
