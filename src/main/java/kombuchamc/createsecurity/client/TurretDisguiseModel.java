package kombuchamc.createsecurity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class TurretDisguiseModel extends ForwardingBakedModel {

    private static final float SOCKET_MAX_Y = 6 / 16f;

    private static RenderMaterial emissive;

    private static RenderMaterial emissive() {
        if (emissive == null && RendererAccess.INSTANCE.hasRenderer()) {
            emissive = RendererAccess.INSTANCE.getRenderer().materialFinder().emissive(true).find();
        }
        return emissive;
    }

    public TurretDisguiseModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        BlockState material = materialAt(world, pos);
        if (material == null) {
            super.emitBlockQuads(world, state, pos, randomSupplier, context);
            return;
        }

        BakedModel disguise = MinecraftClient.getInstance().getBlockRenderManager().getModel(material);

        RenderMaterial glow = material.hasEmissiveLighting(world, pos) ? emissive() : null;
        context.pushTransform(quad -> {
            if (quad.nominalFace() == Direction.DOWN) return false;
            if (glow != null) quad.material(glow);
            return true;
        });
        disguise.emitBlockQuads(world, material, pos, randomSupplier, context);
        context.popTransform();

        context.pushTransform(quad -> quad.nominalFace() == Direction.DOWN || socketDetail(quad));
        super.emitBlockQuads(world, state, pos, randomSupplier, context);
        context.popTransform();
    }

    private static boolean socketDetail(QuadView quad) {
        for (int i = 0; i < 4; i++) {
            if (quad.y(i) > SOCKET_MAX_Y) return false;
        }
        return true;
    }

    public static int tint(BlockState state, BlockRenderView world, BlockPos pos, int tintIndex) {
        BlockState material = materialAt(world, pos);
        if (material == null || material.isOf(state.getBlock())) return 0xFFFFFF;
        return MinecraftClient.getInstance().getBlockColors().getColor(material, world, pos, tintIndex);
    }

    private static BlockState materialAt(BlockRenderView world, BlockPos pos) {
        if (!(world instanceof RenderAttachedBlockView attached)) return null;
        Object data = attached.getBlockEntityRenderAttachment(pos);
        return data instanceof BlockState material && !material.isAir() ? material : null;
    }
}
