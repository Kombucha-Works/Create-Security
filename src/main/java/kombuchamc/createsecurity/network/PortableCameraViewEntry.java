package kombuchamc.createsecurity.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public record PortableCameraViewEntry(BlockPos pos, @Nullable Direction lensDir,
                                      boolean fisheye, @Nullable String label) {
}

