package io.github.flemmli97.flan.event;

import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.api.permission.ObjectToPermissionMap;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.claim.PermHelper;
import io.github.flemmli97.flan.player.LogoutTracker;
import io.github.flemmli97.flan.player.PlayerClaimData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.MossBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NetherForestVegetationConfig;
import net.minecraft.world.level.levelgen.feature.configurations.TwistingVinesConfig;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;

public class PlayerEvents {

    public static void saveClaimData(Player player) {
        if (player instanceof ServerPlayer)
            PlayerClaimData.get((ServerPlayer) player).save(player.getServer());
    }

    public static void readClaimData(Player player) {
        if (player instanceof ServerPlayer)
            PlayerClaimData.get((ServerPlayer) player).read(player.getServer());
    }

    public static void onLogout(Player player) {
        if (player.getServer() != null)
            LogoutTracker.getInstance(player.getServer()).track(player.getUUID());
    }

    public static boolean growBonemeal(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            BlockState state = serverPlayer.level().getBlockState(context.getClickedPos());
            BlockPos.MutableBlockPos pos = context.getClickedPos().mutable();
            ResourceLocation perm = ObjectToPermissionMap.getFromItem(context.getItemInHand());
            /**
             * {@link ItemInteractEvents#onItemUseBlock} handles this case already.
             * Sadly need to check again. In case its used in a claim. Less expensive than aoe check
             */
            if (perm != null && !ClaimStorage.get(serverPlayer.serverLevel()).getForPermissionCheck(pos).canInteract(serverPlayer, perm, pos, false))
                return false;
            int range = 0;
            Registry<ConfiguredFeature<?, ?>> registry = serverPlayer.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
            if (state.getBlock() instanceof MossBlock) {
                VegetationPatchConfiguration cfg = featureRange(registry, CaveFeatures.MOSS_PATCH_BONEMEAL, VegetationPatchConfiguration.class);
                if (cfg != null) {
                    range = cfg.xzRadius.getMaxValue() + 1;
                    pos.set(pos.getX(), pos.getY() + cfg.verticalRange + 1, pos.getZ());
                }
            } else if (state.getBlock() instanceof GrassBlock) {
                range = 4;
            } else if (state.is(Blocks.CRIMSON_NYLIUM)) {
                NetherForestVegetationConfig cfg = featureRange(registry, NetherFeatures.CRIMSON_FOREST_VEGETATION_BONEMEAL, NetherForestVegetationConfig.class);
                if (cfg != null) {
                    range = cfg.spreadWidth;
                    pos.set(pos.getX(), pos.getY() + cfg.spreadHeight + 1, pos.getZ());
                }
            } else if (state.is(Blocks.WARPED_NYLIUM)) {
                NetherForestVegetationConfig cfg = featureRange(registry, NetherFeatures.WARPED_FOREST_VEGETATION_BONEMEAL, NetherForestVegetationConfig.class);
                NetherForestVegetationConfig cfg2 = featureRange(registry, NetherFeatures.NETHER_SPROUTS_BONEMEAL, NetherForestVegetationConfig.class);
                TwistingVinesConfig cfg3 = featureRange(registry, NetherFeatures.TWISTING_VINES_BONEMEAL, TwistingVinesConfig.class);
                int w1 = cfg == null ? 0 : cfg.spreadWidth;
                int w2 = cfg2 == null ? 0 : cfg2.spreadWidth;
                int w3 = cfg3 == null ? 0 : cfg3.spreadWidth();
                int h1 = cfg == null ? 0 : cfg.spreadHeight;
                int h2 = cfg2 == null ? 0 : cfg2.spreadHeight;
                int h3 = cfg3 == null ? 0 : cfg3.spreadHeight();
                range = Math.max(Math.max(w1, w2), w3);
                int y = Math.max(Math.max(h1, h2), h3);
                pos.set(pos.getX(), pos.getY() + y + 1, pos.getZ());
            }
            if (range > 0 && perm != null && !ClaimStorage.get(serverPlayer.serverLevel()).canInteract(pos, range, serverPlayer, perm, false)) {
                serverPlayer.displayClientMessage(PermHelper.translatedText("flan.tooCloseClaim", ChatFormatting.DARK_RED), true);
                return true;
            }
        }
        return false;
    }

    public static float canSpawnFromPlayer(Entity entity, float old) {
        BlockPos pos;
        if (entity instanceof ServerPlayer player &&
                !ClaimStorage.get(player.serverLevel()).getForPermissionCheck(pos = player.blockPosition()).canInteract(player, BuiltinPermission.PLAYERMOBSPAWN, pos, false))
            return -1;
        return old;
    }

    public static boolean canWardenSpawnTrigger(BlockPos pos, ServerPlayer player) {
        return ClaimStorage.get(player.serverLevel()).getForPermissionCheck(pos).canInteract(player, BuiltinPermission.PLAYERMOBSPAWN, pos, false);
    }

    public static boolean canSculkTrigger(BlockPos pos, ServerPlayer player) {
        return ClaimStorage.get(player.serverLevel()).getForPermissionCheck(pos).canInteract(player, BuiltinPermission.SCULK, pos, false);
    }

    @SuppressWarnings("unchecked")
    public static <T extends FeatureConfiguration> T featureRange(Registry<ConfiguredFeature<?, ?>> registry, ResourceKey<ConfiguredFeature<?, ?>> key, Class<T> clss) {
        return registry.getHolder(key).map(r -> {
            if (clss.isInstance(r.value().config()))
                return (T) r.value().config();
            return null;
        }).orElse(null);
    }
}
