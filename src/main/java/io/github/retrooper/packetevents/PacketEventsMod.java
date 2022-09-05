package io.github.retrooper.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.forge.ForgePacketEventsBuilder;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.logging.Logger;

@Mod(PacketEventsMod.MOD_ID)
public class PacketEventsMod {

    public static final String MOD_ID = "packetevents";
    public static final Logger LOGGER = Logger.getLogger("PacketEvents");

    public PacketEventsMod(){
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::complete);
    }

    private void setup(FMLCommonSetupEvent event){
        // Ran after complete

        PacketEvents.setAPI(ForgePacketEventsBuilder.build(MOD_ID));
        PacketEvents.getAPI().getSettings().debug(true).bStats(true);
        PacketEvents.getAPI().load();
    }

    private void complete(FMLLoadCompleteEvent event){
        PacketEvents.getAPI().init();
    }
}
