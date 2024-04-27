package io.github.flemmli97.flan.data;

import io.github.flemmli97.flan.Flan;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = Flan.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataEvent {

    @SubscribeEvent
    public static void data(GatherDataEvent event) {
        DataGenerator data = event.getGenerator();
        data.addProvider(event.includeServer(), new PermissionGen(data.getPackOutput(), event.getLookupProvider()));
    }

}
