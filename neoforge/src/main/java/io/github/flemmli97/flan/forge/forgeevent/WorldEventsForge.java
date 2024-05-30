package io.github.flemmli97.flan.forge.forgeevent;

import io.github.flemmli97.flan.event.WorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public class WorldEventsForge {

    public static void modifyExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel)
            WorldEvents.modifyExplosion(event.getExplosion(), (ServerLevel) event.getLevel());
    }

    public static void preventMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || event.getSpawnType() != MobSpawnType.NATURAL)
            return;
        if (WorldEvents.preventMobSpawn((ServerLevel) event.getLevel(), event.getEntity()))
            event.setSpawnCancelled(true);
    }
}
