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
                    List<Boolean> armed = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        positions.add(buf.readBlockPos());
                        armed.add(buf.readBoolean());
                    }
                    client.execute(() -> kombuchamc.createsecurity.client.KeycardReaderClientStore
                            .replaceAll(positions, armed));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.KEYCARD_READER_UPDATE,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean present = buf.readBoolean();
                    boolean armed = buf.readBoolean();
                    client.execute(() -> kombuchamc.createsecurity.client.KeycardReaderClientStore
                            .set(pos, present, armed));
                });
    }

    public static void sendToggleDetectionPacket(BlockPos pos, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(enabled);
        ClientPlayNetworking.send(ModPackets.TOGGLE_DETECTION, buf);
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

