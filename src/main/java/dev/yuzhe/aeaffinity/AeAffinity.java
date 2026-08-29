package dev.yuzhe.aeaffinity;

import appeng.api.networking.GridServices;
import dev.yuzhe.aeaffinity.command.AffinityCommands;
import dev.yuzhe.aeaffinity.config.AeAffinityConfig;
import dev.yuzhe.aeaffinity.grid.AffinityGridService;
import dev.yuzhe.aeaffinity.grid.IAffinityGridService;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AeAffinity.MOD_ID)
public final class AeAffinity {
    public static final String MOD_ID = "aeaffinity";

    public AeAffinity(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AeAffinityConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(AffinityCommands::register);
        GridServices.register(IAffinityGridService.class, AffinityGridService.class);
    }
}
