package io.github.flemmli97.flan.forge.platform.integration.currency;

import dicemc.money.MoneyMod;
import dicemc.money.storage.MoneyWSD;
import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.claim.ClaimUtils;
import io.github.flemmli97.flan.platform.integration.currency.CommandCurrency;
import io.github.flemmli97.flan.platform.integration.currency.CommonCurrency;
import io.github.flemmli97.flan.player.PlayerClaimData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

public class CommandCurrencyImpl implements CommandCurrency {

    @Override
    public boolean sellClaimBlocks(ServerPlayer player, int blocks, float value, Consumer<Component> message) {
        if (value == -1) {
            message.accept(ClaimUtils.translatedText("flan.sellDisabled", ChatFormatting.DARK_RED));
            return false;
        }
        int common = CommonCurrency.sell(player, blocks, value, message);
        if (common != -1)
            return common == 1;
        if (Flan.diceMCMoneySign) {
            PlayerClaimData data = PlayerClaimData.get(player);
            if (data.getAdditionalClaims() - Math.max(0, data.usedClaimBlocks() - data.getClaimBlocks()) < blocks) {
                message.accept(ClaimUtils.translatedText("flan.sellFail", ChatFormatting.DARK_RED));
                return false;
            }
            double price = blocks * value;
            MoneyWSD.get(player.serverLevel()).changeBalance(MoneyMod.AcctTypes.PLAYER.key, player.getUUID(), price);
            data.setAdditionalClaims(data.getAdditionalClaims() - blocks);
            message.accept(ClaimUtils.translatedText("flan.sellSuccess", blocks, price, ChatFormatting.GOLD));
            return true;
        }
        message.accept(ClaimUtils.translatedText("flan.currencyMissing", ChatFormatting.DARK_RED));
        return false;
    }

    @Override
    public boolean buyClaimBlocks(ServerPlayer player, int blocks, float value, Consumer<Component> message) {
        if (value == -1) {
            message.accept(ClaimUtils.translatedText("flan.buyDisabled", ChatFormatting.DARK_RED));
            return false;
        }
        int common = CommonCurrency.buy(player, blocks, value, message);
        if (common != -1)
            return common == 1;
        if (Flan.diceMCMoneySign) {
            UUID uuid = player.getUUID();
            MoneyWSD manager = MoneyWSD.get(player.serverLevel());
            double bal = manager.getBalance(MoneyMod.AcctTypes.PLAYER.key, uuid);
            double price = blocks * value;
            if (bal >= price) {
                PlayerClaimData data = PlayerClaimData.get(player);
                data.setAdditionalClaims(data.getAdditionalClaims() + blocks);
                manager.changeBalance(MoneyMod.AcctTypes.PLAYER.key, uuid, -price);
                message.accept(ClaimUtils.translatedText("flan.buySuccess", blocks, price, ChatFormatting.GOLD));
                return true;
            }
            message.accept(ClaimUtils.translatedText("flan.buyFail", ChatFormatting.DARK_RED));
            return false;
        }
        message.accept(ClaimUtils.translatedText("flan.currencyMissing", ChatFormatting.DARK_RED));
        return false;
    }
}
