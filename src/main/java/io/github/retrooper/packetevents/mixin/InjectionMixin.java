package io.github.retrooper.packetevents.mixin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import io.github.retrooper.packetevents.PacketEventsMod;
import io.github.retrooper.packetevents.handler.PacketDecoder;
import io.github.retrooper.packetevents.handler.PacketEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class InjectionMixin {

    @Inject(method = "channelActive", at = @At("RETURN"))
    private void channelActive(ChannelHandlerContext ctx, CallbackInfo info) throws Exception {
        Channel channel = ctx.channel();

        User user = new User(channel, ConnectionState.HANDSHAKING, ClientVersion.getLatest(),
                new UserProfile(null, null));
        ProtocolManager.USERS.put(channel, user);
        PacketDecoder decoder = new PacketDecoder(user);
        PacketEncoder encoder = new PacketEncoder(user);
        if(channel.pipeline().names().size() < 2){
            channel.pipeline().addFirst(PacketEvents.DECODER_NAME, decoder);
            channel.pipeline().addLast(PacketEvents.ENCODER_NAME, encoder);
        }else{
            channel.pipeline().addAfter("splitter", PacketEvents.DECODER_NAME, decoder);
            channel.pipeline().addAfter("prepender", PacketEvents.ENCODER_NAME, encoder);
        }
        PacketEventsMod.LOGGER.info("Pipeline: " + ChannelHelper.pipelineHandlerNamesAsString(channel));
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void channelInactive(ChannelHandlerContext ctx, CallbackInfo info) {
        PacketEventsMod.LOGGER.info("Disconnected!");
    }
}
