package kombuchamc.createsecurity.network;

import kombuchamc.createsecurity.block.entity.CameraDisplayBlockEntity;
import kombuchamc.createsecurity.screen.PortableCameraViewScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class ModClientPackets {
    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_CHUNK_DATA,
                (client, handler, buf, responseSender) -> {
                    BlockPos cameraPos = buf.readBlockPos();
                    int chunkX = buf.readVarInt();
                    int chunkZ = buf.readVarInt();
                    net.minecraft.network.packet.s2c.play.ChunkData chunkData =
                            new net.minecraft.network.packet.s2c.play.ChunkData(buf, chunkX, chunkZ);
                    net.minecraft.network.packet.s2c.play.LightData lightData =
                            new net.minecraft.network.packet.s2c.play.LightData(buf, chunkX, chunkZ);
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteChunkStore
                            .accept(cameraPos, chunkX, chunkZ, chunkData, lightData));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_ENTITIES,
                (client, handler, buf, responseSender) -> {
                    kombuchamc.createsecurity.client.camera.RemoteEntities.Snapshot snapshot =
                            kombuchamc.createsecurity.client.camera.RemoteEntities.read(buf);
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteEntities.apply(snapshot));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_BLOCK_UPDATE,
                (client, handler, buf, responseSender) -> {
                    BlockPos cameraPos = buf.readBlockPos();
                    List<kombuchamc.createsecurity.client.camera.RemoteChunkStore.BlockUpdate> updates =
                            kombuchamc.createsecurity.client.camera.RemoteChunkStore.readBlockUpdates(buf);
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteChunkStore
                            .applyBlockUpdates(cameraPos, updates));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_LIGHT_UPDATE,
                (client, handler, buf, responseSender) -> {
                    BlockPos cameraPos = buf.readBlockPos();
                    List<kombuchamc.createsecurity.client.camera.RemoteChunkStore.LightUpdate> updates =
                            kombuchamc.createsecurity.client.camera.RemoteChunkStore.readLightUpdates(buf);
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteChunkStore
                            .applyLightUpdates(cameraPos, updates));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_PARTICLES,
                (client, handler, buf, responseSender) -> {
                    BlockPos cameraPos = buf.readBlockPos();
                    int count = buf.readVarInt();
                    List<net.minecraft.network.packet.s2c.play.ParticleS2CPacket> events =
                            new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        events.add(new net.minecraft.network.packet.s2c.play.ParticleS2CPacket(buf));
                    }
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteParticles
                            .spawn(cameraPos, events));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.REMOTE_CHUNK_CLEAR,
                (client, handler, buf, responseSender) -> {
                    BlockPos cameraPos = buf.readBlockPos();
                    client.execute(() -> kombuchamc.createsecurity.client.camera.RemoteChunkStore
                            .clear(cameraPos));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.DISPLAY_LINK_SYNC,
                (client, handler, buf, responseSender) -> {
                    BlockPos displayPos = buf.readBlockPos();
                    boolean hasLink = buf.readBoolean();
                    BlockPos cameraPos = hasLink ? buf.readBlockPos() : null;
                    Direction lensDir = hasLink ? Direction.byId(buf.readInt()) : null;
                    boolean hasFisheye = hasLink && buf.readBoolean();
                    int cameraCount = hasLink ? buf.readVarInt() : 0;
                    String labelRaw = hasLink ? buf.readString() : "";
                    String cameraLabel = labelRaw.isEmpty() ? null : labelRaw;
                    BlockPos nextCameraPos = hasLink && buf.readBoolean() ? buf.readBlockPos() : null;
                    boolean nextCameraFisheye = nextCameraPos != null && buf.readBoolean();
                    BlockPos prevCameraPos = hasLink && buf.readBoolean() ? buf.readBlockPos() : null;
                    boolean prevCameraFisheye = prevCameraPos != null && buf.readBoolean();

                    client.execute(() -> {
                        if (client.world == null) return;
                        BlockEntity be = client.world.getBlockEntity(displayPos);
                        if (be instanceof CameraDisplayBlockEntity display) {
                            display.applyLinkSyncFromServer(cameraPos, lensDir, hasFisheye, cameraCount,
                                    cameraLabel, nextCameraPos, prevCameraPos,
                                    nextCameraFisheye, prevCameraFisheye);
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_PORTABLE_VIEW,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readVarInt();
                    List<PortableCameraViewEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        BlockPos pos = buf.readBlockPos();
                        Direction lensDir = buf.readBoolean() ? Direction.byId(buf.readInt()) : null;
                        boolean fisheye = buf.readBoolean();
                        String label = buf.readString();
                        entries.add(new PortableCameraViewEntry(pos, lensDir, fisheye,
                                label.isEmpty() ? null : label));
                    }
                    client.execute(() -> {
                        if (client.world == null || entries.isEmpty()) return;
                        client.setScreen(new PortableCameraViewScreen(entries));
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.KEYCARD_READER_SYNC,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(count);
                    List<kombuchamc.createsecurity.client.KeycardReaderClientStore.Mounted> mounted =
                            new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        positions.add(buf.readBlockPos());
                        boolean armed = buf.readBoolean();
                        kombuchamc.createsecurity.block.LockKind kind =
                                kombuchamc.createsecurity.block.LockKind.byId(buf.readByte());
                        mounted.add(new kombuchamc.createsecurity.client.KeycardReaderClientStore.Mounted(
                                armed, kind, buf.readBoolean()));
                    }
                    client.execute(() -> kombuchamc.createsecurity.client.KeycardReaderClientStore
                            .replaceAll(positions, mounted));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.KEYCARD_READER_UPDATE,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean present = buf.readBoolean();
                    boolean armed = buf.readBoolean();
                    kombuchamc.createsecurity.block.LockKind kind =
                            kombuchamc.createsecurity.block.LockKind.byId(buf.readByte());
                    boolean unlocked = buf.readBoolean();
                    client.execute(() -> kombuchamc.createsecurity.client.KeycardReaderClientStore.set(
                            pos, present,
                            new kombuchamc.createsecurity.client.KeycardReaderClientStore.Mounted(
                                    armed, kind, unlocked)));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OPEN_CODE_LOCK,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean setting = buf.readBoolean();
                    client.execute(() -> client.setScreen(
                            new kombuchamc.createsecurity.screen.CodeLockScreen(pos, setting)));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OBSIDIAN_OWNER_SYNC,
                (client, handler, buf, responseSender) -> {
                    boolean bypass = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(count);
                    List<Boolean> mine = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        positions.add(buf.readBlockPos());
                        mine.add(buf.readBoolean());
                    }
                    client.execute(() -> kombuchamc.createsecurity.client.ObsidianOwnerClientStore
                            .replaceAll(positions, mine, bypass));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OBSIDIAN_OWNER_UPDATE,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean present = buf.readBoolean();
                    boolean mine = buf.readBoolean();
                    client.execute(() -> kombuchamc.createsecurity.client.ObsidianOwnerClientStore
                            .set(pos, present, mine));
                });
    }

    public static void sendToggleDetectionPacket(BlockPos pos, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(enabled);
        ClientPlayNetworking.send(ModPackets.TOGGLE_DETECTION, buf);
    }

    public static void sendCodeLockSubmit(BlockPos pos, int code) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(code);
        ClientPlayNetworking.send(ModPackets.CODE_LOCK_SUBMIT, buf);
    }

    public static void sendPlayerListUpdate(net.minecraft.util.Hand hand, boolean includeMode,
                                            List<String> names) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(hand == net.minecraft.util.Hand.MAIN_HAND);
        buf.writeBoolean(includeMode);
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeString(name, 16);
        }
        ClientPlayNetworking.send(ModPackets.PLAYER_LIST_UPDATE, buf);
    }
}
