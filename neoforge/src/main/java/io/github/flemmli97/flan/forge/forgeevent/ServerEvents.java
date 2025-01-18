package io.github.flemmli97.flan.forge.forgeevent;

import io.github.flemmli97.flan.commands.CommandClaim;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.event.PlayerEvents;
import io.github.flemmli97.flan.platform.integration.webmap.BluemapIntegration;
import io.github.flemmli97.flan.player.LogoutTracker;
import io.github.flemmli97.flan.player.PlayerDataHandler;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ServerEvents {

    public static void serverStart(ServerAboutToStartEvent event) {
        ConfigHandler.reloadConfigs();

        if (ModList.get().isLoaded("bluemap"))
            BluemapIntegration.reg(event.getServer());
    }

    public static void serverFinishLoad(ServerStartedEvent event) {
        PlayerDataHandler.deleteInactivePlayerData(event.getServer());
    }

    public static void commands(RegisterCommandsEvent event) {
        CommandClaim.register(event.getDispatcher(), event.getBuildContext(), false);
    }

    public static void savePlayer(PlayerEvent.SaveToFile event) {
        PlayerEvents.saveClaimData(event.getEntity());
    }

    public static void readPlayer(PlayerEvent.LoadFromFile event) {
        PlayerEvents.readClaimData(event.getEntity());
    }

    public static void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEvents.onLogout(event.getEntity());
    }

    public static void serverTick(ServerTickEvent.Pre event) {
        LogoutTracker.getInstance(event.getServer()).tick();
    }
}
