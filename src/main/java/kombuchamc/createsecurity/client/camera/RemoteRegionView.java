package kombuchamc.createsecurity.client.camera;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class RemoteRegionView implements BlockRenderView {

    private final RemoteChunkStore.Region region;

    RemoteRegionView(RemoteChunkStore.Region region) {
        this.region = region;
    }

    @Nullable
    private WorldChunk chunkAt(BlockPos pos) {
        return region.chunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        WorldChunk chunk = chunkAt(pos);
        return chunk == null ? Blocks.AIR.getDefaultState() : chunk.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        WorldChunk chunk = chunkAt(pos);
        return chunk == null ? Fluids.EMPTY.getDefaultState() : chunk.getFluidState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return region.world().getBrightness(direction, shaded);
    }

    @Override
    public LightingProvider getLightingProvider() {
        return region.world().getLightingProvider();
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver resolver) {
        WorldChunk chunk = chunkAt(pos);
        Biome biome = chunk == null
                ? region.world().getBiome(pos).value()
                : chunk.getBiomeForNoiseGen(BiomeCoords.fromBlock(pos.getX()),
                        BiomeCoords.fromBlock(pos.getY()), BiomeCoords.fromBlock(pos.getZ())).value();
        return resolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos) {
        return type == LightType.SKY ? region.skyLight(pos) : region.blockLight(pos);
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness) {
        return Math.max(region.blockLight(pos), region.skyLight(pos) - ambientDarkness);
    }

    @Override
    public boolean isSkyVisible(BlockPos pos) {
        return region.skyLight(pos) >= getMaxLightLevel();
    }

    @Override
    public int getHeight() {
        return region.world().getHeight();
    }

    @Override
    public int getBottomY() {
        return region.world().getBottomY();
    }
}
