package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import kombuchamc.createsecurity.block.entity.CameraLinkBlockEntity;
import kombuchamc.createsecurity.block.entity.MonitorBlockEntity;
import kombuchamc.createsecurity.config.CSConfigs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

public final class BlockOwnership {
    private BlockOwnership() {}

    @Nullable
    public static String deniedMessageKey(@Nullable BlockEntity be, PlayerEntity player) {
        if (CSConfigs.canBypassOwner(player)) return null;
        if (be instanceof CameraBlockEntity camera) {
            return camera.isOwner(player) ? null : "message.create-security.not_camera_owner";
        }
        if (be instanceof CameraLinkBlockEntity link) {
            return link.isOwner(player) ? null : "message.create-security.not_camera_link_owner";
        }
        if (be instanceof MonitorBlockEntity monitor) {
            return monitor.isOwner(player) ? null : "message.create-security.not_monitor_owner";
        }
        return null;
    }

    public static boolean checkAndWarn(@Nullable BlockEntity be, PlayerEntity player) {
        String key = deniedMessageKey(be, player);
        if (key == null) return true;
        player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
        return false;
    }
}
