package io.github.flemmli97.flan.mixin;

import io.github.flemmli97.flan.event.ItemInteractEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "useOn", at = @At(value = "HEAD"), cancellable = true)
    private void blockUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> info) {
        InteractionResult result = ItemInteractEvents.onItemUseBlock(context);
        if (result != InteractionResult.PASS) {
            info.setReturnValue(result);
            info.cancel();
        }
    }
}