package kombuchamc.createsecurity.block.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.entity.CameraDisplayBlockEntity;
import kombuchamc.createsecurity.mixin.CameraAccessor;
import kombuchamc.createsecurity.mixin.GameRendererAccessor;
import kombuchamc.createsecurity.mixin.MinecraftClientAccessor;
import kombuchamc.createsecurity.mixin.WorldRendererAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CameraViewManager {

    private static final Map<BlockPos, SimpleFramebuffer> framebuffers = new HashMap<>();

    private static final Map<BlockPos, SimpleFramebuffer> nameFramebuffers = new HashMap<>();

    private static final double NAME_RANGE_SQ = 64.0 * 64.0;
    private static boolean isPreRendering = false;
    private static boolean insideInnerRender = false;
    private static BlockPos currentCameraPos = null;

    public static final float NORMAL_FOV = 70f;
    public static final float FISHEYE_FOV = 150f;

    private static final Map<BlockPos, Float> currentFovByCamera = new HashMap<>();
    private static float currentFov = NORMAL_FOV;

    private static final long SEEN_WINDOW_NANOS = 250_000_000L;

    private static final int MAX_CAMERA_RENDERS_PER_FRAME = 2;

    private static final long NEAR_INTERVAL_NANOS = 33_000_000L;
    private static final long MID_INTERVAL_NANOS  = 66_000_000L;
    private static final long FAR_INTERVAL_NANOS  = 125_000_000L;
    private static final double NEAR_DIST_SQ = 32.0 * 32.0;
    private static final double MID_DIST_SQ  = 64.0 * 64.0;


    private static final Map<BlockPos, Long> lastRenderNanos = new HashMap<>();

    private static final Set<BlockPos> renderErrorLogged = new HashSet<>();

    private static final long SWEEP_INTERVAL_NANOS = 5_000_000_000L;
    private static long lastSweepNanos = 0L;

    private static BlockPos portableCameraPos = null;
    private static Direction portableLensDir = null;
    private static boolean portableFisheye = false;

    public static void setPortableView(BlockPos cameraPos, Direction lensDir, boolean fisheye) {
        portableCameraPos = cameraPos;
        portableLensDir = lensDir;
        portableFisheye = fisheye;
    }

    public static void clearPortableView() {
        portableCameraPos = null;
        portableLensDir = null;
        portableFisheye = false;
    }

    public static float getCurrentFov() {
        return currentFov;
    }

    public static float getCurrentAspect() {
        return 1.5f;
    }

    public static boolean isPreRendering() {
        return isPreRendering;
    }

    public static boolean isInsideInnerRender() {
        return insideInnerRender;
    }

    public static BlockPos getCurrentCameraPos() {
        return currentCameraPos;
    }

    public static SimpleFramebuffer getFramebuffer(BlockPos cameraPos) {
        return framebuffers.get(cameraPos);
    }

    public static int getNameOverlayTexture(BlockPos cameraPos) {
        SimpleFramebuffer fb = nameFramebuffers.get(cameraPos);
        return fb == null ? 0 : fb.getColorAttachment();
    }

    public static boolean isCameraInClientRange(BlockPos cameraPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.worldRenderer == null) return false;
        int viewDistance = ((WorldRendererAccessor) client.worldRenderer).getViewDistanceField();
        int chunkDistance = Math.max(
                Math.abs((cameraPos.getX() >> 4) - (client.player.getBlockPos().getX() >> 4)),
                Math.abs((cameraPos.getZ() >> 4) - (client.player.getBlockPos().getZ() >> 4)));
        return chunkDistance <= Math.max(0, viewDistance - 1);
    }

    public static void preRenderAllCameras(MinecraftClient client, float tickDelta, long limitTime) {
        if (isPreRendering || client.world == null || client.player == null) return;

        if (client.isPaused()) return;

        long now = System.nanoTime();

        Map<BlockPos, kombuchamc.createsecurity.block.entity.CameraFeedView> sampleDisplayPerCamera = new HashMap<>();
        Map<BlockPos, Double> nearestDistSqPerCamera = new HashMap<>();
        Set<BlockPos> allLinkedCameras = new HashSet<>();
        if (portableCameraPos != null) allLinkedCameras.add(portableCameraPos);
        Vec3d playerPos = client.player.getPos();
        List<kombuchamc.createsecurity.block.entity.CameraFeedView> feedViews =
                new ArrayList<>(CameraDisplayBlockEntity.getClientInstances());
        feedViews.addAll(kombuchamc.createsecurity.block.entity.MonitorBlockEntity.getClientInstances());
        for (kombuchamc.createsecurity.block.entity.CameraFeedView be : feedViews) {
            BlockPos camPos = be.getLinkedCameraPos();
            if (camPos == null) continue;
            allLinkedCameras.add(camPos);
            if (now - be.getLastSeenNanos() > SEEN_WINDOW_NANOS) continue;
            double distSq = playerPos.squaredDistanceTo(Vec3d.ofCenter(be.getFeedPos()));
            Double prev = nearestDistSqPerCamera.get(camPos);
            if (prev == null || distSq < prev) {
                nearestDistSqPerCamera.put(camPos, distSq);
                sampleDisplayPerCamera.put(camPos, be);
            }
        }

        if (now - lastSweepNanos > SWEEP_INTERVAL_NANOS) {
            lastSweepNanos = now;
            framebuffers.entrySet().removeIf(e -> {
                if (allLinkedCameras.contains(e.getKey())) return false;
                e.getValue().delete();
                return true;
            });
            nameFramebuffers.entrySet().removeIf(e -> {
                if (allLinkedCameras.contains(e.getKey())) return false;
                e.getValue().delete();
                return true;
            });
            currentFovByCamera.keySet().retainAll(allLinkedCameras);
            lastRenderNanos.keySet().retainAll(allLinkedCameras);
            renderErrorLogged.retainAll(allLinkedCameras);
        }

        List<BlockPos> due = new ArrayList<>();
        for (BlockPos camPos : sampleDisplayPerCamera.keySet()) {
            long last = lastRenderNanos.getOrDefault(camPos, 0L);
            if (now - last >= refreshIntervalNanos(nearestDistSqPerCamera.get(camPos))) {
                due.add(camPos);
            }
        }
        boolean portableDue = portableCameraPos != null
                && now - lastRenderNanos.getOrDefault(portableCameraPos, 0L) >= NEAR_INTERVAL_NANOS;
        if (due.isEmpty() && !portableDue) return;
        due.sort((a, b) -> Long.compare(
                lastRenderNanos.getOrDefault(a, 0L),
                lastRenderNanos.getOrDefault(b, 0L)));

        isPreRendering = true;
        try {
            int rendered = 0;

            if (portableDue) {
                Direction lensDir = portableLensDir;
                if (lensDir == null) {
                    BlockState camState = client.world.getBlockState(portableCameraPos);
                    if (camState.isOf(RegisterModBlocks.CAMERA_BLOCK)) {
                        lensDir = camState.get(CameraBlock.FACING).getOpposite();
                    }
                }
                if (lensDir != null && isCameraInClientRange(portableCameraPos)) {
                    renderWithFovLerp(client, portableCameraPos, lensDir, portableFisheye,
                            now, tickDelta, limitTime);
                    rendered++;
                }
            }
            for (BlockPos camPos : due) {
                if (rendered >= MAX_CAMERA_RENDERS_PER_FRAME) break;

                if (camPos.equals(portableCameraPos)) continue;
                if (!isCameraInClientRange(camPos)) continue;
                kombuchamc.createsecurity.block.entity.CameraFeedView be = sampleDisplayPerCamera.get(camPos);

                Direction lensDir = be.getLinkedCameraLensDir();
                if (lensDir == null) {
                    BlockState camState = client.world.getBlockState(camPos);
                    if (!camState.isOf(RegisterModBlocks.CAMERA_BLOCK)) continue;
                    lensDir = camState.get(CameraBlock.FACING).getOpposite();
                }

                rendered++;
                renderWithFovLerp(client, camPos, lensDir, be.isLinkedCameraFisheye(),
                        now, tickDelta, limitTime);
            }
        } finally {
            isPreRendering = false;

            client.getFramebuffer().beginWrite(true);
        }
    }

    private static void renderWithFovLerp(MinecraftClient client, BlockPos camPos, Direction lensDir,
                                          boolean fisheye, long now, float tickDelta, long limitTime) {
        long last = lastRenderNanos.getOrDefault(camPos, 0L);
        float elapsedFrames = last == 0L ? 1f
                : Math.min(10f, (now - last) / 16_666_666f);
        float step = 1f - (float) Math.pow(0.85, elapsedFrames);
        float targetFov = fisheye ? FISHEYE_FOV : NORMAL_FOV;
        float lerped = currentFovByCamera.getOrDefault(camPos, targetFov);
        lerped += (targetFov - lerped) * step;
        if (Math.abs(targetFov - lerped) < 0.05f) lerped = targetFov;
        currentFovByCamera.put(camPos, lerped);
        currentFov = lerped;

        lastRenderNanos.put(camPos, now);
        renderCameraView(client, camPos, lensDir, tickDelta, limitTime);
        currentFov = NORMAL_FOV;
    }

    private static long refreshIntervalNanos(double nearestDistSq) {
        if (nearestDistSq <= NEAR_DIST_SQ) return NEAR_INTERVAL_NANOS;
        if (nearestDistSq <= MID_DIST_SQ) return MID_INTERVAL_NANOS;
        return FAR_INTERVAL_NANOS;
    }

    private static void renderCameraView(MinecraftClient client, BlockPos cameraPos, Direction lensDir,
                                         float tickDelta, long limitTime) {
        if (client.world == null || client.player == null) return;

        int fbWidth = Math.max(1, kombuchamc.createsecurity.config.CSConfigs.cameraResolution());
        int fbHeight = Math.max(1, fbWidth * 2 / 3);

        SimpleFramebuffer fb = framebuffers.get(cameraPos);
        if (fb != null && (fb.textureWidth != fbWidth || fb.textureHeight != fbHeight)) {
            fb.delete();
            framebuffers.remove(cameraPos);
            fb = null;
        }
        if (fb == null) {
            fb = new SimpleFramebuffer(fbWidth, fbHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            framebuffers.put(cameraPos, fb);
        }

        int nameWidth = Math.max(1, kombuchamc.createsecurity.config.CSConfigs.nameTagResolution());
        int nameHeight = Math.max(1, nameWidth * 2 / 3);
        SimpleFramebuffer nameFb = nameFramebuffers.get(cameraPos);
        if (nameFb != null
                && (nameFb.textureWidth != nameWidth || nameFb.textureHeight != nameHeight)) {
            nameFb.delete();
            nameFramebuffers.remove(cameraPos);
            nameFb = null;
        }
        if (nameFb == null) {
            nameFb = new SimpleFramebuffer(nameWidth, nameHeight, false, MinecraftClient.IS_SYSTEM_MAC);
            nameFramebuffers.put(cameraPos, nameFb);
        }

        Camera ourCamera = new Camera();
        ourCamera.update(client.world, client.player, true, false, tickDelta);

        Vec3d center = Vec3d.ofCenter(cameraPos);
        CameraAccessor camAcc = (CameraAccessor) ourCamera;
        camAcc.invokeSetPos(center.x, center.y, center.z);
        camAcc.invokeSetRotation(facingToYaw(lensDir), facingToPitch(lensDir));

        MinecraftClientAccessor mcAcc = (MinecraftClientAccessor) client;
        GameRendererAccessor grAcc = (GameRendererAccessor) client.gameRenderer;
        WorldRendererAccessor wrAcc = (WorldRendererAccessor) client.worldRenderer;

        Framebuffer savedFramebuffer = mcAcc.getFramebufferField();
        Camera savedCamera = grAcc.getCameraField();
        Frustum savedFrustum = wrAcc.getFrustumField();
        Matrix4f savedProj = RenderSystem.getProjectionMatrix();
        VertexSorter savedSorter = RenderSystem.getVertexSorting();
        Matrix3f savedInvRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        double savedSortX = wrAcc.getLastTranslucentSortX();
        double savedSortY = wrAcc.getLastTranslucentSortY();
        double savedSortZ = wrAcc.getLastTranslucentSortZ();

        PostEffectProcessor savedTransparencyProc = wrAcc.getTransparencyPostProcessor();
        Framebuffer savedTranslucentFb = wrAcc.getTranslucentFramebufferField();
        Framebuffer savedParticlesFb = wrAcc.getParticlesFramebufferField();
        Framebuffer savedWeatherFb = wrAcc.getWeatherFramebufferField();
        Framebuffer savedCloudsFb = wrAcc.getCloudsFramebufferField();
        Framebuffer savedEntityFb = wrAcc.getEntityFramebufferField();
        Framebuffer savedEntityOutlinesFb = wrAcc.getEntityOutlinesFramebufferField();

        wrAcc.setTransparencyPostProcessor(null);
        wrAcc.setTranslucentFramebufferField(null);
        wrAcc.setParticlesFramebufferField(null);
        wrAcc.setWeatherFramebufferField(null);
        wrAcc.setCloudsFramebufferField(null);
        wrAcc.setEntityFramebufferField(null);
        wrAcc.setEntityOutlinesFramebufferField(null);

        mcAcc.setFramebufferField(fb);
        grAcc.setCameraField(ourCamera);
        currentCameraPos = cameraPos;

        fb.setClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        fb.clear(MinecraftClient.IS_SYSTEM_MAC);
        fb.beginWrite(true);

        RenderSystem.viewport(0, 0, fbWidth, fbHeight);

        insideInnerRender = true;
        try {

            client.gameRenderer.renderWorld(tickDelta, 0L, new MatrixStack());

            fb.beginWrite(true);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0f, 0f, 0f, 1f);
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.colorMask(true, true, true, true);

            drawFlaggedPlayerOutlines(client, cameraPos, lensDir, tickDelta);

            drawFeedNameTags(client, cameraPos, lensDir, tickDelta);
        } catch (Throwable t) {

            if (renderErrorLogged.add(cameraPos.toImmutable())) {
                kombuchamc.createsecurity.CreateSecurity.LOGGER.error(
                        "Failed to render camera feed at {}", cameraPos, t);
            }
        } finally {
            insideInnerRender = false;
            mcAcc.setFramebufferField(savedFramebuffer);
            grAcc.setCameraField(savedCamera);
            wrAcc.setFrustumField(savedFrustum);
            RenderSystem.setProjectionMatrix(savedProj, savedSorter);
            RenderSystem.setInverseViewRotationMatrix(savedInvRot);
            wrAcc.setLastTranslucentSortX(savedSortX);
            wrAcc.setLastTranslucentSortY(savedSortY);
            wrAcc.setLastTranslucentSortZ(savedSortZ);

            wrAcc.getUpdateFinished().set(true);

            wrAcc.setTransparencyPostProcessor(savedTransparencyProc);
            wrAcc.setTranslucentFramebufferField(savedTranslucentFb);
            wrAcc.setParticlesFramebufferField(savedParticlesFb);
            wrAcc.setWeatherFramebufferField(savedWeatherFb);
            wrAcc.setCloudsFramebufferField(savedCloudsFb);
            wrAcc.setEntityFramebufferField(savedEntityFb);
            wrAcc.setEntityOutlinesFramebufferField(savedEntityOutlinesFb);
            currentCameraPos = null;
        }
    }

    private static void drawFeedNameTags(MinecraftClient client, BlockPos cameraPos,
                                         Direction lensDir, float tickDelta) {
        SimpleFramebuffer nameFb = nameFramebuffers.get(cameraPos);
        if (nameFb == null || client.world == null) return;

        nameFb.setClearColor(0f, 0f, 0f, 0f);
        nameFb.clear(MinecraftClient.IS_SYSTEM_MAC);
        nameFb.beginWrite(true);
        RenderSystem.viewport(0, 0, nameFb.textureWidth, nameFb.textureHeight);

        float yaw = facingToYaw(lensDir);
        float pitch = facingToPitch(lensDir);
        Vec3d camCenter = Vec3d.ofCenter(cameraPos);
        Vec3d lensOrigin = camCenter.add(Vec3d.of(lensDir.getVector()).multiply(0.5));

        MatrixStack viewStack = new MatrixStack();
        viewStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        viewStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw + 180f));

        org.joml.Quaternionf billboard = new org.joml.Quaternionf().rotationYXZ(
                -yaw * (float) (Math.PI / 180.0), pitch * (float) (Math.PI / 180.0), 0f);

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.push();
        mvStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            net.minecraft.client.render.VertexConsumerProvider.Immediate immediate =
                    client.getBufferBuilders().getEntityVertexConsumers();
            net.minecraft.client.font.TextRenderer tr = client.textRenderer;
            int background = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255f) << 24;

            for (net.minecraft.client.network.AbstractClientPlayerEntity player
                    : client.world.getPlayers()) {
                Vec3d pos = player.getLerpedPos(tickDelta);
                double dx = pos.x - camCenter.x;
                double dy = pos.y - camCenter.y;
                double dz = pos.z - camCenter.z;
                if (dx * dx + dy * dy + dz * dz > NAME_RANGE_SQ) continue;
                if (!nameTagVisible(client, lensOrigin, player)) continue;

                net.minecraft.text.Text name = player.getDisplayName();
                viewStack.push();
                viewStack.translate(dx, dy + player.getHeight() + 0.5, dz);
                viewStack.multiply(billboard);
                viewStack.scale(-0.025f, -0.025f, 0.025f);
                Matrix4f mat = viewStack.peek().getPositionMatrix();
                float x = -tr.getWidth(name) / 2f;
                tr.draw(name, x, 0f, 0x20FFFFFF, false, mat, immediate,
                        net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH,
                        background, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
                tr.draw(name, x, 0f, -1, false, mat, immediate,
                        net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                        0, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
                viewStack.pop();
            }
            immediate.draw();
        } catch (Throwable t) {
            if (!nameTagErrorLogged) {
                nameTagErrorLogged = true;
                kombuchamc.createsecurity.CreateSecurity.LOGGER.error(
                        "Failed to render camera feed name tags", t);
            }
        } finally {
            RenderSystem.disableBlend();
            mvStack.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static boolean nameTagErrorLogged = false;

    private static boolean nameTagVisible(MinecraftClient client, Vec3d from,
                                          net.minecraft.entity.player.PlayerEntity player) {
        if (client.world == null) return false;
        Vec3d[] targets = {player.getEyePos(),
                new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ())};
        for (Vec3d target : targets) {
            net.minecraft.util.hit.BlockHitResult hit = client.world.raycast(
                    new net.minecraft.world.RaycastContext(from, target,
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                            net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static void drawFlaggedPlayerOutlines(MinecraftClient client, BlockPos cameraPos,
                                                  Direction lensDir, float tickDelta) {
        if (client.world == null) return;
        net.minecraft.block.entity.BlockEntity be = client.world.getBlockEntity(cameraPos);
        if (!(be instanceof kombuchamc.createsecurity.block.entity.CameraBlockEntity camera)) return;
        java.util.Set<java.util.UUID> flagged = camera.getFlaggedPlayers();
        if (flagged.isEmpty()) return;

        MatrixStack viewStack = new MatrixStack();
        viewStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X
                .rotationDegrees(facingToPitch(lensDir)));
        viewStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y
                .rotationDegrees(facingToYaw(lensDir) + 180f));
        Vec3d camCenter = Vec3d.ofCenter(cameraPos);

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.push();
        mvStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            net.minecraft.client.render.VertexConsumerProvider.Immediate immediate =
                    client.getBufferBuilders().getEntityVertexConsumers();
            net.minecraft.client.render.VertexConsumer lines =
                    immediate.getBuffer(net.minecraft.client.render.RenderLayer.getLines());
            for (net.minecraft.entity.player.PlayerEntity player : client.world.getPlayers()) {
                if (!flagged.contains(player.getUuid())) continue;
                Vec3d lerped = player.getLerpedPos(tickDelta);
                net.minecraft.util.math.Box box = player.getDimensions(player.getPose())
                        .getBoxAt(lerped.x, lerped.y, lerped.z).expand(0.05)
                        .offset(-camCenter.x, -camCenter.y, -camCenter.z);
                net.minecraft.client.render.WorldRenderer.drawBox(
                        viewStack, lines, box, 1f, 0f, 0f, 1f);
            }
            immediate.draw(net.minecraft.client.render.RenderLayer.getLines());
        } finally {
            mvStack.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static float facingToYaw(Direction facing) {

        return switch (facing) {
            case NORTH -> 180.5f;
            case SOUTH -> 0.5f;
            case WEST -> 90.5f;
            case EAST -> -89.5f;
            default -> 180f;
        };
    }

    private static float facingToPitch(Direction facing) {
        return switch (facing) {
            case UP -> -90f;
            case DOWN -> 90f;
            default -> 0f;
        };
    }

    public static void cleanup(BlockPos cameraPos) {
        SimpleFramebuffer fb = framebuffers.remove(cameraPos);
        if (fb != null) fb.delete();
        SimpleFramebuffer nameFb = nameFramebuffers.remove(cameraPos);
        if (nameFb != null) nameFb.delete();
        currentFovByCamera.remove(cameraPos);
        lastRenderNanos.remove(cameraPos);
        renderErrorLogged.remove(cameraPos);
        CameraChunkVisibility.cleanup(cameraPos);
    }
}

