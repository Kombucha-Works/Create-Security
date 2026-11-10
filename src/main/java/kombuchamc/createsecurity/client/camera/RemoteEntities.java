package kombuchamc.createsecurity.client.camera;

import com.mojang.authlib.GameProfile;
import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class RemoteEntities {

    private static final int TICKS_PER_UPDATE = 2;
    private static final long UPDATE_NANOS = TICKS_PER_UPDATE * 50_000_000L;
    private static final int TRACKER_END = 255;

    private RemoteEntities() {}

    public record Full(int typeId, UUID uuid, @Nullable String playerName,
                       List<DataTracker.SerializedEntry<?>> tracked, @Nullable ItemStack[] equipment) {}

    public record Update(int id, double x, double y, double z, float yaw, float pitch,
                         float headYaw, float bodyYaw, @Nullable Full full) {}

    public record Snapshot(BlockPos camera, List<Update> updates) {}

    private static final class Tracked {
        final Entity entity;
        final ClientWorld world;
        double prevX, prevY, prevZ, x, y, z;
        float prevYaw, yaw, prevPitch, pitch, prevHead, head, prevBody, body;
        long updatedNanos;

        Tracked(Entity entity, ClientWorld world, Update update) {
            this.entity = entity;
            this.world = world;
            prevX = x = update.x();
            prevY = y = update.y();
            prevZ = z = update.z();
            prevYaw = yaw = update.yaw();
            prevPitch = pitch = update.pitch();
            prevHead = head = update.headYaw();
            prevBody = body = update.bodyYaw();
            updatedNanos = Util.getMeasuringTimeNano();
        }

        void move(Update update, long now) {
            prevX = x;
            prevY = y;
            prevZ = z;
            prevYaw = yaw;
            prevPitch = pitch;
            prevHead = head;
            prevBody = body;
            x = update.x();
            y = update.y();
            z = update.z();
            yaw = update.yaw();
            pitch = update.pitch();
            head = update.headYaw();
            body = update.bodyYaw();
            updatedNanos = now;

            if (entity instanceof LivingEntity living) {
                double dx = x - prevX;
                double dz = z - prevZ;
                float stride = (float) Math.sqrt(dx * dx + dz * dz) / TICKS_PER_UPDATE;
                for (int tick = 0; tick < TICKS_PER_UPDATE; tick++) {
                    living.limbAnimator.updateLimbs(Math.min(stride * 4f, 1f), 0.4f);
                }
            }
            entity.age += TICKS_PER_UPDATE;
        }
    }

    private static final Map<BlockPos, Map<Integer, Tracked>> byCamera = new HashMap<>();
    private static boolean applyFailureLogged;
    private static boolean renderFailureLogged;

    public static Snapshot read(PacketByteBuf buf) {
        BlockPos camera = buf.readBlockPos();
        int count = buf.readVarInt();
        List<Update> updates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float headYaw = buf.readFloat();
            float bodyYaw = buf.readFloat();
            Full full = null;
            if (buf.readBoolean()) {
                int typeId = buf.readVarInt();
                UUID uuid = buf.readUuid();
                String playerName = buf.readBoolean() ? buf.readString() : null;
                List<DataTracker.SerializedEntry<?>> tracked = new ArrayList<>();
                int index;
                while ((index = buf.readUnsignedByte()) != TRACKER_END) {
                    tracked.add(DataTracker.SerializedEntry.fromBuf(buf, index));
                }
                ItemStack[] equipment = null;
                if (buf.readBoolean()) {
                    equipment = new ItemStack[EquipmentSlot.values().length];
                    for (int slot = 0; slot < equipment.length; slot++) equipment[slot] = buf.readItemStack();
                }
                full = new Full(typeId, uuid, playerName, tracked, equipment);
            }
            updates.add(new Update(id, x, y, z, yaw, pitch, headYaw, bodyYaw, full));
        }
        return new Snapshot(camera, updates);
    }

    public static void apply(Snapshot snapshot) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !RemoteChunkRequests.isWatching(snapshot.camera())) return;

        Map<Integer, Tracked> tracked = byCamera.computeIfAbsent(snapshot.camera().toImmutable(), key -> new HashMap<>());
        Set<Integer> seen = new HashSet<>();
        long now = Util.getMeasuringTimeNano();
        for (Update update : snapshot.updates()) {
            seen.add(update.id());
            Tracked entry = tracked.get(update.id());
            if (update.full() != null) entry = refresh(world, tracked, entry, update);
            if (entry != null) entry.move(update, now);
        }
        tracked.keySet().retainAll(seen);
    }

    @Nullable
    private static Tracked refresh(ClientWorld world, Map<Integer, Tracked> tracked,
                                   @Nullable Tracked entry, Update update) {
        Full full = update.full();
        try {
            EntityType<?> type = Registries.ENTITY_TYPE.get(full.typeId());
            if (type == null) {
                tracked.remove(update.id());
                return null;
            }
            if (entry == null || entry.entity.getType() != type || entry.world != world) {
                Entity entity = create(world, type, full);
                if (entity == null) {
                    tracked.remove(update.id());
                    return null;
                }
                entity.setId(update.id());
                entity.setUuid(full.uuid());
                entity.setPosition(update.x(), update.y(), update.z());
                entry = new Tracked(entity, world, update);
                tracked.put(update.id(), entry);
            }
            if (!full.tracked().isEmpty()) entry.entity.getDataTracker().writeUpdatedEntries(full.tracked());
            if (full.equipment() != null && entry.entity instanceof LivingEntity living) {
                EquipmentSlot[] slots = EquipmentSlot.values();
                for (int slot = 0; slot < slots.length && slot < full.equipment().length; slot++) {
                    living.equipStack(slots[slot], full.equipment()[slot]);
                }
            }
            return entry;
        } catch (Throwable t) {
            if (!applyFailureLogged) {
                applyFailureLogged = true;
                CreateSecurity.LOGGER.error("Failed to mirror a remote camera entity", t);
            }
            tracked.remove(update.id());
            return null;
        }
    }

    @Nullable
    private static Entity create(ClientWorld world, EntityType<?> type, Full full) {
        if (type == EntityType.PLAYER) {
            return full.playerName() == null
                    ? null
                    : new OtherClientPlayerEntity(world, new GameProfile(full.uuid(), full.playerName()));
        }
        return type.create(world);
    }

    public static void render(BlockPos cameraPos, RemoteRegionView view, Frustum frustum, Vec3d eye,
                              MatrixStack matrices, VertexConsumerProvider.Immediate immediate) {
        Map<Integer, Tracked> tracked = byCamera.get(cameraPos);
        if (tracked == null || tracked.isEmpty()) return;
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        long now = Util.getMeasuringTimeNano();

        for (Tracked entry : tracked.values()) {
            Entity entity = entry.entity;
            float alpha = MathHelper.clamp((now - entry.updatedNanos) / (float) UPDATE_NANOS, 0f, 1f);
            double x = MathHelper.lerp(alpha, entry.prevX, entry.x);
            double y = MathHelper.lerp(alpha, entry.prevY, entry.y);
            double z = MathHelper.lerp(alpha, entry.prevZ, entry.z);

            entity.prevX = entity.lastRenderX = entry.prevX;
            entity.prevY = entity.lastRenderY = entry.prevY;
            entity.prevZ = entity.lastRenderZ = entry.prevZ;
            entity.setPosition(entry.x, entry.y, entry.z);
            entity.prevYaw = entry.prevYaw;
            entity.setYaw(entry.yaw);
            entity.prevPitch = entry.prevPitch;
            entity.setPitch(entry.pitch);
            if (entity instanceof LivingEntity living) {
                living.prevBodyYaw = entry.prevBody;
                living.bodyYaw = entry.body;
                living.prevHeadYaw = entry.prevHead;
                living.headYaw = entry.head;
            }

            try {
                if (!isVisible(entity, frustum, eye)) continue;
                int light = WorldRenderer.getLightmapCoordinates(view,
                        BlockPos.ofFloored(x, y + entity.getStandingEyeHeight(), z));
                dispatcher.render(entity, x - eye.x, y - eye.y, z - eye.z,
                        MathHelper.lerpAngleDegrees(alpha, entry.prevYaw, entry.yaw), alpha,
                        matrices, immediate, light);
            } catch (Throwable t) {
                if (!renderFailureLogged) {
                    renderFailureLogged = true;
                    CreateSecurity.LOGGER.error("Failed to render a remote camera entity", t);
                }
            }
        }
    }

    private static boolean isVisible(Entity entity, Frustum frustum, Vec3d eye) {
        if (!entity.shouldRender(eye.x, eye.y, eye.z)) return false;
        if (entity.ignoreCameraFrustum) return true;
        Box box = entity.getVisibilityBoundingBox().expand(0.5);
        if (box.isNaN() || box.getAverageSideLength() == 0) {
            box = new Box(entity.getX() - 2, entity.getY() - 2, entity.getZ() - 2,
                    entity.getX() + 2, entity.getY() + 2, entity.getZ() + 2);
        }
        return frustum.isVisible(box);
    }

    public static void clear(BlockPos cameraPos) {
        byCamera.remove(cameraPos);
    }

    public static void clearAll() {
        byCamera.clear();
    }
}
