package io.github.flemmli97.flan.gui;

import com.mojang.authlib.properties.PropertyMap;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimUtils;
import io.github.flemmli97.flan.gui.inv.SeparateInv;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;

public class GroupPlayerScreenHandler extends ServerOnlyScreenHandler<ClaimGroup> {

    private final Claim claim;
    private final String group;
    private boolean removeMode;

    private GroupPlayerScreenHandler(int syncId, Inventory playerInventory, Claim claim, String group) {
        super(syncId, playerInventory, 6, new ClaimGroup() {

            @Override
            public Claim getClaim() {
                return claim;
            }

            @Override
            public String getGroup() {
                return group;
            }
        });
        this.claim = claim;
        this.group = group;
    }

    public static void openPlayerGroupMenu(Player player, Claim claim, String group) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                return new GroupPlayerScreenHandler(syncId, inv, claim, group);
            }

            @Override
            public Component getDisplayName() {
                return ClaimUtils.translatedText("flan.screenGroupPlayers", group);
            }
        };
        player.openMenu(fac);
    }

    @Override
    protected void fillInventoryWith(Player player, SeparateInv inv, ClaimGroup additionalData) {
        Claim claim = additionalData.getClaim();
        List<String> players = claim.playersFromGroup(player.getServer(), additionalData.getGroup());
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack close = new ItemStack(Items.TNT);
                close.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText("flan.screenBack", ChatFormatting.DARK_RED));
                inv.updateStack(i, close);
            } else if (i == 3) {
                ItemStack stack = new ItemStack(Items.ANVIL);
                stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText("flan.screenAdd", ChatFormatting.DARK_GREEN));
                inv.updateStack(i, stack);
            } else if (i == 4) {
                ItemStack stack = new ItemStack(Items.REDSTONE_BLOCK);
                stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText("flan.screenRemoveMode", this.removeMode ? ServerScreenHelper.coloredGuiText("flan.screenTrue") : ServerScreenHelper.coloredGuiText("flan.screenFalse"), ChatFormatting.DARK_RED));
                inv.updateStack(i, stack);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                inv.updateStack(i, ServerScreenHelper.emptyFiller());
            else {
                int row = i / 9 - 1;
                int id = (i % 9) + row * 7 - 1;
                if (id < players.size()) {
                    ItemStack group = new ItemStack(Items.PLAYER_HEAD);
                    group.set(DataComponents.PROFILE, new ResolvableProfile(Optional.of(players.get(id)), Optional.empty(), new PropertyMap()));
                    inv.updateStack(i, group);
                }
            }
        }
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return slot == 0 || slot == 3 || slot == 4 || (slot < 45 && slot > 8 && slot % 9 != 0 && slot % 9 != 8);
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {
        if (index == 0) {
            player.closeContainer();
            player.getServer().execute(() -> GroupScreenHandler.openGroupMenu(player, this.claim));
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        if (index == 3) {
            player.closeContainer();
            player.getServer().execute(() -> StringResultScreenHandler.createNewStringResult(player, (s) -> {
                boolean fl = player.getServer().getProfileCache().get(s).map(prof -> this.claim.setPlayerGroup(prof.getId(), this.group, false)).orElse(true);
                player.closeContainer();
                player.getServer().execute(() -> GroupPlayerScreenHandler.openPlayerGroupMenu(player, this.claim, this.group));
                if (fl)
                    ServerScreenHelper.playSongToPlayer(player, SoundEvents.ANVIL_USE, 1, 1f);
                else {
                    player.displayClientMessage(ClaimUtils.translatedText("flan.playerGroupAddFail", ChatFormatting.RED), false);
                    ServerScreenHelper.playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
                }
            }, () -> {
                player.closeContainer();
                player.getServer().execute(() -> GroupPlayerScreenHandler.openPlayerGroupMenu(player, this.claim, this.group));
                ServerScreenHelper.playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
            }));
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        if (index == 4) {
            this.removeMode = !this.removeMode;
            ItemStack stack = new ItemStack(Items.REDSTONE_BLOCK);
            stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText("flan.screenRemoveMode", this.removeMode ? ServerScreenHelper.coloredGuiText("flan.screenTrue") : ServerScreenHelper.coloredGuiText("flan.screenFalse"), ChatFormatting.DARK_RED));
            slot.set(stack);
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            if (this.removeMode && profile != null && profile.isResolved()) {
                this.claim.setPlayerGroup(profile.gameProfile().getId(), null, false);
                slot.set(ItemStack.EMPTY);
                ServerScreenHelper.playSongToPlayer(player, SoundEvents.BAT_DEATH, 1, 1f);
            }
        }
        return false;
    }
}
