package io.github.flemmli97.flan.forge;

import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.api.permission.InteractionOverrideManager;
import io.github.flemmli97.flan.api.permission.PermissionManager;
import io.github.flemmli97.flan.forge.forgeevent.BlockInteractEventsForge;
import io.github.flemmli97.flan.forge.forgeevent.EntityInteractEventsForge;
import io.github.flemmli97.flan.forge.forgeevent.ItemInteractEventsForge;
import io.github.flemmli97.flan.forge.forgeevent.ServerEvents;
import io.github.flemmli97.flan.forge.forgeevent.WorldEventsForge;
import io.github.flemmli97.flan.platform.integration.webmap.DynmapIntegration;
import io.github.flemmli97.flan.scoreboard.ClaimCriterias;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@Mod(FlanForge.MODID)
public class FlanForge {

    public static final String MODID = "flan";

    public FlanForge() {
        Flan.ftbRanks = ModList.get().isLoaded("ftbranks");
        Flan.diceMCMoneySign = ModList.get().isLoaded("dicemcmm");
        Flan.ftbChunks = ModList.get().isLoaded("ftbchunks");
        Flan.mineColonies = ModList.get().isLoaded("minecolonies");
        Flan.impactor = ModList.get().isLoaded("impactor");

        IEventBus forge = NeoForge.EVENT_BUS;
        forge.addListener(WorldEventsForge::modifyExplosion);
        forge.addListener(WorldEventsForge::preventMobSpawn);
        forge.addListener(ItemInteractEventsForge::useItem);
        forge.addListener(EventPriority.HIGHEST, BlockInteractEventsForge::startBreakBlocks);
        forge.addListener(EventPriority.HIGHEST, BlockInteractEventsForge::breakBlocks);
        forge.addListener(EventPriority.HIGHEST, BlockInteractEventsForge::useBlocks);
        forge.addListener(EventPriority.HIGHEST, BlockInteractEventsForge::placeBlock);
        forge.addListener(EventPriority.HIGHEST, BlockInteractEventsForge::placeBlocks);
        forge.addListener(EntityInteractEventsForge::attackEntity);
        forge.addListener(EntityInteractEventsForge::useAtEntity);
        forge.addListener(EntityInteractEventsForge::useEntity);
        forge.addListener(EntityInteractEventsForge::projectileHit);
        forge.addListener(EntityInteractEventsForge::preventDamage);
        forge.addListener(EntityInteractEventsForge::xpAbsorb);
        forge.addListener(EntityInteractEventsForge::canDropItem);
        forge.addListener(EntityInteractEventsForge::mobGriefing);
        forge.addListener(EntityInteractEventsForge::entityLightningHit);

        forge.addListener(ServerEvents::serverStart);
        forge.addListener(ServerEvents::commands);
        forge.addListener(ServerEvents::savePlayer);
        forge.addListener(ServerEvents::readPlayer);
        forge.addListener(ServerEvents::serverFinishLoad);
        forge.addListener(ServerEvents::disconnect);
        forge.addListener(ServerEvents::serverTick);
        forge.addListener(this::addReloadListener);

        if (ModList.get().isLoaded("dynmap"))
            DynmapIntegration.reg();
        Flan.create = ModList.get().isLoaded("create");

        ClaimCriterias.init();
    }

    public void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(PermissionManager.INSTANCE);
        event.addListener(InteractionOverrideManager.INSTANCE);
    }
}