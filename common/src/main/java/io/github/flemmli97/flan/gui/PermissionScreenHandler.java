package io.github.flemmli97.flan.gui;

import io.github.flemmli97.flan.api.permission.ClaimPermission;
import io.github.flemmli97.flan.api.permission.PermissionManager;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.PermHelper;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.gui.inv.SeparateInv;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public class PermissionScreenHandler extends ServerOnlyScreenHandler<ClaimGroup> {

    private final Claim claim;
    private final String group;
    private int page, maxPages;
    private List<ClaimPermission> perms;

    private PermissionScreenHandler(int syncId, Inventory playerInventory, Claim claim, String group) {
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

    public static void openClaimMenu(Player player, Claim claim, String group) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                return new PermissionScreenHandler(syncId, inv, claim, group);
            }

            @Override
            public Component getDisplayName() {
                return PermHelper.simpleColoredText(group == null ? ConfigHandler.LANG_MANAGER.get("screenGlobalPerms") : String.format(ConfigHandler.LANG_MANAGER.get("screenGroupPerms"), group));
            }
        };
        player.openMenu(fac);
    }

    @Override
    protected void fillInventoryWith(Player player, SeparateInv inv, ClaimGroup additionalData) {
        this.perms = new ArrayList<>(PermissionManager.INSTANCE.getAll());
        if (additionalData.getGroup() != null)
            this.perms.removeIf(p -> p.global);
        this.maxPages = (this.perms.size() - 1) / 28;
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack close = new ItemStack(Items.TNT);
                close.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(ConfigHandler.LANG_MANAGER.get("screenBack"), ChatFormatting.DARK_RED));
                inv.updateStack(i, close);
            } else if (i == 51) {
                ItemStack close = new ItemStack(Items.ARROW);
                close.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(ConfigHandler.LANG_MANAGER.get("screenNext"), ChatFormatting.WHITE));
                inv.updateStack(i, close);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                inv.updateStack(i, ServerScreenHelper.emptyFiller());
            else {
                int row = i / 9 - 1;
                int id = (i % 9) + row * 7 - 1;
                if (id < this.perms.size())
                    inv.updateStack(i, ServerScreenHelper.fromPermission(additionalData.getClaim(), this.perms.get(id), additionalData.getGroup() == null ? null : additionalData.getGroup()));
            }
        }
    }

    private void flipPage() {
        for (int i = 0; i < 54; i++) {
            if (i == 0) {
                ItemStack close = new ItemStack(Items.TNT);
                close.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(ConfigHandler.LANG_MANAGER.get("screenBack"), ChatFormatting.DARK_RED));
                this.slots.get(i).set(close);
            } else if (i == 47) {
                ItemStack stack = ServerScreenHelper.emptyFiller();
                if (this.page >= 1) {
                    stack = new ItemStack(Items.ARROW);
                    stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(ConfigHandler.LANG_MANAGER.get("screenPrevious"), ChatFormatting.WHITE));
                }
                this.slots.get(i).set(stack);
            } else if (i == 51) {
                ItemStack stack = ServerScreenHelper.emptyFiller();
                if (this.page < this.maxPages) {
                    stack = new ItemStack(Items.ARROW);
                    stack.set(DataComponents.CUSTOM_NAME, ServerScreenHelper.coloredGuiText(ConfigHandler.LANG_MANAGER.get("screenNext"), ChatFormatting.WHITE));
                }
                this.slots.get(i).set(stack);
            } else if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8)
                this.slots.get(i).set(ServerScreenHelper.emptyFiller());
            else {
                int row = i / 9 - 1;
                int id = (i % 9) + row * 7 - 1 + this.page * 28;
                if (id < this.perms.size()) {
                    this.slots.get(i).set(ServerScreenHelper.fromPermission(this.claim, this.perms.get(id), this.group));
                } else
                    this.slots.get(i).set(ItemStack.EMPTY);
            }
        }
        this.broadcastChanges();
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {
        if (index == 0) {
            if (this.group == null) {
                player.closeContainer();
                player.getServer().execute(() -> ClaimMenuScreenHandler.openClaimMenu(player, this.claim));
            } else {
                player.closeContainer();
                player.getServer().execute(() -> GroupScreenHandler.openGroupMenu(player, this.claim));
            }
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
            return true;
        }
        if (index == 47) {
            this.page--;
            this.flipPage();
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
        }
        if (index == 51) {
            this.page++;
            this.flipPage();
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, 1, 1f);
        }
        ItemStack stack = slot.getItem();
        ClaimPermission perm;
        try {
            perm = PermissionManager.INSTANCE.get(ResourceLocation.parse(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .copyTag().getString(ServerScreenHelper.PERMISSION_KEY)));
            if (perm == null)
                return false;
        } catch (NullPointerException e) {
            return false;
        }
        boolean success;
        if (this.group == null) {
            int mode;
            if (this.claim.parentClaim() == null)
                mode = this.claim.permEnabled(perm.getId()) == 1 ? -1 : 1;
            else
                mode = this.claim.permEnabled(perm.getId()) + 1;
            success = this.claim.editGlobalPerms(player, perm.getId(), mode);
        } else
            success = this.claim.editPerms(player, this.group, perm.getId(), this.claim.groupHasPerm(this.group, perm.getId()) + 1);
        slot.set(ServerScreenHelper.fromPermission(this.claim, perm, this.group));
        if (success)
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.NOTE_BLOCK_PLING, 1, 1.2f);
        else
            ServerScreenHelper.playSongToPlayer(player, SoundEvents.VILLAGER_NO, 1, 1f);
        return true;
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return slot == 0 || (this.page > 0 && slot == 47) || (this.page < this.maxPages && slot == 51) || (slot < 45 && slot > 8 && slot % 9 != 0 && slot % 9 != 8);
    }
}
