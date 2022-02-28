package io.github.flemmli97.flan.integration.currency.fabric;

import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.claim.PermHelper;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.player.PlayerClaimData;
import io.github.gunpowder.api.GunpowderMod;
import io.github.gunpowder.api.module.currency.dataholders.StoredBalance;
import io.github.gunpowder.api.module.currency.modelhandlers.BalanceHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.util.function.Consumer;

public class CommandCurrencyImpl {

    public static boolean sellClaimBlocks(ServerPlayerEntity player, int blocks, float value, Consumer<Text> message) {
        if (value == -1) {
            message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("sellDisabled"), Formatting.DARK_RED));
            return false;
        }
        if (Flan.gunpowder) {
            PlayerClaimData data = PlayerClaimData.get(player);
            if (data.getAdditionalClaims() - Math.max(0, data.usedClaimBlocks() - data.getClaimBlocks()) < blocks) {
                message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("sellFail"), Formatting.DARK_RED));
                return false;
            }
            StoredBalance bal = GunpowderMod.getInstance().getRegistry().getModelHandler(BalanceHandler.class).getUser(player.getUuid());
            BigDecimal price = BigDecimal.valueOf(blocks * value);
            bal.setBalance(bal.getBalance().add(price));
            GunpowderMod.getInstance().getRegistry().getModelHandler(BalanceHandler.class).updateUser(bal);
            data.setAdditionalClaims(data.getAdditionalClaims() - blocks);
            message.accept(PermHelper.simpleColoredText(String.format(ConfigHandler.langManager.get("sellSuccess"), blocks, price), Formatting.GOLD));
            return true;
        }
        message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("currencyMissing"), Formatting.DARK_RED));
        return false;
    }

    public static boolean buyClaimBlocks(ServerPlayerEntity player, int blocks, float value, Consumer<Text> message) {
        if (value == -1) {
            message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("buyDisabled"), Formatting.DARK_RED));
            return false;
        }
        if (Flan.gunpowder) {
            StoredBalance bal = GunpowderMod.getInstance().getRegistry().getModelHandler(BalanceHandler.class).getUser(player.getUuid());
            BigDecimal price = BigDecimal.valueOf(Math.max(0, blocks * value));
            if (bal.getBalance().compareTo(price) >= 0) {
                PlayerClaimData data = PlayerClaimData.get(player);
                data.setAdditionalClaims(data.getAdditionalClaims() + blocks);
                bal.setBalance(bal.getBalance().subtract(price));
                GunpowderMod.getInstance().getRegistry().getModelHandler(BalanceHandler.class).updateUser(bal);
                message.accept(PermHelper.simpleColoredText(String.format(ConfigHandler.langManager.get("buySuccess"), blocks, price), Formatting.GOLD));
                return true;
            }
            message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("buyFail"), Formatting.DARK_RED));
            return false;
        }
        message.accept(PermHelper.simpleColoredText(ConfigHandler.langManager.get("currencyMissing"), Formatting.DARK_RED));
        return false;
    }
}