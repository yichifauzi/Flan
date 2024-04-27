package io.github.flemmli97.flan.forge.platform;

import io.github.flemmli97.flan.platform.CrossPlatformStuff;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import java.nio.file.Path;

public class CrossPlatformStuffImpl implements CrossPlatformStuff {

    @Override
    public Path configPath() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isDataGen() {
        return DatagenModLoader.isRunningDataGen();
    }

    @Override
    public boolean isModLoaded(String mod) {
        return ModList.get().isLoaded(mod);
    }

    @Override
    public boolean isInventoryTile(BlockEntity blockEntity) {
        return blockEntity instanceof Container || blockEntity instanceof WorldlyContainerHolder ||
                blockEntity.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(),
                        null) != null;
    }

    @Override
    public boolean blockDataContains(CompoundTag nbt, String tag) {
        return nbt.contains(tag) || nbt.getCompound("ForgeData").contains(tag);
    }
}
