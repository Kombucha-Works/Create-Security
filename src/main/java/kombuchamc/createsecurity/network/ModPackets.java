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

    public static final Identifier TURRET_SLIDER =
            new Identifier(CreateSecurity.MOD_ID, "turret_slider");

    public static final Identifier PLAYER_LIST_UPDATE =
            new Identifier(CreateSecurity.MOD_ID, "player_list_update");

    public static final Identifier KEYCARD_READER_SYNC =
            new Identifier(CreateSecurity.MOD_ID, "keycard_reader_sync");

    public static final Identifier KEYCARD_READER_UPDATE =
            new Identifier(CreateSecurity.MOD_ID, "keycard_reader_update");

    public static final Identifier OPEN_CODE_LOCK =
            new Identifier(CreateSecurity.MOD_ID, "open_code_lock");

    public static final Identifier CODE_LOCK_SUBMIT =
            new Identifier(CreateSecurity.MOD_ID, "code_lock_submit");

    public static final Identifier OBSIDIAN_OWNER_SYNC =
            new Identifier(CreateSecurity.MOD_ID, "obsidian_owner_sync");

    public static final Identifier OBSIDIAN_OWNER_UPDATE =
            new Identifier(CreateSecurity.MOD_ID, "obsidian_owner_update");

    public static void sendToggleDetectionPacket(BlockPos pos, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(enabled);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(TOGGLE_DETECTION, buf);
    }

    public static void sendTurretSlider(int syncId, int slider, int value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(syncId);
        buf.writeVarInt(slider);
        buf.writeVarInt(value);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(TURRET_SLIDER, buf);
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

        ServerPlayNetworking.registerGlobalReceiver(TURRET_SLIDER,
                (server, player, handler, buf, responseSender) -> {
                    int syncId = buf.readVarInt();
                    int slider = buf.readVarInt();
                    int value = buf.readVarInt();
                    server.execute(() -> {
                        if (player.currentScreenHandler
                                        instanceof kombuchamc.createsecurity.screen.TurretScreenHandler turret
                                && turret.syncId == syncId) {
                            turret.setSlider(slider, value);
                        }
                    });
                });

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

        registerCodeLockReceiver();
    }

    private static void registerCodeLockReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(CODE_LOCK_SUBMIT,
                (server, player, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    int code = buf.readInt();
                    server.execute(() -> kombuchamc.createsecurity.block.CodeLock
                            .onCodeSubmitted(player, pos, code));
                });
    }

    public static void sendKeycardReaderFullSync(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        java.util.List<kombuchamc.createsecurity.block.KeycardReaderStore.Entry> entries =
                kombuchamc.createsecurity.block.KeycardReaderStore.get(world).all();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entries.size());
        for (kombuchamc.createsecurity.block.KeycardReaderStore.Entry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeBoolean(entry.isArmed());
            buf.writeByte(entry.kind().ordinal());
            buf.writeBoolean(kombuchamc.createsecurity.block.KeycardReaderLights
                    .isUnlocked(world, entry.pos()));
        }
        ServerPlayNetworking.send(player, KEYCARD_READER_SYNC, buf);
    }

    public static void broadcastKeycardReaderUpdate(ServerWorld world, BlockPos pos, boolean present,
                                                    boolean armed,
                                                    kombuchamc.createsecurity.block.LockKind kind) {
        broadcastKeycardReaderUpdate(world, pos, present, armed, kind, false);
    }

    public static void broadcastKeycardReaderUpdate(ServerWorld world, BlockPos pos, boolean present,
                                                    boolean armed,
                                                    kombuchamc.createsecurity.block.LockKind kind,
                                                    boolean unlocked) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeBoolean(present);
            buf.writeBoolean(armed);
            buf.writeByte(kind.ordinal());
            buf.writeBoolean(unlocked);
            ServerPlayNetworking.send(player, KEYCARD_READER_UPDATE, buf);
        }
    }

    public static void sendOpenCodeLock(ServerPlayerEntity player, BlockPos pos, boolean setting) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(setting);
        ServerPlayNetworking.send(player, OPEN_CODE_LOCK, buf);
    }

    public static void sendObsidianOwnerFullSync(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        kombuchamc.createsecurity.block.obsidian.ObsidianOwnerStore store =
                kombuchamc.createsecurity.block.obsidian.ObsidianOwnerStore.get(world);
        java.util.Set<BlockPos> mine = new java.util.HashSet<>(store.ownedBy(player.getUuid()));
        java.util.List<BlockPos> all = store.allPositions();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(kombuchamc.createsecurity.config.CSConfigs.canBypassOwner(player));
        buf.writeVarInt(all.size());
        for (BlockPos pos : all) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(mine.contains(pos));
        }
        ServerPlayNetworking.send(player, OBSIDIAN_OWNER_SYNC, buf);
    }

    public static void broadcastObsidianOwner(ServerWorld world, BlockPos pos, boolean present,
                                              @Nullable java.util.UUID owner) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeBoolean(present);
            buf.writeBoolean(owner != null && owner.equals(player.getUuid()));
            ServerPlayNetworking.send(player, OBSIDIAN_OWNER_UPDATE, buf);
        }
    }
}
