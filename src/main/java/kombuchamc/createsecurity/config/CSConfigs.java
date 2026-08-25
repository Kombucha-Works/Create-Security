package kombuchamc.createsecurity.config;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import kombuchamc.createsecurity.CreateSecurity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public class CSConfigs {

    public static final Settings CONFIG;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CONFIG = new Settings(builder);
        SPEC = builder.build();
    }

    public static void register() {
        ForgeConfigRegistry.INSTANCE.register(CreateSecurity.MOD_ID, ModConfig.Type.COMMON, SPEC);
    }

    public static int cameraRenderDistance() {
        return SPEC.isLoaded()
                ? CONFIG.cameraRenderDistance.get()
                : CONFIG.cameraRenderDistance.getDefault();
    }

    public static int cameraResolution() {
        return SPEC.isLoaded()
                ? CONFIG.cameraResolution.get()
                : CONFIG.cameraResolution.getDefault();
    }

    public static int nameTagResolution() {
        return SPEC.isLoaded()
                ? CONFIG.nameTagResolution.get()
                : CONFIG.nameTagResolution.getDefault();
    }

    public static int cameraSimulationDistance() {
        return SPEC.isLoaded()
                ? CONFIG.cameraSimulationDistance.get()
                : CONFIG.cameraSimulationDistance.getDefault();
    }

    public static int keycardReaderMarkerDistance() {
        return SPEC.isLoaded()
                ? CONFIG.keycardReaderMarkerDistance.get()
                : CONFIG.keycardReaderMarkerDistance.getDefault();
    }

    public static boolean operatorsCanAccessUnownedCameras() {
        return SPEC.isLoaded()
                ? CONFIG.operatorsCanAccessUnownedCameras.get()
                : CONFIG.operatorsCanAccessUnownedCameras.getDefault();
    }

    public static boolean canBypassOwner(PlayerEntity player) {
        return player.hasPermissionLevel(2) && operatorsCanAccessUnownedCameras();
    }

    public static class Settings {
        public final ForgeConfigSpec.IntValue cameraRenderDistance;
        public final ForgeConfigSpec.IntValue cameraResolution;
        public final ForgeConfigSpec.IntValue nameTagResolution;
        public final ForgeConfigSpec.IntValue cameraSimulationDistance;
        public final ForgeConfigSpec.IntValue keycardReaderMarkerDistance;
        public final ForgeConfigSpec.BooleanValue operatorsCanAccessUnownedCameras;

        Settings(ForgeConfigSpec.Builder builder) {
            cameraRenderDistance = builder
                    .comment("How far a camera renders the world in its point of view, in chunks.",
                            "Your own render distance is a hard ceiling: the game only builds terrain",
                            "around the player, so setting this higher than it changes nothing.",
                            "Lower values cost less performance per visible feed.",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("cameraRenderDistance", 12, 2, 32);
            cameraResolution = builder
                    .comment("Width in pixels each camera feed is rendered at; the height follows",
                            "the 3:2 screen shape, so 640 renders a 640x426 image per camera.",
                            "Higher looks sharper on large screens and costs more GPU time and memory",
                            "per visible camera. Applies as soon as the value is changed.",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("cameraResolution", 640, 64, 2048);
            nameTagResolution = builder
                    .comment("Width in pixels of the separate layer player name tags are drawn on",
                            "inside a camera feed, so names stay sharp when the feed is stretched",
                            "across a wall or viewed fullscreen. Name tags keep their normal size;",
                            "only their sharpness changes. Values below the camera resolution have",
                            "no benefit. Costs memory per visible camera (roughly width x width x 8 bytes).",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("nameTagResolution", 1280, 64, 4096);
            cameraSimulationDistance = builder
                    .comment("Radius in chunks kept loaded and ticking around every placed camera,",
                            "so cameras keep working while no player is nearby.",
                            "0 keeps only the camera's own chunk loaded. Each camera costs server performance.",
                            "Read on the server, so a server's own value applies to everyone.")
                    .defineInRange("cameraSimulationDistance", 2, 0, 8);
            keycardReaderMarkerDistance = builder
                    .comment("How far away, in blocks, the green mounting markers show up while you",
                            "hold a Keycard Reader. Higher values scan a larger area around you",
                            "a few times a second, so very high values cost more client performance.",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("keycardReaderMarkerDistance", 8, 1, 32);
            operatorsCanAccessUnownedCameras = builder
                    .comment("Whether operators (permission level 2) may open, break and bind cameras",
                            "placed by someone else. Also applies to Camera Links and Monitors.",
                            "Read on the server, so a server's own value applies to everyone.")
                    .define("operatorsCanAccessUnownedCameras", true);
        }
    }
}
