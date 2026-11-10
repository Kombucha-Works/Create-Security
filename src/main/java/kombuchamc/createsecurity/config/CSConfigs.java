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

    public static boolean shadedCameraFeeds() {
        return SPEC.isLoaded()
                ? CONFIG.shadedCameraFeeds.get()
                : CONFIG.shadedCameraFeeds.getDefault();
    }

    public static int shadedFeedIntervalMs() {
        return SPEC.isLoaded()
                ? CONFIG.shadedFeedIntervalMs.get()
                : CONFIG.shadedFeedIntervalMs.getDefault();
    }

    public static int cameraSimulationDistance() {
        return SPEC.isLoaded()
                ? CONFIG.cameraSimulationDistance.get()
                : CONFIG.cameraSimulationDistance.getDefault();
    }

    public static int cameraFeedRadius() {
        return SPEC.isLoaded()
                ? CONFIG.cameraFeedRadius.get()
                : CONFIG.cameraFeedRadius.getDefault();
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
        public final ForgeConfigSpec.BooleanValue shadedCameraFeeds;
        public final ForgeConfigSpec.IntValue shadedFeedIntervalMs;
        public final ForgeConfigSpec.IntValue cameraSimulationDistance;
        public final ForgeConfigSpec.IntValue cameraFeedRadius;
        public final ForgeConfigSpec.IntValue keycardReaderMarkerDistance;
        public final ForgeConfigSpec.BooleanValue operatorsCanAccessUnownedCameras;

        Settings(ForgeConfigSpec.Builder builder) {
            cameraRenderDistance = builder
                    .comment("How far a camera renders the world in its point of view, in chunks.",
                            "0 means no limit of our own: the feed reaches as far as you can see.",
                            "Your own render distance is a hard ceiling either way, because the game",
                            "only builds terrain around the player, so a camera can never show you",
                            "anything your own client has not been sent.",
                            "Lower values cost less performance per visible feed.",
                            "Ignored while Sodium is installed, which draws the feed with its own",
                            "terrain renderer at your full render distance.",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("cameraRenderDistance", 0, 0, 32);
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
            shadedCameraFeeds = builder
                    .comment("Whether a camera feed is drawn with your shader pack instead of plain",
                            "vanilla lighting. Only applies to cameras close enough for your own client",
                            "to have the terrain; a camera beyond your render distance always draws",
                            "unshaded, because its feed is built from streamed chunks the shader",
                            "pipeline knows nothing about.",
                            "A shader pack can only render one point of view per frame, so a frame that",
                            "refreshes a feed shows your own view from the previous frame instead.",
                            "The feed also renders at your window size rather than cameraResolution,",
                            "so this costs roughly a whole extra frame every time a feed refreshes.",
                            "On by default. Turn OFF your pack's Temporal Filtering (TAA) and Motion",
                            "Blur: both blend each frame with the one before it and cannot tell the two",
                            "points of view apart, so TAA makes terrain flicker and Motion Blur makes",
                            "your view wobble. A chat message says so whenever a pack is loaded with",
                            "this on. Raising shadedFeedIntervalMs makes those artifacts rarer if you",
                            "want to keep the effects on; setting this to false avoids them entirely",
                            "and draws panels with vanilla lighting instead.",
                            "Has no effect without Iris and a shader pack loaded.",
                            "Read on the client, so every player uses their own value.")
                    .define("shadedCameraFeeds", true);
            shadedFeedIntervalMs = builder
                    .comment("How often a shaded camera feed is allowed to refresh, in milliseconds.",
                            "A shader pack cannot render two points of view in one frame, so a frame",
                            "that refreshes a feed shows your own view from the previous frame instead.",
                            "Lower values refresh panels more often and repeat your own view more often,",
                            "which reads as a slight judder while you turn; higher values are smoother",
                            "for you and update the panels less. Standing still costs nothing either way,",
                            "because the repeated frame is identical to the one before it.",
                            "Only applies while shadedCameraFeeds is on and a pack is loaded.",
                            "Read on the client, so every player uses their own value.")
                    .defineInRange("shadedFeedIntervalMs", 250, 33, 2000);
            cameraSimulationDistance = builder
                    .comment("Radius in chunks kept loaded and ticking around every placed camera,",
                            "so cameras keep working while no player is nearby.",
                            "0 keeps only the camera's own chunk loaded. Each camera costs server performance.",
                            "Read on the server, so a server's own value applies to everyone.")
                    .defineInRange("cameraSimulationDistance", 2, 0, 8);
            cameraFeedRadius = builder
                    .comment("Radius in chunks the server streams to a player around a camera that is",
                            "too far away for their own client to have the terrain already.",
                            "This is what lets a feed work beyond your render distance; 0 turns it off",
                            "and a distant camera shows OUT OF RANGE instead.",
                            "Each watching player holds that area loaded for as long as they watch,",
                            "and the chunks are sent once, so large values cost bandwidth and server memory.",
                            "Read on the server, so a server's own value applies to everyone.")
                    .defineInRange("cameraFeedRadius", 6,
                            0, kombuchamc.createsecurity.block.camera.RemoteChunkFeed.MAX_RADIUS);
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
