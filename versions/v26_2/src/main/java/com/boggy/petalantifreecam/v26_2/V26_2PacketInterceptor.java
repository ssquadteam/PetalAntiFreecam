package com.boggy.petalantifreecam.v26_2;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.nms.PacketInterceptor;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class V26_2PacketInterceptor implements PacketInterceptor {

    static final String HANDLER_NAME = "petal-antifreecam";

    private final Plugin plugin;
    private final PlayerVisibilityManager visibility;
    private final ConfigurationManager configuration;
    private final ChunkPacketMasker chunkMasker = new ChunkPacketMasker();
    private final Map<UUID, Channel> injected = new ConcurrentHashMap<>();

    V26_2PacketInterceptor(Plugin plugin, PlayerVisibilityManager visibility, ConfigurationManager configuration) {
        this.plugin = plugin;
        this.visibility = visibility;
        this.configuration = configuration;
    }

    @Override
    public void inject(Player player) {
        Channel channel = channel(player);
        if (channel == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Channel previous = injected.put(playerId, channel);
        if (previous != null && previous != channel) {
            removeHandler(previous);
        }
        runOnEventLoop(channel, () -> addHandler(channel, playerId));
    }

    @Override
    public void uninject(Player player) {
        Channel channel = injected.remove(player.getUniqueId());
        if (channel == null) {
            channel = channel(player);
        }
        if (channel != null) {
            removeHandler(channel);
        }
    }

    @Override
    public void uninjectAll() {
        for (Channel channel : injected.values()) {
            removeHandler(channel);
        }
        injected.clear();
    }

    private void addHandler(Channel channel, UUID playerId) {
        if (!channel.isOpen()) {
            return;
        }
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
        if (channel.pipeline().get("packet_handler") == null) {
            return;
        }
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new MaskingHandler(playerId));
    }

    private static void removeHandler(Channel channel) {
        runOnEventLoop(channel, () -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        });
    }

    private static void runOnEventLoop(Channel channel, Runnable action) {
        if (channel.eventLoop().inEventLoop()) {
            action.run();
            return;
        }
        channel.eventLoop().execute(action);
    }

    private static Channel channel(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return null;
        }
        Connection connection = craftPlayer.getHandle().connection.connection;
        return connection.channel;
    }

    private final class MaskingHandler extends ChannelDuplexHandler {

        private final UUID playerId;

        private MaskingHandler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (isWorldPacket(msg) && visibility.isMasking(playerId)) {
                Object rewritten = rewriteSafe(msg);
                if (rewritten == null) {
                    promise.setSuccess();
                    return;
                }
                msg = rewritten;
            }
            ctx.write(msg, promise);
        }

        private Object rewriteSafe(Object msg) {
            try {
                Player player = plugin.getServer().getPlayer(playerId);
                if (!(player instanceof CraftPlayer craftPlayer)) {
                    return msg;
                }
                return rewrite(msg, craftPlayer.getHandle(), configuration.current().hideBlocksBelowY());
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to mask outbound packet: " + exception.getMessage());
                return msg;
            }
        }

        private Object rewrite(Object msg, ServerPlayer serverPlayer, int hideBlocksBelowY) {
            if (msg instanceof ClientboundLevelChunkWithLightPacket packet) {
                return chunkMasker.mask(packet, serverPlayer, hideBlocksBelowY);
            }
            if (msg instanceof ClientboundBlockUpdatePacket
                    || msg instanceof ClientboundSectionBlocksUpdatePacket
                    || msg instanceof ClientboundBlockEventPacket) {
                return BlockPacketFilter.filter(msg, hideBlocksBelowY);
            }
            if (msg instanceof ClientboundBundlePacket bundle) {
                return rewriteBundle(bundle, serverPlayer, hideBlocksBelowY);
            }
            return msg;
        }

        private Object rewriteBundle(
                ClientboundBundlePacket bundle,
                ServerPlayer serverPlayer,
                int hideBlocksBelowY
        ) {
            List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> rewritten = null;
            int index = 0;
            for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> subPacket : bundle.subPackets()) {
                Object next = rewrite(subPacket, serverPlayer, hideBlocksBelowY);
                if (next != subPacket || rewritten != null) {
                    if (rewritten == null) {
                        rewritten = new ArrayList<>();
                        int copyIndex = 0;
                        for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> previous : bundle.subPackets()) {
                            if (copyIndex++ >= index) {
                                break;
                            }
                            rewritten.add(previous);
                        }
                    }
                    if (next != null) {
                        @SuppressWarnings("unchecked")
                        Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet =
                                (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) next;
                        rewritten.add(packet);
                    }
                }
                index++;
            }
            if (rewritten == null) {
                return bundle;
            }
            if (rewritten.isEmpty()) {
                return null;
            }
            return new ClientboundBundlePacket(rewritten);
        }
    }

    private static boolean isWorldPacket(Object msg) {
        return msg instanceof ClientboundLevelChunkWithLightPacket
                || msg instanceof ClientboundBlockUpdatePacket
                || msg instanceof ClientboundSectionBlocksUpdatePacket
                || msg instanceof ClientboundBlockEventPacket
                || msg instanceof ClientboundBundlePacket;
    }
}
