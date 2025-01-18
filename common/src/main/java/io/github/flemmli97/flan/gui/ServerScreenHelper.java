package io.github.flemmli97.flan.gui;

import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.api.permission.ClaimPermission;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.PermHelper;
import io.github.flemmli97.flan.config.Config;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.player.PlayerClaimData;
import io.github.flemmli97.linguabib.api.LanguageAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerScreenHelper {

    public static final String PERMISSION_KEY = Flan.MODID + ".permission";

    public static ItemStack emptyFiller() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, PermHelper.translatedText(""));
        return stack;
    }

    public static ItemStack fromPermission(Claim claim, ServerPlayer player, ClaimPermission perm, String group) {
        ItemStack stack = perm.getItem();
        stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(perm.translationKey(), ChatFormatting.GOLD));
        List<Component> lore = new ArrayList<>();
        for (String pdesc : LanguageAPI.getFormattedKeys(player, perm.translationKeyDescription())) {
            Component trans = ServerScreenHelper.coloredGuiText(pdesc, ChatFormatting.YELLOW);
            lore.add(trans);
        }
        Config.GlobalType global = ConfigHandler.CONFIG.getGlobal(claim.getLevel(), perm.getId());
        if (!claim.isAdminClaim() && !global.canModify()) {
            Component text = ServerScreenHelper.coloredGuiText("flan.screenUneditable", ChatFormatting.DARK_RED);
            lore.add(text);
            String permFlag = global.getValue() ? "flan.screenTrue" : "flan.screenFalse";
            Component text2 = ServerScreenHelper.coloredGuiText("flan.screenEnableText", ServerScreenHelper.coloredGuiText(permFlag), permFlag.equals("flan.screenTrue") ? ChatFormatting.GREEN : ChatFormatting.RED);
            lore.add(text2);
        } else {
            String permFlag;
            if (group == null) {
                if (claim.parentClaim() == null)
                    permFlag = claim.permEnabled(perm.getId()) == 1 ? "flan.screenTrue" : "flan.screenFalse";
                else {
                    permFlag = switch (claim.permEnabled(perm.getId())) {
                        case -1 -> "flan.screenDefault";
                        case 1 -> "flan.screenTrue";
                        default -> "flan.screenFalse";
                    };
                }
            } else {
                permFlag = switch (claim.groupHasPerm(group, perm.getId())) {
                    case -1 -> "flan.screenDefault";
                    case 1 -> "flan.screenTrue";
                    default -> "flan.screenFalse";
                };
            }
            Component text = ServerScreenHelper.coloredGuiText("flan.screenEnableText", ServerScreenHelper.coloredGuiText(permFlag), permFlag.equals("flan.screenTrue") ? ChatFormatting.GREEN : ChatFormatting.RED);
            lore.add(text);
        }
        addLore(stack, lore);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PERMISSION_KEY, perm.getId().toString()));
        return stack;
    }

    public static ItemStack getFromPersonal(ServerPlayer player, ClaimPermission perm, String group) {
        ItemStack stack = perm.getItem();
        stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(perm.translationKey(), ChatFormatting.GOLD));
        List<Component> lore = new ArrayList<>();
        for (String pdesc : LanguageAPI.getFormattedKeys(player, perm.translationKeyDescription())) {
            Component trans = ServerScreenHelper.coloredGuiText(pdesc, ChatFormatting.YELLOW);
            lore.add(trans);
        }
        Config.GlobalType global = ConfigHandler.CONFIG.getGlobal(player.serverLevel(), perm.getId());
        if (!global.canModify()) {
            Component text = ServerScreenHelper.coloredGuiText("flan.screenUneditable", ChatFormatting.DARK_RED);
            lore.add(text);
            boolean permFlag = global.getValue();
            Component text2 = ServerScreenHelper.coloredGuiText("flan.screenEnableText", ServerScreenHelper.coloredGuiText(permFlag ? "flan.screenTrue" : "flan.screenFalse"), permFlag ? ChatFormatting.GREEN : ChatFormatting.RED);
            lore.add(text2);
        } else {
            String permFlag;
            Map<ResourceLocation, Boolean> map = PlayerClaimData.get(player).playerDefaultGroups().getOrDefault(group, new HashMap<>());
            if (map.containsKey(perm.getId()))
                permFlag = map.get(perm.getId()) ? "flan.screenTrue" : "flan.screenFalse";
            else
                permFlag = "flan.screenDefault";
            Component text = ServerScreenHelper.coloredGuiText("flan.screenEnableText", ServerScreenHelper.coloredGuiText(permFlag), permFlag.equals("flan.screenTrue") ? ChatFormatting.GREEN : ChatFormatting.RED);
            lore.add(text);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PERMISSION_KEY, perm.getId().toString()));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public static void playSongToPlayer(ServerPlayer player, SoundEvent event, float vol, float pitch) {
        player.connection.send(
                new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, player.level().getRandom().nextLong()));
    }

    public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch) {
        player.connection.send(
                new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch, player.level().getRandom().nextLong()));
    }

    public static MutableComponent coloredGuiText(String key, Object... compArgs) {
        List<ChatFormatting> formattings = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Object obj : compArgs) {
            if (obj instanceof ChatFormatting formatting)
                formattings.add(formatting);
            else
                args.add(obj);
        }
        return Component.translatable(key,
                args.toArray()).setStyle(Style.EMPTY.withItalic(false).applyFormats(formattings.toArray(ChatFormatting[]::new)));
    }

    public static void addLore(ItemStack stack, Component text) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(text)));
    }

    public static void addLore(ItemStack stack, List<Component> texts) {
        stack.set(DataComponents.LORE, new ItemLore(texts));
    }
}
