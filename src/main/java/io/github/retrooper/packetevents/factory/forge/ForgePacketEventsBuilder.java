package io.github.retrooper.packetevents.factory.forge;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.InternalPacketListener;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.LogManager;
import com.github.retrooper.packetevents.util.reflection.ReflectionObject;
import io.github.retrooper.packetevents.handler.PacketDecoder;
import io.github.retrooper.packetevents.handler.PacketEncoder;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import io.netty.channel.Channel;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.IServerPlayNetHandler;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraftforge.common.MinecraftForge;
import org.antlr.v4.runtime.misc.NotNull;

import javax.annotation.Nullable;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ForgePacketEventsBuilder {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + '\u00A7' + "[0-9A-FK-ORX]");
    private static PacketEventsAPI<Minecraft> INSTANCE;

    public static void clearBuildCache() {
        INSTANCE = null;
    }

    public static PacketEventsAPI<Minecraft> build(String modId) {
        if (INSTANCE == null) {
            INSTANCE = buildNoCache(modId);
        }
        return INSTANCE;
    }

    public static PacketEventsAPI<Minecraft> build(String modId, PacketEventsSettings settings) {
        if (INSTANCE == null) {
            INSTANCE = buildNoCache(modId, settings);
        }
        return INSTANCE;
    }


    public static PacketEventsAPI<Minecraft> buildNoCache(String modId) {
        return buildNoCache(modId, new PacketEventsSettings());
    }

    public static PacketEventsAPI<Minecraft> buildNoCache(String modId, PacketEventsSettings inSettings) {
        return new PacketEventsAPI<Minecraft>() {
            private final PacketEventsSettings settings = inSettings;
            //TODO Implement platform version
            private final ProtocolManager protocolManager = new ProtocolManagerAbstract() {
                @Override
                public ProtocolVersion getPlatformVersion() {
                    return ProtocolVersion.UNKNOWN;
                }
            };
            private final ServerManager serverManager = new ServerManagerAbstract() {
                @Override
                public ServerVersion getVersion() {
                    //TODO Not perfect, as this is on the client! Might be inaccurate by a few patch versions.
                    System.out.println(Minecraft.getInstance().getLaunchedVersion() + " " + Minecraft.getInstance().getVersionType());
//                    if (VERSION == null) {
//                        int targetPV = Minecraft.getInstance().vers
//                        for (ServerVersion version : ServerVersion.reversedValues()) {
//                            if (version.getProtocolVersion() == targetPV) {
//                                VERSION = version;
//                            }
//                        }
//                    }
//                    return VERSION;

                    return ServerVersion.V_1_16_5;
                }
            };

            private final PlayerManagerAbstract playerManager = new PlayerManagerAbstract() {
                @Override
                public int getPing(@NotNull Object player) {
                    // TODO
                    return -1;
                }

                @Override
                public Object getChannel(@NotNull Object player) {
                    NetworkManager manager = ((ServerPlayerEntity) player).connection.getConnection();
                    return manager.channel();
                }
            };

            private final ChannelInjector injector = new ChannelInjector() {
                @Override
                public boolean isServerBound() {
                    return true;
                }

                @Override
                public void inject() {

                }

                @Override
                public void uninject() {

                }

                @Override
                public User getUser(Object ch) {
                    Channel channel = (Channel) ch;
                    PacketDecoder decoder = (PacketDecoder) channel.pipeline().get(PacketEvents.DECODER_NAME);
                    return decoder.user;
                }

                @Override
                public void changeConnectionState(Object ch, @Nullable ConnectionState packetState) {
                    Channel channel = (Channel) ch;
                    PacketDecoder decoder = (PacketDecoder) channel.pipeline().get(PacketEvents.DECODER_NAME);
                    decoder.user.setConnectionState(packetState);
                }

                @Override
                public void updateUser(Object ch, User user) {
                    Channel channel = (Channel) ch;
                    PacketDecoder decoder = (PacketDecoder) channel.pipeline().get(PacketEvents.DECODER_NAME);
                    decoder.user = user;
                    PacketEncoder encoder = (PacketEncoder) channel.pipeline().get(PacketEvents.ENCODER_NAME);
                    encoder.user = user;
                }

                @Override
                public void setPlayer(Object ch, Object player) {
                    Channel channel = (Channel) ch;
                    PacketDecoder decoder = (PacketDecoder) channel.pipeline().get(PacketEvents.DECODER_NAME);
                    decoder.player = (ServerPlayerEntity) player;

                    PacketEncoder encoder = (PacketEncoder) channel.pipeline().get(PacketEvents.ENCODER_NAME);
                    encoder.player = (ServerPlayerEntity) player;
                }

                @Override
                public boolean hasPlayer(Object ch) {
                    PacketDecoder decoder = (PacketDecoder) ((Channel) ch).pipeline().get(PacketEvents.DECODER_NAME);
                    return decoder != null && decoder.player != null;
                }
            };
            private final NettyManager nettyManager = new NettyManagerImpl();
            private final LogManager logManager = new LogManager() {
                @Override
                protected void log(Level level, @Nullable NamedTextColor color, String message) {
                    //First we must strip away the color codes that might be in this message
                    message = STRIP_COLOR_PATTERN.matcher(message).replaceAll("");
                    System.out.println(message);
                }
            };
            private boolean loaded;
            private boolean initialized;

            @Override
            public void load() {
                if (!loaded) {
                    final String id = modId.toLowerCase();
                    //Resolve server version and cache
                    PacketEvents.IDENTIFIER = "pe-" + id;
                    PacketEvents.ENCODER_NAME = "pe-encoder-" + id;
                    PacketEvents.DECODER_NAME = "pe-decoder-" + id;
                    PacketEvents.CONNECTION_HANDLER_NAME = "pe-connection-handler-" + id;
                    PacketEvents.SERVER_CHANNEL_HANDLER_NAME = "pe-connection-initializer-" + id;

                    injector.inject();

                    loaded = true;

                    //Register internal packet listener (should be the first listener)
                    //This listener doesn't do any modifications to the packets, just reads data
                    getEventManager().registerListener(new InternalPacketListener());
                    getEventManager().registerListener(new SimplePacketListenerAbstract() {
                        @Override
                        public void onPacketPlaySend(PacketPlaySendEvent event) {
                            if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
                                System.out.println("Is player null? " + (Minecraft.getInstance().player != null));
                                PacketEvents.getAPI().getInjector().setPlayer(event.getChannel(),
                                        Minecraft.getInstance().player);
                            }
                        }
                    });
                }
            }

            @Override
            public boolean isLoaded() {
                return loaded;
            }

            @Override
            public void init() {
                //Load if we haven't loaded already
                load();
                if (!initialized) {
                    if (settings.shouldCheckForUpdates()) {
                        getUpdateChecker().handleUpdateCheck();
                    }

                    if (settings.isbStatsEnabled()) {
                        //TODO Cross-platform metrics?
                    }

                    PacketType.Play.Client.load();
                    PacketType.Play.Server.load();
                    initialized = true;
                }
            }

            @Override
            public boolean isInitialized() {
                return initialized;
            }

            @Override
            public void terminate() {
                if (initialized) {
                    //Eject the injector if needed(depends on the injector implementation)
                    injector.uninject();
                    //Unregister all our listeners
                    getEventManager().unregisterAllListeners();
                    initialized = false;
                }
            }

            @Override
            public Minecraft getPlugin() {
                return Minecraft.getInstance();
            }

            @Override
            public ProtocolManager getProtocolManager() {
                return protocolManager;
            }

            @Override
            public ServerManager getServerManager() {
                return serverManager;
            }

            @Override
            public LogManager getLogManager() {
                return logManager;
            }

            @Override
            public PlayerManager getPlayerManager() {
                return playerManager;
            }

            @Override
            public ChannelInjector getInjector() {
                return injector;
            }

            @Override
            public PacketEventsSettings getSettings() {
                return settings;
            }

            @Override
            public NettyManager getNettyManager() {
                return nettyManager;
            }
        };
    }
}
