package kombuchamc.createsecurity.network;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class ModPackets {

    public static final Identifier TOGGLE_DETECTION =
            new Identifier(CreateSecurity.MOD_ID, "toggle_detection");

    public static final Identifier DISPLAY_LINK_SYNC =
            new Identifier(CreateSecurity.MOD_ID, "display_link_sync");

    public static final Identifier OPEN_PORTABLE_VIEW =
            new Identifier(CreateSecurity.MOD_ID, "open_portable_view");

    public static final Identifier PLAYER_LIST_UPDATE =
            new Identifier(CreateSecurity.MOD_ID, "player_list_update");

    public static final Identifier KEYCARD_READER_SYNC =
            new Identifier(CreateSecurity.MOD_ID, "keycard_reader_sync");

    public static final Identifier KEYCARD_READER_UPDATE =
            new Identifier(CreateSecurity.MOD_ID, "keycard_reader_update");


    public static void sendToggleDetectionPacket(BlockPos pos, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(enabled);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(TOGGLE_DETECTION, buf);
    }

    public static void sendDisplayLinkSync(ServerWorld world, BlockPos displayPos,
                                           @Nullable BlockPos cameraPos, @Nullable Direction lensDir,
                                           boolean hasFisheye, int cameraCount, @Nullable String cameraLabel,
                                           @Nullable BlockPos nextCameraPos, @Nullable BlockPos prevCameraPos,
                                           boolean nextCameraFisheye, boolean prevCameraFisheye) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(displayPos);
        if (cameraPos != null && lensDir != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(cameraPos);
            buf.writeInt(lensDir.getId());
            buf.writeBoolean(hasFisheye);
            buf.writeVarInt(cameraCount);
            buf.writeString(cameraLabel == null ? "" : cameraLabel);
            buf.writeBoolean(nextCameraPos != null);
            if (nextCameraPos != null) {
                buf.writeBlockPos(nextCameraPos);
                buf.writeBoolean(nextCameraFisheye);
            }
            buf.writeBoolean(prevCameraPos != null);
            if (prevCameraPos != null) {
                buf.writeBlockPos(prevCameraPos);
                buf.writeBoolean(prevCameraFisheye);
            }
        } else {
            buf.writeBoolean(false);
        }
        for (ServerPlayerEntity player : PlayerLookup.tracking(world, displayPos)) {
            ServerPlayNetworking.send(player, DISPLAY_LINK_SYNC, buf);
        }
    }

    public static void sendOpenPortableView(ServerPlayerEntity player,
                                            java.util.List<PortableCameraViewEntry> entries) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entries.size());
        for (PortableCameraViewEntry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeBoolean(entry.lensDir() != null);
            if (entry.lensDir() != null) {
                buf.writeInt(entry.lensDir().getId());
            }
            buf.writeBoolean(entry.fisheye());
            buf.writeString(entry.label() == null ? "" : entry.label());
        }
        ServerPlayNetworking.send(player, OPEN_PORTABLE_VIEW, buf);
    }

    public static void registerServerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_DETECTION,
                new ServerPlayNetworking.PlayChannelHandler() {
                    @Override
                    public void receive(MinecraftServer server, ServerPlayerEntity player,
                                        ServerPlayNetworkHandler handler, PacketByteBuf buf,
                                        PacketSender responseSender) {

                        BlockPos pos = buf.readBlockPos();
                        boolean enabled = buf.readBoolean();

                        server.execute(() -> {
                            BlockEntity be = player.getServerWorld().getBlockEntity(pos);
                            if (be instanceof CameraBlockEntity camera) {
                                camera.setDetectionEnabled(enabled);
                            }
                        });
                    }
                }
        );

        ServerPlayNetworking.registerGlobalReceiver(PLAYER_LIST_UPDATE,
                (server, player, handler, buf, responseSender) -> {

                    boolean mainHand = buf.readBoolean();
                    boolean includeMode = buf.readBoolean();
                    int count = Math.min(buf.readVarInt(),
                            kombuchamc.createsecurity.items.PlayerListItem.MAX_PLAYERS);
                    java.util.List<String> names = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        names.add(buf.readString(16));
                    }
                    server.execute(() -> {
                        net.minecraft.util.Hand hand = mainHand
                                ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
                        net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
                        if (!stack.isOf(kombuchamc.createsecurity.items.RegisterModItems.PLAYER_LIST)) return;
                        kombuchamc.createsecurity.items.PlayerListItem.writeData(stack, includeMode,
                                kombuchamc.createsecurity.items.PlayerListItem.sanitizeNames(names));
                    });
                });
    }

    public static void sendKeycardReaderFullSync(ServerPlayerEntity player) {
        java.util.List<kombuchamc.createsecurity.block.KeycardReaderStore.Entry> entries =
                kombuchamc.createsecurity.block.KeycardReaderStore.get(player.getServerWorld()).all();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entries.size());
        for (kombuchamc.createsecurity.block.KeycardReaderStore.Entry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeBoolean(entry.isArmed());
        }
        ServerPlayNetworking.send(player, KEYCARD_READER_SYNC, buf);
    }

    public static void broadcastKeycardReaderUpdate(ServerWorld world, BlockPos pos, boolean present,
                                                    boolean armed) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeBoolean(present);
            buf.writeBoolean(armed);
            ServerPlayNetworking.send(player, KEYCARD_READER_UPDATE, buf);
        }
    }
}

