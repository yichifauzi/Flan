package io.github.flemmli97.flan.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.flemmli97.flan.event.EntityInteractEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FrostWalkerEnchantment.class)
public abstract class FrostWalkerMixin {

    @ModifyExpressionValue(method = "onEntityMoved", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean freeze(boolean orig, LivingEntity entity, Level level, BlockPos originPos, @Local(ordinal = 1) BlockPos pos) {
        if (level instanceof ServerLevel && !EntityInteractEvents.canFrostwalkerFreeze((ServerLevel) level, pos, entity))
            return false;
        return orig;
    }
}
