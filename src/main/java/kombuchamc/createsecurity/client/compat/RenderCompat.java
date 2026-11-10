package kombuchamc.createsecurity.client.compat;

import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class RenderCompat {

    public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    public static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    private static FeedShaderHook shaderHook;
    private static boolean shaderHookBroken;

    private RenderCompat() {}

    public static void init() {
        if (IRIS) {
            try {
                shaderHook = (FeedShaderHook) Class
                        .forName("kombuchamc.createsecurity.client.compat.IrisFeedShaderHook")
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable t) {
                CreateSecurity.LOGGER.warn("Iris is installed but the camera feed shader hook could not"
                        + " be installed; feeds may render incorrectly while a shader pack is active", t);
            }
        }
        CreateSecurity.LOGGER.info("Camera feed compatibility: sodium={}, iris={}, shaderHook={}",
                SODIUM, IRIS, shaderHook != null);
    }

    public static boolean shaderPackInUse() {
        if (shaderHook == null || shaderHookBroken) return false;
        try {
            return shaderHook.shaderPackInUse();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String describeShaderState() {
        if (shaderHook == null) return "noShaderHook";
        try {
            return shaderHook.describe();
        } catch (Throwable t) {
            return "describeFailed=" + t;
        }
    }

    public static boolean shadedFeedsActive() {
        return kombuchamc.createsecurity.config.CSConfigs.shadedCameraFeeds() && shaderPackInUse();
    }

    public static boolean canRenderInRangeFeed() {
        if (shaderHook == null || shaderHookBroken) return true;
        if (shadedFeedsActive()) return true;
        try {
            return shaderHook.canSwap();
        } catch (Throwable t) {
            return true;
        }
    }

    public static void beginFeedRender(boolean remote) {
        if (shaderHook == null || shaderHookBroken) return;
        if (!remote && shadedFeedsActive()) return;
        try {
            shaderHook.begin();
        } catch (Throwable t) {
            shaderHookBroken = true;
            CreateSecurity.LOGGER.error("Disabling the camera feed shader hook after an error", t);
        }
    }

    public static void endFeedRender() {
        if (shaderHook == null) return;
        try {
            shaderHook.end();
        } catch (Throwable t) {
            shaderHookBroken = true;
            CreateSecurity.LOGGER.error("Failed to restore the shader pipeline after a camera feed", t);
        }
    }
}
