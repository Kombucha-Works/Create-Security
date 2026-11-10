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

    private static final float PANEL_ASPECT = 1.5f;
    private static float currentAspect = PANEL_ASPECT;

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

    private static SimpleFramebuffer playerViewSnapshot;
    private static boolean snapshotValid;
    private static boolean stealPending;
    private static long lastStealNanos;

    private static final int MIN_FRAMES_BETWEEN_STEALS = 4;
    private static int shadedFrames;
    private static int lastStealFrame;

    private static final boolean SHADED_SUPPRESS_EXTRAS = true;
    private static BlockPos pendingStealCamera;
    private static Direction pendingStealLens;
    private static float pendingStealFov = NORMAL_FOV;
    private static boolean stolenFrameActive;
    private static Object lastLoggedWorld;
    private static boolean unswappableLogged;
    private static final long FEED_GATE_MIN_MS = 3_000L;
    private static final long FEED_GATE_MAX_MS = 20_000L;
    private static Object feedGateWorld;
    private static boolean feedGateOpen;
    private static long feedGateStartMs;
    private static Direction sfLensDir;
    private static float sfTickDelta;
    private static SimpleFramebuffer sfFeedFb;
    private static Framebuffer sfMain;
    private static Camera sfSavedCamera;
    private static Frustum sfSavedFrustum;
    private static Matrix4f sfSavedProj;
    private static VertexSorter sfSavedSorter;
    private static Matrix3f sfSavedInvRot;
    private static double sfSortX;
    private static double sfSortY;
    private static double sfSortZ;



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
        return currentAspect;
    }

    public static boolean isPreRendering() {
        return isPreRendering;
    }

    private static final List<Runnable> deferredPanels = new ArrayList<>();
    private static Matrix4f deferredProj;
    private static VertexSorter deferredSorter;

    public static boolean deferPanels(BlockPos cameraPos) {
        if (cameraPos == null) return false;
        if (insideInnerRender || isPreRendering) return false;
        return kombuchamc.createsecurity.client.compat.RenderCompat.shaderPackInUse();
    }

    public static void queuePanel(Runnable draw) {
        if (deferredProj == null) {
            deferredProj = new Matrix4f(RenderSystem.getProjectionMatrix());
            deferredSorter = RenderSystem.getVertexSorting();
        }
        deferredPanels.add(draw);
    }

    public static void drawDeferredPanels() {
        if (deferredPanels.isEmpty()) {
            deferredProj = null;
            return;
        }
        Matrix4f savedProj = RenderSystem.getProjectionMatrix();
        VertexSorter savedSorter = RenderSystem.getVertexSorting();
        try {
            RenderSystem.setProjectionMatrix(deferredProj, deferredSorter);
            for (Runnable draw : deferredPanels) {
                draw.run();
            }
        } catch (Throwable t) {
            kombuchamc.createsecurity.CreateSecurity.LOGGER.error("Failed to draw camera panels", t);
        } finally {
            RenderSystem.setProjectionMatrix(savedProj, savedSorter);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            deferredPanels.clear();
            deferredProj = null;
        }
    }

    public static boolean isInsideInnerRender() {
        return insideInnerRender;
    }

    public static boolean wantsVanillaTerrain() {
        return insideInnerRender && !stolenFrameActive;
    }

    private static boolean terrainReadyForFeeds(MinecraftClient client) {
        if (client.worldRenderer == null) return false;
        if (feedGateWorld != client.world) {
            feedGateWorld = client.world;
            feedGateOpen = false;
            feedGateStartMs = net.minecraft.util.Util.getMeasuringTimeMs();
        }
        if (feedGateOpen) return true;
        long elapsed = net.minecraft.util.Util.getMeasuringTimeMs() - feedGateStartMs;
        if (elapsed < FEED_GATE_MIN_MS) return false;
        if (client.worldRenderer.isTerrainRenderComplete() || elapsed > FEED_GATE_MAX_MS) {
            feedGateOpen = true;
            kombuchamc.createsecurity.CreateSecurity.LOGGER.info(
                    "Camera feeds enabled {} ms after world load (terrainComplete={})",
                    elapsed, client.worldRenderer.isTerrainRenderComplete());
        }
        return feedGateOpen;
    }

    public static boolean suppressExtras() {
        return insideInnerRender && SHADED_SUPPRESS_EXTRAS;
    }


    public static BlockPos getCurrentCameraPos() {
        return currentCameraPos;
    }

    public static void shadedFrameTick() {
        shadedFrames++;
    }



    private static boolean shadedStealDue() {
        if (!snapshotValid) return false;
        if (shadedFrames - lastStealFrame < MIN_FRAMES_BETWEEN_STEALS) return false;
        long interval = kombuchamc.createsecurity.config.CSConfigs.shadedFeedIntervalMs() * 1_000_000L;
        return System.nanoTime() - lastStealNanos >= interval;
    }


    public static boolean consumeStealPending() {
        if (!stealPending) return false;
        stealPending = false;
        return snapshotValid;
    }

    public static void savePlayerView(MinecraftClient client) {
        Framebuffer main = client.getFramebuffer();
        if (main == null) return;
        int width = main.textureWidth;
        int height = main.textureHeight;
        if (width <= 0 || height <= 0) return;
        if (playerViewSnapshot == null
                || playerViewSnapshot.textureWidth != width
                || playerViewSnapshot.textureHeight != height) {
            if (playerViewSnapshot != null) playerViewSnapshot.delete();
            playerViewSnapshot = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            snapshotValid = false;
        }
        blitFull(main.fbo, playerViewSnapshot.fbo, width, height);
        snapshotValid = true;
        main.beginWrite(true);
    }

    public static void restorePlayerView(MinecraftClient client) {
        Framebuffer main = client.getFramebuffer();
        if (main == null || playerViewSnapshot == null || !snapshotValid) return;
        if (playerViewSnapshot.textureWidth != main.textureWidth
                || playerViewSnapshot.textureHeight != main.textureHeight) {
            return;
        }
        blitFull(playerViewSnapshot.fbo, main.fbo, main.textureWidth, main.textureHeight);
        main.beginWrite(true);
    }

    private static void blitFull(int sourceFbo, int destinationFbo, int width, int height) {
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, destinationFbo);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
    }

    private static void cropInto(Framebuffer source, SimpleFramebuffer destination) {
        int sourceWidth = source.textureWidth;
        int sourceHeight = source.textureHeight;
        int width = destination.textureWidth;
        int height = destination.textureHeight;
        float aspect = width / (float) height;
        int cropWidth;
        int cropHeight;
        if (sourceWidth / (float) sourceHeight >= aspect) {
            cropHeight = sourceHeight;
            cropWidth = Math.max(1, Math.round(sourceHeight * aspect));
        } else {
            cropWidth = sourceWidth;
            cropHeight = Math.max(1, Math.round(sourceWidth / aspect));
        }
        int left = (sourceWidth - cropWidth) / 2;
        int bottom = (sourceHeight - cropHeight) / 2;
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, source.fbo);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, destination.fbo);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(left, bottom, left + cropWidth, bottom + cropHeight,
                0, 0, width, height, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(left, bottom, left + cropWidth, bottom + cropHeight,
                0, 0, width, height, org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                org.lwjgl.opengl.GL11.GL_NEAREST);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, destination.fbo);
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
        if (client.world == null || client.player == null) return false;
        return client.world.getChunkManager()
                .isChunkLoaded(cameraPos.getX() >> 4, cameraPos.getZ() >> 4);
    }

    private static boolean eligibleThisPass(BlockPos cameraPos, boolean shadedPass) {
        if (!kombuchamc.createsecurity.client.compat.RenderCompat.shadedFeedsActive()) return true;
        return shadedPass == isCameraInClientRange(cameraPos);
    }

    public static boolean canShowFeed(BlockPos cameraPos) {
        return isCameraInClientRange(cameraPos)
                || kombuchamc.createsecurity.client.camera.RemoteChunkRequests.isWatching(cameraPos);
    }

    public static void preRenderAllCameras(MinecraftClient client, float tickDelta, long limitTime) {
        preRenderAllCameras(client, tickDelta, limitTime, false);
    }

    public static void preRenderAllCameras(MinecraftClient client, float tickDelta, long limitTime,
                                           boolean shadedPass) {
        if (isPreRendering || client.world == null || client.player == null) return;

        if (client.isPaused()) return;

        if (!terrainReadyForFeeds(client)) return;

        if (shadedPass && !shadedStealDue()) return;

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

        Set<BlockPos> activeCameras = new HashSet<>(sampleDisplayPerCamera.keySet());
        if (portableCameraPos != null) activeCameras.add(portableCameraPos);
        kombuchamc.createsecurity.client.camera.RemoteChunkRequests.sync(activeCameras);
        kombuchamc.createsecurity.client.camera.RemoteFeedRenderer.tick();

        if (!shadedPass && now - lastSweepNanos > SWEEP_INTERVAL_NANOS) {
            lastSweepNanos = now;
            int sweptFeeds = 0;
            int sweptNames = 0;
            for (var it = framebuffers.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (allLinkedCameras.contains(e.getKey())) continue;
                e.getValue().delete();
                it.remove();
                sweptFeeds++;
            }
            for (var it = nameFramebuffers.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (allLinkedCameras.contains(e.getKey())) continue;
                e.getValue().delete();
                it.remove();
                sweptNames++;
            }
            currentFovByCamera.keySet().retainAll(allLinkedCameras);
            lastRenderNanos.keySet().retainAll(allLinkedCameras);
            renderErrorLogged.retainAll(allLinkedCameras);
        }

        List<BlockPos> due = new ArrayList<>();
        for (BlockPos camPos : sampleDisplayPerCamera.keySet()) {
            long last = lastRenderNanos.getOrDefault(camPos, 0L);
            if (shadedPass || now - last >= refreshIntervalNanos(nearestDistSqPerCamera.get(camPos))) {
                due.add(camPos);
            }
        }
        boolean portableDue = portableCameraPos != null
                && (shadedPass
                        || now - lastRenderNanos.getOrDefault(portableCameraPos, 0L) >= NEAR_INTERVAL_NANOS);
        if (due.isEmpty() && !portableDue) return;
        due.sort((a, b) -> Long.compare(
                lastRenderNanos.getOrDefault(a, 0L),
                lastRenderNanos.getOrDefault(b, 0L)));

        if (portableDue && portableCameraPos != null && canShowFeed(portableCameraPos)) {
            ensureFramebuffers(client, portableCameraPos);
        }
        for (BlockPos camPos : due) {
            if (!canShowFeed(camPos)) continue;
            ensureFramebuffers(client, camPos);
        }

        isPreRendering = true;
        try {
            int rendered = 0;
            int maxRenders = shadedPass ? 1 : MAX_CAMERA_RENDERS_PER_FRAME;

            if (portableDue) {
                Direction lensDir = portableLensDir;
                if (lensDir == null) {
                    BlockState camState = client.world.getBlockState(portableCameraPos);
                    if (camState.isOf(RegisterModBlocks.CAMERA_BLOCK)) {
                        lensDir = camState.get(CameraBlock.FACING).getOpposite();
                    }
                }
                if (lensDir != null && rendered < maxRenders && canShowFeed(portableCameraPos)
                        && eligibleThisPass(portableCameraPos, shadedPass)) {
                    renderWithFovLerp(client, portableCameraPos, lensDir, portableFisheye,
                            now, tickDelta, limitTime);
                    rendered++;
                }
            }
            for (BlockPos camPos : due) {
                if (rendered >= maxRenders) break;

                if (camPos.equals(portableCameraPos)) continue;
                if (!canShowFeed(camPos)) continue;
                if (!eligibleThisPass(camPos, shadedPass)) continue;
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

            if (shadedPass && rendered > 0) {
                lastStealNanos = now;
                lastStealFrame = shadedFrames;
                stealPending = true;
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
        if (isCameraInClientRange(camPos)
                && kombuchamc.createsecurity.client.compat.RenderCompat.shadedFeedsActive()) {
            pendingStealCamera = camPos;
            pendingStealLens = lensDir;
            pendingStealFov = lerped;
        } else {
            renderCameraView(client, camPos, lensDir, tickDelta, limitTime);
        }
        currentFov = NORMAL_FOV;
    }

    private static long refreshIntervalNanos(double nearestDistSq) {
        if (nearestDistSq <= NEAR_DIST_SQ) return NEAR_INTERVAL_NANOS;
        if (nearestDistSq <= MID_DIST_SQ) return MID_INTERVAL_NANOS;
        return FAR_INTERVAL_NANOS;
    }

    private static boolean ensureFramebuffers(MinecraftClient client, BlockPos cameraPos) {
        int fbWidth = Math.max(1, kombuchamc.createsecurity.config.CSConfigs.cameraResolution());
        int fbHeight = Math.max(1, fbWidth * 2 / 3);
        int nameWidth = Math.max(1, kombuchamc.createsecurity.config.CSConfigs.nameTagResolution());
        int nameHeight = Math.max(1, nameWidth * 2 / 3);

        SimpleFramebuffer fb = framebuffers.get(cameraPos);
        SimpleFramebuffer nameFb = nameFramebuffers.get(cameraPos);
        boolean fbOk = fb != null && fb.textureWidth == fbWidth && fb.textureHeight == fbHeight;
        boolean nameOk = nameFb != null
                && nameFb.textureWidth == nameWidth && nameFb.textureHeight == nameHeight;
        if (fbOk && nameOk) return true;

        if (!fbOk) {
            if (fb != null) {
                fb.delete();
                framebuffers.remove(cameraPos);
            }
            framebuffers.put(cameraPos,
                    new SimpleFramebuffer(fbWidth, fbHeight, true, MinecraftClient.IS_SYSTEM_MAC));
        }
        if (!nameOk) {
            if (nameFb != null) {
                nameFb.delete();
                nameFramebuffers.remove(cameraPos);
            }
            nameFramebuffers.put(cameraPos,
                    new SimpleFramebuffer(nameWidth, nameHeight, false, MinecraftClient.IS_SYSTEM_MAC));
        }

        Framebuffer main = client.getFramebuffer();
        if (main != null) main.beginWrite(true);
        return true;
    }

    public static void beginStolenFrameIfPending(MinecraftClient client, float tickDelta) {
        if (pendingStealCamera == null) return;
        BlockPos cameraPos = pendingStealCamera;
        Direction lensDir = pendingStealLens;
        pendingStealCamera = null;
        pendingStealLens = null;
        if (client.world == null || client.player == null) return;
        SimpleFramebuffer fb = framebuffers.get(cameraPos);
        if (fb == null || !snapshotValid) return;

        Camera ourCamera = new Camera();
        ourCamera.update(client.world, client.player, true, false, tickDelta);
        CameraAccessor camAcc = (CameraAccessor) ourCamera;
        Vec3d center = Vec3d.ofCenter(cameraPos);
        camAcc.invokeSetPos(center.x, center.y, center.z);
        camAcc.invokeSetRotation(facingToYaw(lensDir), facingToPitch(lensDir));

        GameRendererAccessor grAcc = (GameRendererAccessor) client.gameRenderer;
        WorldRendererAccessor wrAcc = (WorldRendererAccessor) client.worldRenderer;

        sfFeedFb = fb;
        sfMain = client.getFramebuffer();
        sfSavedCamera = grAcc.getCameraField();
        sfSavedFrustum = wrAcc.getFrustumField();
        sfSavedProj = RenderSystem.getProjectionMatrix();
        sfSavedSorter = RenderSystem.getVertexSorting();
        sfSavedInvRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        sfSortX = wrAcc.getLastTranslucentSortX();
        sfSortY = wrAcc.getLastTranslucentSortY();
        sfSortZ = wrAcc.getLastTranslucentSortZ();

        currentAspect = sfMain.textureWidth / (float) sfMain.textureHeight;
        currentFov = pendingStealFov;
        currentCameraPos = cameraPos;
        sfLensDir = lensDir;
        sfTickDelta = tickDelta;
        grAcc.setCameraField(ourCamera);
        insideInnerRender = true;
        isPreRendering = true;
        stolenFrameActive = true;
    }

    public static boolean endStolenFrameIfActive(MinecraftClient client) {
        if (!stolenFrameActive) return false;
        stolenFrameActive = false;
        GameRendererAccessor grAcc = (GameRendererAccessor) client.gameRenderer;
        WorldRendererAccessor wrAcc = (WorldRendererAccessor) client.worldRenderer;
        try {
            currentAspect = PANEL_ASPECT;
            cropInto(sfMain, sfFeedFb);
            RenderSystem.viewport(0, 0, sfFeedFb.textureWidth, sfFeedFb.textureHeight);
            sfFeedFb.beginWrite(true);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0f, 0f, 0f, 1f);
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.colorMask(true, true, true, true);
            drawFlaggedPlayerOutlines(client, currentCameraPos, sfLensDir, sfTickDelta);
            drawFeedNameTags(client, currentCameraPos, sfLensDir, sfTickDelta);
        } catch (Throwable t) {
            if (renderErrorLogged.add(currentCameraPos == null
                    ? BlockPos.ORIGIN : currentCameraPos.toImmutable())) {
                kombuchamc.createsecurity.CreateSecurity.LOGGER.error("Failed to finish stolen feed", t);
            }
        } finally {
            insideInnerRender = false;
            isPreRendering = false;
            currentAspect = PANEL_ASPECT;
            currentFov = NORMAL_FOV;
            currentCameraPos = null;
            sfLensDir = null;
            grAcc.setCameraField(sfSavedCamera);
            wrAcc.setFrustumField(sfSavedFrustum);
            RenderSystem.setProjectionMatrix(sfSavedProj, sfSavedSorter);
            RenderSystem.setInverseViewRotationMatrix(sfSavedInvRot);
            wrAcc.setLastTranslucentSortX(sfSortX);
            wrAcc.setLastTranslucentSortY(sfSortY);
            wrAcc.setLastTranslucentSortZ(sfSortZ);
            wrAcc.getUpdateFinished().set(true);
            restorePlayerView(client);
        }
        return true;
    }

    private static void renderCameraView(MinecraftClient client, BlockPos cameraPos, Direction lensDir,
                                         float tickDelta, long limitTime) {
        if (client.world == null || client.player == null) return;

        SimpleFramebuffer fb = framebuffers.get(cameraPos);
        if (fb == null) return;
        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;

        Camera ourCamera = new Camera();
        ourCamera.update(client.world, client.player, true, false, tickDelta);

        CameraAccessor camAcc = (CameraAccessor) ourCamera;
        Vec3d center = Vec3d.ofCenter(cameraPos);
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

        boolean remoteFeed = !isCameraInClientRange(cameraPos);

        if (lastLoggedWorld != client.world) {
            lastLoggedWorld = client.world;
            kombuchamc.createsecurity.CreateSecurity.LOGGER.info(
                    "First camera feed in this world: remote={}, canSwap={}, {}", remoteFeed,
                    kombuchamc.createsecurity.client.compat.RenderCompat.canRenderInRangeFeed(),
                    kombuchamc.createsecurity.client.compat.RenderCompat.describeShaderState());
        }

        if (!remoteFeed
                && !kombuchamc.createsecurity.client.compat.RenderCompat.canRenderInRangeFeed()) {
            if (!unswappableLogged) {
                unswappableLogged = true;
                kombuchamc.createsecurity.CreateSecurity.LOGGER.warn(
                        "Skipping camera feeds: a shader pack is loaded but Iris has no pipeline to"
                                + " swap out yet, and re-entering renderWorld with the pack live blacks"
                                + " out the player's view. {}",
                        kombuchamc.createsecurity.client.compat.RenderCompat.describeShaderState());
            }
            return;
        }

        {
            wrAcc.setTransparencyPostProcessor(null);
            wrAcc.setTranslucentFramebufferField(null);
            wrAcc.setParticlesFramebufferField(null);
            wrAcc.setWeatherFramebufferField(null);
            wrAcc.setCloudsFramebufferField(null);
            wrAcc.setEntityFramebufferField(null);
            wrAcc.setEntityOutlinesFramebufferField(null);
        }

        mcAcc.setFramebufferField(fb);
        currentAspect = PANEL_ASPECT;
        grAcc.setCameraField(ourCamera);
        currentCameraPos = cameraPos;

        fb.setClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        fb.clear(MinecraftClient.IS_SYSTEM_MAC);
        fb.beginWrite(true);
        RenderSystem.viewport(0, 0, fbWidth, fbHeight);

        insideInnerRender = true;
        try {
            kombuchamc.createsecurity.client.compat.RenderCompat.beginFeedRender(remoteFeed);

            if (!remoteFeed) {
                client.gameRenderer.renderWorld(tickDelta, 0L, new MatrixStack());
            } else {
                kombuchamc.createsecurity.client.camera.RemoteFeedRenderer.render(
                        client, cameraPos, ourCamera, currentFov, tickDelta);
            }

            currentAspect = PANEL_ASPECT;

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
            kombuchamc.createsecurity.client.compat.RenderCompat.endFeedRender();
            insideInnerRender = false;
            currentAspect = PANEL_ASPECT;
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
