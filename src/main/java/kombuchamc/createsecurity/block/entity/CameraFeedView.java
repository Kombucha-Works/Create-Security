package kombuchamc.createsecurity.block.entity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public interface CameraFeedView {

    @Nullable
    BlockPos getLinkedCameraPos();

    @Nullable
    Direction getLinkedCameraLensDir();

    boolean isLinkedCameraFisheye();

    long getLastSeenNanos();

    BlockPos getFeedPos();
}
