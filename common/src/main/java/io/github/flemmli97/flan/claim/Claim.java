package io.github.flemmli97.flan.claim;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.flemmli97.flan.api.data.IPermissionContainer;
import io.github.flemmli97.flan.api.permission.BuiltinPermission;
import io.github.flemmli97.flan.api.permission.PermissionManager;
import io.github.flemmli97.flan.config.Config;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.platform.ClaimPermissionCheck;
import io.github.flemmli97.flan.platform.integration.permissions.PermissionNodeHandler;
import io.github.flemmli97.flan.platform.integration.webmap.WebmapCalls;
import io.github.flemmli97.flan.player.LogoutTracker;
import io.github.flemmli97.flan.player.PlayerClaimData;
import io.github.flemmli97.flan.player.display.ClaimDisplayBox;
import io.github.flemmli97.flan.player.display.DisplayBox;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Claim implements IPermissionContainer {

    private boolean dirty;
    private int minX, minZ, maxX, maxZ, minY;

    private UUID owner;

    private UUID claimID;
    private String claimName = "";
    private BlockPos homePos;
    private final Map<ResourceLocation, Boolean> globalPerm = new HashMap<>();
    private final Map<String, Map<ResourceLocation, Boolean>> permissions = new HashMap<>();

    private final Map<UUID, String> playersGroups = new HashMap<>();

    private final Set<UUID> fakePlayers = new HashSet<>();

    private final List<Claim> subClaims = new ArrayList<>();

    private UUID parent;
    private Claim parentClaim;

    /**
     * Flag for players tracking this claim
     */
    private boolean removed;

    private final ServerLevel level;

    private final Map<Holder<MobEffect>, Integer> potions = new HashMap<>();

    public final AllowedRegistryList<Item> allowedItems = AllowedRegistryList.ofItemLike(BuiltInRegistries.ITEM, this);
    public final AllowedRegistryList<Block> allowedUseBlocks = AllowedRegistryList.ofItemLike(BuiltInRegistries.BLOCK, this);
    public final AllowedRegistryList<Block> allowedBreakBlocks = AllowedRegistryList.ofItemLike(BuiltInRegistries.BLOCK, this);
    public final AllowedRegistryList<EntityType<?>> allowedEntityAttack = new AllowedRegistryList<>(BuiltInRegistries.ENTITY_TYPE, this, AllowedRegistryList.ENTITY_AS_ITEM);
    public final AllowedRegistryList<EntityType<?>> allowedEntityUse = new AllowedRegistryList<>(BuiltInRegistries.ENTITY_TYPE, this, AllowedRegistryList.ENTITY_AS_ITEM);

    public Component enterTitle, enterSubtitle, leaveTitle, leaveSubtitle;

    private Claim(ServerLevel level) {
        this.level = level;
    }

    //New claim
    public Claim(BlockPos pos1, BlockPos pos2, ServerPlayer creator) {
        this(pos1.getX(), pos2.getX(), pos1.getZ(), pos2.getZ(), Math.min(pos1.getY(), pos2.getY()), creator.getUUID(), creator.serverLevel(), PlayerClaimData.get(creator).playerDefaultGroups().isEmpty());
        PlayerClaimData.get(creator).playerDefaultGroups().forEach((s, m) -> m.forEach((perm, bool) -> this.editPerms(null, s, perm, bool ? 1 : 0, true)));
        Collection<Claim> all = ClaimStorage.get(creator.serverLevel()).allClaimsFromPlayer(creator.getUUID());
        String name = String.format(ConfigHandler.CONFIG.defaultClaimName, "%1$s", all.size());
        if (!name.isEmpty()) {
            for (Claim claim : all) {
                // If config formatting has no number this should return the appended number
                String numbered = claim.claimName.replace(name + " #", "");
                if (!numbered.isEmpty()) {
                    name = name + " #" + all.size();
                    break;
                }
            }
        }
        this.claimName = name;
        if (!ConfigHandler.CONFIG.defaultEnterMessage.isEmpty())
            this.enterTitle = Component.literal(String.format(ConfigHandler.CONFIG.defaultEnterMessage, this.claimName));
        if (!ConfigHandler.CONFIG.defaultLeaveMessage.isEmpty())
            this.leaveTitle = Component.literal(String.format(ConfigHandler.CONFIG.defaultLeaveMessage, this.claimName));
    }

    public Claim(BlockPos pos1, BlockPos pos2, UUID creator, ServerLevel world) {
        this(pos1.getX(), pos2.getX(), pos1.getZ(), pos2.getZ(), Math.min(pos1.getY(), pos2.getY()), creator, world);
    }

    //Griefprevention parsing
    public Claim(int x1, int x2, int z1, int z2, int minY, UUID creator, ServerLevel world) {
        this(x1, x2, z1, z2, minY, creator, world, true);
    }

    public Claim(int x1, int x2, int z1, int z2, int minY, UUID creator, ServerLevel world, boolean setDefaultGroups) {
        this.minX = Math.min(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxZ = Math.max(z1, z2);
        this.minY = Math.max(world.getMinBuildHeight(), minY);
        this.owner = creator;
        this.level = world;
        this.homePos = this.getInitCenterPos();
        this.setDirty(true);
        PermissionManager.INSTANCE.getAll().stream().filter(perm -> perm.defaultVal).forEach(perm -> this.globalPerm.put(perm.getId(), true));
        ConfigHandler.CONFIG.getGloballyDefinedVals(world).forEach(e -> this.globalPerm.put(e.getKey(), e.getValue().getValue()));
        if (setDefaultGroups)
            ConfigHandler.CONFIG.defaultGroups.forEach((s, m) -> m.forEach((perm, bool) -> this.editPerms(null, s, perm, bool ? 1 : 0, true)));
    }

    public static Claim fromJson(JsonObject obj, UUID owner, ServerLevel world) {
        Claim claim = new Claim(world);
        claim.readJson(obj, owner);
        ClaimUpdater.updateClaim(claim);
        return claim;
    }

    private BlockPos getInitCenterPos() {
        BlockPos center = BlockPos.containing(this.minX + (this.maxX - this.minX) * 0.5, 0, this.minZ + (this.maxZ - this.minZ) * 0.5);
        int y = !this.level.hasChunk(center.getX() >> 4, center.getZ() >> 4) ? this.minY + 1 : this.level.getChunk(center.getX() >> 4, center.getZ() >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX() & 15, center.getZ() & 15);
        return new BlockPos(center.getX(), y + 1, center.getZ());
    }

    private BlockPos getDefaultCenterPos() {
        BlockPos center = BlockPos.containing(this.minX + (this.maxX - this.minX) * 0.5, 0, this.minZ + (this.maxZ - this.minZ) * 0.5);
        return new BlockPos(center.getX(), 255, center.getZ());
    }

    public void setClaimID(UUID uuid) {
        this.claimID = uuid;
        this.setDirty(true);
    }

    public void extendDownwards(BlockPos pos) {
        this.minY = pos.getY();
        this.setDirty(true);
        WebmapCalls.onExtendDownwards(this);
    }

    public UUID getClaimID() {
        return this.claimID;
    }

    public String getClaimName() {
        String ownerName = this.isAdminClaim() ? "Admin" : this.level.getServer().getProfileCache().get(this.owner).map(GameProfile::getName).orElse("<UNKNOWN>");
        return String.format(this.claimName, ownerName);
    }

    public void setClaimName(String name) {
        this.claimName = name;
        this.setDirty(true);
        WebmapCalls.changeClaimName(this);
    }

    public UUID getOwner() {
        return this.owner;
    }

    public Optional<ServerPlayer> getOwnerPlayer() {
        if (this.getOwner() != null)
            return Optional.ofNullable(this.level.getServer().getPlayerList().getPlayer(this.getOwner()));
        return Optional.empty();
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public Claim parentClaim() {
        if (this.parent == null)
            return null;
        if (this.parentClaim == null) {
            ClaimStorage storage = ClaimStorage.get(this.level);
            this.parentClaim = storage.getFromUUID(this.parent);
        }
        return this.parentClaim;
    }

    public void copySizes(Claim claim) {
        this.minX = claim.minX;
        this.maxX = claim.maxX;
        this.minZ = claim.minZ;
        this.maxZ = claim.maxZ;
        this.minY = claim.minY;
        this.removed = false;
        this.setDirty(true);
    }

    public void toggleAdminClaim(ServerPlayer player, boolean flag) {
        if (!flag)
            this.transferOwner(player.getUUID());
        else {
            this.owner = null;
            this.subClaims.forEach(claim -> claim.owner = null);
        }
        this.setDirty(true);
    }

    public boolean isAdminClaim() {
        return this.owner == null;
    }

    public void transferOwner(UUID player) {
        this.owner = player;
        this.subClaims.forEach(claim -> claim.owner = player);
        this.setDirty(true);
    }

    public int getPlane() {
        return (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
    }

    /**
     * @return The claims dimension in order: x, X, z, Z, y
     */
    public int[] getDimensions() {
        return new int[]{this.minX, this.maxX, this.minZ, this.maxZ, this.minY};
    }

    public int getMaxY() {
        return this.getLevel().getMaxBuildHeight();
    }

    public boolean insideClaim(BlockPos pos) {
        return this.minX <= pos.getX() && this.maxX >= pos.getX() && this.minZ <= pos.getZ() && this.maxZ >= pos.getZ() && this.minY <= pos.getY();
    }

    public boolean intersects(Claim other) {
        return this.minX <= other.maxX && this.maxX >= other.minX && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public boolean intersects(AABB box) {
        return this.minX < box.maxX && this.maxX + 1 > box.minX && this.minZ < box.maxZ && this.maxZ + 1 > box.minZ && box.maxY >= this.minY;
    }

    public boolean isCorner(BlockPos pos) {
        return (pos.getX() == this.minX && pos.getZ() == this.minZ) || (pos.getX() == this.minX && pos.getZ() == this.maxZ)
                || (pos.getX() == this.maxX && pos.getZ() == this.minZ) || (pos.getX() == this.maxX && pos.getZ() == this.maxZ);
    }

    public void remove() {
        this.removed = true;
    }

    public boolean isRemoved() {
        return this.removed;
    }

    @Override
    public boolean canInteract(ServerPlayer player, ResourceLocation perm, BlockPos pos, boolean message) {
        boolean realPlayer = player != null && player.getClass().equals(ServerPlayer.class);
        message = message && realPlayer && player.connection != null; //dont send messages to fake players
        //Delegate interaction to FAKEPLAYER perm if a fake player
        if (player != null && !realPlayer) {
            //Some mods use the actual user/placer/owner whatever of the fakeplayer. E.g. ComputerCraft
            //For those mods we dont pass them as fake players
            if (this.fakePlayers.contains(player.getUUID()))
                return true;
            if (!player.getUUID().equals(this.owner) && !this.playersGroups.containsKey(player.getUUID())) {
                perm = BuiltinPermission.FAKEPLAYER;
            }
        }
        InteractionResult res = ClaimPermissionCheck.INSTANCE.check(player, perm, pos);
        if (res != InteractionResult.PASS)
            return res != InteractionResult.FAIL;
        if (!this.isAdminClaim()) {
            Config.GlobalType global = ConfigHandler.CONFIG.getGlobal(this.level, perm);
            if (!global.canModify()) {
                if (global.getValue() || (player != null && this.isAdminIgnore(player)))
                    return true;
                if (message)
                    player.displayClientMessage(PermHelper.translatedText("flan.noPermissionSimple", ChatFormatting.DARK_RED), true);
                if (perm.equals(BuiltinPermission.FAKEPLAYER))
                    this.getOwnerPlayer().ifPresent(p -> PlayerClaimData.get(p).notifyFakePlayerInteraction(player, pos, this));
                return false;
            }
            if (ConfigHandler.CONFIG.offlineProtectActivation != -1 && (LogoutTracker.getInstance(this.level.getServer()).justLoggedOut(this.getOwner()) || this.getOwnerPlayer().isPresent())) {
                return global == Config.GlobalType.NONE || global.getValue();
            }
        }
        if (PermissionManager.INSTANCE.isGlobalPermission(perm)) {
            for (Claim claim : this.subClaims) {
                if (claim.insideClaim(pos)) {
                    return claim.canInteract(player, perm, pos, message);
                }
            }
            if (this.hasPerm(perm))
                return true;
            if (message)
                player.displayClientMessage(PermHelper.translatedText("flan.noPermissionSimple", ChatFormatting.DARK_RED), true);
            if (perm.equals(BuiltinPermission.FAKEPLAYER))
                this.getOwnerPlayer().ifPresent(p -> PlayerClaimData.get(p).notifyFakePlayerInteraction(player, pos, this));
            return false;
        }
        if (this.isAdminIgnore(player) || player.getUUID().equals(this.owner))
            return true;
        if (!perm.equals(BuiltinPermission.EDITCLAIM) && !perm.equals(BuiltinPermission.EDITPERMS))
            for (Claim claim : this.subClaims) {
                if (claim.insideClaim(pos)) {
                    return claim.canInteract(player, perm, pos, message);
                }
            }
        if (this.playersGroups.containsKey(player.getUUID())) {
            Map<ResourceLocation, Boolean> map = this.permissions.get(this.playersGroups.get(player.getUUID()));
            if (map != null && map.containsKey(perm)) {
                if (map.get(perm))
                    return true;
                if (message)
                    player.displayClientMessage(PermHelper.translatedText("flan.noPermissionSimple", ChatFormatting.DARK_RED), true);
                if (perm.equals(BuiltinPermission.FAKEPLAYER))
                    this.getOwnerPlayer().ifPresent(p -> PlayerClaimData.get(p).notifyFakePlayerInteraction(player, pos, this));
                return false;
            }
        }
        if (this.hasPerm(perm))
            return true;
        if (message)
            player.displayClientMessage(PermHelper.translatedText("flan.noPermissionSimple", ChatFormatting.DARK_RED), true);
        if (perm.equals(BuiltinPermission.FAKEPLAYER))
            this.getOwnerPlayer().ifPresent(p -> PlayerClaimData.get(p).notifyFakePlayerInteraction(player, pos, this));
        return false;
    }

    private boolean isAdminIgnore(ServerPlayer player) {
        if (player == null)
            return true;
        if (PlayerClaimData.get(player).isAdminIgnoreClaim())
            return !this.isAdminClaim() || PermissionNodeHandler.INSTANCE.perm(player, PermissionNodeHandler.cmdAdminBypass, true);
        return this.isAdminClaim() && player.hasPermissions(2);
    }

    /**
     * @return -1 for default, 0 for false, 1 for true
     */
    public int permEnabled(ResourceLocation perm) {
        return !this.globalPerm.containsKey(perm) ? -1 : this.globalPerm.get(perm) ? 1 : 0;
    }

    private boolean hasPerm(ResourceLocation perm) {
        if (this.parentClaim() == null)
            return this.permEnabled(perm) == 1;
        if (this.permEnabled(perm) == -1)
            return this.parentClaim().permEnabled(perm) == 1;
        return this.permEnabled(perm) == 1;
    }

    private UUID generateUUID() {
        UUID uuid = UUID.randomUUID();
        for (Claim claim : this.subClaims)
            if (claim.claimID.equals(uuid)) {
                return this.generateUUID();
            }
        return uuid;
    }

    public Set<Claim> tryCreateSubClaim(BlockPos pos1, BlockPos pos2) {
        //No sub sub claims
        if (this.parentClaim() != null)
            return Set.of(this.parentClaim());
        Claim sub = new Claim(pos1, new BlockPos(pos2.getX(), 0, pos2.getZ()), this.owner, this.level);
        sub.setClaimID(this.generateUUID());
        Set<Claim> conflicts = new HashSet<>();
        for (Claim other : this.subClaims)
            if (sub.intersects(other)) {
                conflicts.add(other);
            }
        if (conflicts.isEmpty()) {
            sub.parent = this.claimID;
            sub.parentClaim = this;
            this.subClaims.add(sub);
            //Copy parent claims perms
            sub.permissions.clear();
            sub.permissions.putAll(this.permissions);
            sub.playersGroups.clear();
            sub.playersGroups.putAll(this.playersGroups);
            sub.potions.clear();
            sub.potions.putAll(this.potions);
            this.setDirty(true);
        }
        return conflicts;
    }

    public void addSubClaimGriefprevention(Claim claim) {
        claim.setClaimID(this.generateUUID());
        claim.parent = this.claimID;
        claim.parentClaim = this;
        this.subClaims.add(claim);
        this.setDirty(true);
    }

    public Claim getSubClaim(BlockPos pos) {
        for (Claim claim : this.subClaims)
            if (claim.insideClaim(pos))
                return claim;
        return null;
    }

    public boolean deleteSubClaim(Claim claim) {
        claim.remove();
        this.setDirty(true);
        return this.subClaims.remove(claim);
    }

    public List<Claim> getAllSubclaims() {
        return ImmutableList.copyOf(this.subClaims);
    }

    public Set<Claim> resizeSubclaim(Claim claim, BlockPos from, BlockPos to) {
        int[] dims = claim.getDimensions();
        BlockPos opposite = new BlockPos(dims[0] == from.getX() ? dims[1] : dims[0], dims[4], dims[2] == from.getZ() ? dims[3] : dims[2]);
        Claim newClaim = new Claim(opposite, to, claim.claimID, this.level);
        Set<Claim> conflicts = new HashSet<>();
        for (Claim other : this.subClaims)
            if (!claim.equals(other) && newClaim.intersects(other))
                conflicts.add(other);
        if (conflicts.isEmpty()) {
            claim.copySizes(newClaim);
            this.setDirty(true);
        }
        return conflicts;
    }

    public boolean setPlayerGroup(UUID player, String group, boolean force) {
        if (player.equals(this.owner))
            return false;
        if (group == null) {
            this.playersGroups.remove(player);
            this.setDirty(true);
            return true;
        }
        if (!this.playersGroups.containsKey(player) || force) {
            this.playersGroups.put(player, group);
            this.setDirty(true);
            return true;
        }
        return false;
    }

    public boolean modifyFakePlayerUUID(UUID uuid, boolean remove) {
        if (remove)
            return this.fakePlayers.remove(uuid);
        return this.fakePlayers.add(uuid);
    }

    public List<String> playersFromGroup(MinecraftServer server, String group) {
        List<UUID> l = new ArrayList<>();
        this.playersGroups.forEach((uuid, g) -> {
            if (g.equals(group))
                l.add(uuid);
        });
        List<String> names = new ArrayList<>();
        l.forEach(uuid -> server.getProfileCache().get(uuid).ifPresent(prof -> names.add(prof.getName())));
        names.sort(null);
        return names;
    }

    public List<String> getAllowedFakePlayerUUID() {
        return this.fakePlayers.stream().map(UUID::toString).toList();
    }

    public boolean editGlobalPerms(ServerPlayer player, ResourceLocation toggle, int mode) {
        if ((player != null && !this.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition())) || (!this.isAdminClaim() && ConfigHandler.CONFIG.globallyDefined(this.level, toggle)))
            return false;
        if (mode > 1)
            mode = -1;
        if (mode == -1)
            this.globalPerm.remove(toggle);
        else
            this.globalPerm.put(toggle, mode == 1);
        this.setDirty(true);
        return true;
    }

    public boolean editPerms(ServerPlayer player, String group, ResourceLocation perm, int mode) {
        return this.editPerms(player, group, perm, mode, false);
    }

    /**
     * Edit the permissions for a group. If not defined for the group creates a new default permission map for that group
     *
     * @param mode -1 = makes it resort to the global perm, 0 = deny perm, 1 = allow perm
     * @return If editing was successful or not
     */
    public boolean editPerms(ServerPlayer player, String group, ResourceLocation perm, int mode, boolean alwaysCan) {
        if (PermissionManager.INSTANCE.isGlobalPermission(perm) || (!this.isAdminClaim() && ConfigHandler.CONFIG.globallyDefined(this.level, perm)))
            return false;
        if (alwaysCan || this.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition())) {
            if (mode > 1)
                mode = -1;
            boolean has = this.permissions.containsKey(group);
            Map<ResourceLocation, Boolean> perms = has ? this.permissions.get(group) : new HashMap<>();
            if (mode == -1)
                perms.remove(perm);
            else
                perms.put(perm, mode == 1);
            if (!has)
                this.permissions.put(group, perms);
            this.setDirty(true);
            return true;
        }
        return false;
    }

    public boolean removePermGroup(ServerPlayer player, String group) {
        if (this.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition())) {
            this.permissions.remove(group);
            List<UUID> toRemove = new ArrayList<>();
            this.playersGroups.forEach((uuid, g) -> {
                if (g.equals(group))
                    toRemove.add(uuid);
            });
            toRemove.forEach(this.playersGroups::remove);
            this.setDirty(true);
            return true;
        }
        return false;
    }

    public int groupHasPerm(String rank, ResourceLocation perm) {
        if (!this.permissions.containsKey(rank) || !this.permissions.get(rank).containsKey(perm))
            return -1;
        return this.permissions.get(rank).get(perm) ? 1 : 0;
    }

    public List<String> groups() {
        List<String> l = new ArrayList<>(this.permissions.keySet());
        l.sort(null);
        return l;
    }

    public boolean setHomePos(BlockPos homePos) {
        if (this.insideClaim(homePos)) {
            this.homePos = homePos;
            this.setDirty(true);
            return true;
        }
        return false;
    }

    public void addPotion(Holder<MobEffect> effect, int amplifier) {
        this.potions.put(effect, amplifier);
        this.setDirty(true);
    }

    public void removePotion(Holder<MobEffect> effect) {
        this.potions.remove(effect);
        this.setDirty(true);
    }

    public Map<Holder<MobEffect>, Integer> getPotions() {
        return this.potions;
    }

    public void applyEffects(ServerPlayer player) {
        if (player.level().getGameTime() % 80 == 0)
            this.potions.forEach((effect, amp) -> player.forceAddEffect(new MobEffectInstance(effect, effect == MobEffects.NIGHT_VISION ? 400 : 200, amp - 1, true, false), null));
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    public void setEnterTitle(Component title, Component sub) {
        if (title != null && title.getString().equals("$empty"))
            title = null;
        if (sub != null && sub.getString().equals("$empty"))
            sub = null;
        this.enterTitle = title;
        this.enterSubtitle = sub;
        this.setDirty(true);
    }

    public void setLeaveTitle(Component title, Component sub) {
        if (title != null && title.getString().equals("$empty"))
            title = null;
        if (sub != null && sub.getString().equals("$empty"))
            sub = null;
        this.leaveTitle = title;
        this.leaveSubtitle = sub;
        this.setDirty(true);
    }

    private void displayTitleMessage(ServerPlayer player, @Nullable Component title, @Nullable Component subtitle) {
        title = this.transformForDisplay(title);
        if (title == null) return;
        subtitle = this.transformForDisplay(subtitle);
        if (ConfigHandler.CONFIG.claimDisplayActionBar) {
            if (subtitle != null) {
                MutableComponent message = title.copy().append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))).append(subtitle);
                player.displayClientMessage(message, true);
                return;
            }
            player.displayClientMessage(title, true);
            return;
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    @Nullable
    private Component transformForDisplay(Component component) {
        if (component == null)
            return null;
        MutableComponent res;
        String claimName = this.getClaimName();
        if (component.getContents() instanceof TranslatableContents trans) {
            res = Component.translatable(trans.getKey(), this.isAdminClaim() ? "Admin" : this.level.getServer().getProfileCache().get(this.owner).map(GameProfile::getName).orElse("<UNKNOWN>"), claimName);
        } else if (component.getContents() instanceof PlainTextContents comp) {
            res = Component.translatable(comp.text(), this.isAdminClaim() ? "Admin" : this.level.getServer().getProfileCache().get(this.owner).map(GameProfile::getName).orElse("<UNKNOWN>"), claimName);
        } else {
            res = component.plainCopy();
        }
        res.getSiblings().addAll(component.getSiblings().stream().map(this::transformForDisplay).toList());
        res.setStyle(component.getStyle());
        return res;
    }

    public void displayEnterTitle(ServerPlayer player) {
        this.displayTitleMessage(player, this.enterTitle, this.enterSubtitle);
    }

    public void displayLeaveTitle(ServerPlayer player) {
        this.displayTitleMessage(player, this.leaveTitle, this.leaveSubtitle);
    }

    public boolean canUseItem(ItemStack stack) {
        return this.allowedItems.matches(stack::is, stack::is);
    }

    public boolean canUseBlockItem(BlockState state) {
        return this.allowedUseBlocks.matches(state::is, state::is);
    }

    public boolean canBreakBlockItem(BlockState state) {
        return this.allowedBreakBlocks.matches(state::is, state::is);
    }

    public boolean canAttackEntity(Entity entity) {
        return this.allowedEntityAttack.matches(type -> entity.getType() == type, tag -> entity.getType().is(tag));
    }

    public boolean canInteractWithEntity(Entity entity) {
        return this.allowedEntityUse.matches(type -> entity.getType() == type, tag -> entity.getType().is(tag));
    }

    /**
     * Only marks non sub claims
     */
    public void setDirty(boolean flag) {
        if (this.parentClaim() != null)
            this.parentClaim().setDirty(flag);
        else
            this.dirty = flag;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void readJson(JsonObject obj, UUID uuid) {
        try {
            this.claimID = UUID.fromString(obj.get("ID").getAsString());
            this.claimName = ConfigHandler.fromJson(obj, "Name", "");
            JsonArray pos = obj.getAsJsonArray("PosxXzZY");
            this.minX = pos.get(0).getAsInt();
            this.maxX = pos.get(1).getAsInt();
            this.minZ = pos.get(2).getAsInt();
            this.maxZ = pos.get(3).getAsInt();
            this.minY = pos.get(4).getAsInt();
            JsonArray home = ConfigHandler.arryFromJson(obj, "Home");
            if (home.size() != 3)
                this.homePos = this.getDefaultCenterPos();
            else {
                this.homePos = new BlockPos(home.get(0).getAsInt(), home.get(1).getAsInt(), home.get(2).getAsInt());
            }
            String message = ConfigHandler.fromJson(obj, "EnterTitle", "");
            if (!message.isEmpty())
                this.enterTitle = Component.Serializer.fromJson(message, this.level.registryAccess());
            else
                this.enterTitle = null;
            message = ConfigHandler.fromJson(obj, "EnterSubtitle", "");
            if (!message.isEmpty())
                this.enterSubtitle = Component.Serializer.fromJson(message, this.level.registryAccess());
            else
                this.enterSubtitle = null;
            message = ConfigHandler.fromJson(obj, "LeaveTitle", "");
            if (!message.isEmpty())
                this.leaveTitle = Component.Serializer.fromJson(message, this.level.registryAccess());
            else
                this.leaveTitle = null;
            message = ConfigHandler.fromJson(obj, "LeaveSubtitle", "");
            if (!message.isEmpty())
                this.leaveSubtitle = Component.Serializer.fromJson(message, this.level.registryAccess());
            else
                this.leaveSubtitle = null;
            JsonObject potion = ConfigHandler.fromJson(obj, "Potions");
            potion.entrySet().forEach(e ->
                    BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.getKey()))
                            .ifPresent(effect -> this.potions.put(effect, e.getValue().getAsInt())));
            if (ConfigHandler.fromJson(obj, "AdminClaim", false))
                this.owner = null;
            else
                this.owner = uuid;
            this.allowedItems.read(ConfigHandler.arryFromJson(obj, "AllowedItems"));
            this.allowedUseBlocks.read(ConfigHandler.arryFromJson(obj, "AllowedUseBlocks"));
            this.allowedBreakBlocks.read(ConfigHandler.arryFromJson(obj, "AllowedBreakBlocks"));
            this.allowedEntityAttack.read(ConfigHandler.arryFromJson(obj, "AllowedEntityAttack"));
            this.allowedEntityUse.read(ConfigHandler.arryFromJson(obj, "AllowedEntityUse"));
            this.globalPerm.clear();
            this.permissions.clear();
            this.subClaims.clear();
            if (obj.has("Parent"))
                this.parent = UUID.fromString(obj.get("Parent").getAsString());
            if (obj.has("GlobalPerms")) {
                if (this.parent == null) {
                    obj.getAsJsonArray("GlobalPerms").forEach(perm ->
                            this.globalPerm.put(BuiltinPermission.tryLegacy(perm.getAsString()), true));
                } else {
                    obj.getAsJsonObject("GlobalPerms").entrySet().forEach(entry ->
                            this.globalPerm.put(BuiltinPermission.tryLegacy(entry.getKey()), entry.getValue().getAsBoolean()));
                }
            }
            ConfigHandler.fromJson(obj, "PermGroup").entrySet().forEach(key -> {
                Map<ResourceLocation, Boolean> map = new HashMap<>();
                JsonObject group = key.getValue().getAsJsonObject();
                group.entrySet().forEach(gkey ->
                        map.put(BuiltinPermission.tryLegacy(gkey.getKey()), gkey.getValue().getAsBoolean()));
                this.permissions.put(key.getKey(), map);
            });
            ConfigHandler.fromJson(obj, "PlayerPerms").entrySet()
                    .forEach(key -> this.playersGroups.put(UUID.fromString(key.getKey()), key.getValue().getAsString()));
            ConfigHandler.arryFromJson(obj, "SubClaims")
                    .forEach(sub -> this.subClaims.add(Claim.fromJson(sub.getAsJsonObject(), this.owner, this.level)));
            ConfigHandler.arryFromJson(obj, "FakePlayers")
                    .forEach(e -> {
                        try {
                            this.fakePlayers.add(UUID.fromString(e.getAsString()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
        } catch (Exception e) {
            throw new IllegalStateException("Error reading claim data for claim " + uuid);
        }
    }

    public JsonObject toJson(JsonObject obj) {
        obj.addProperty("ID", this.claimID.toString());
        obj.addProperty("Name", this.claimName);
        JsonArray pos = new JsonArray();
        pos.add(this.minX);
        pos.add(this.maxX);
        pos.add(this.minZ);
        pos.add(this.maxZ);
        pos.add(this.minY);
        obj.add("PosxXzZY", pos);
        JsonArray home = new JsonArray();
        home.add(this.homePos.getX());
        home.add(this.homePos.getY());
        home.add(this.homePos.getZ());
        obj.add("Home", home);
        obj.addProperty("EnterTitle", this.enterTitle == null ? "" : Component.Serializer.toJson(this.enterTitle, this.level.registryAccess()));
        obj.addProperty("EnterSubtitle", this.enterSubtitle == null ? "" : Component.Serializer.toJson(this.enterSubtitle, this.level.registryAccess()));
        obj.addProperty("LeaveTitle", this.leaveTitle == null ? "" : Component.Serializer.toJson(this.leaveTitle, this.level.registryAccess()));
        obj.addProperty("LeaveSubtitle", this.leaveSubtitle == null ? "" : Component.Serializer.toJson(this.leaveSubtitle, this.level.registryAccess()));
        JsonObject potions = new JsonObject();
        this.potions.forEach((effect, amp) -> potions.addProperty(effect.getRegisteredName(), amp));
        obj.add("Potions", potions);
        if (this.parent != null)
            obj.addProperty("Parent", this.parent.toString());
        obj.add("AllowedItems", this.allowedItems.save());
        obj.add("AllowedUseBlocks", this.allowedUseBlocks.save());
        obj.add("AllowedBreakBlocks", this.allowedBreakBlocks.save());
        obj.add("AllowedEntityAttack", this.allowedEntityAttack.save());
        obj.add("AllowedEntityUse", this.allowedEntityUse.save());
        if (!this.globalPerm.isEmpty()) {
            JsonElement gPerm;
            if (this.parent == null) {
                gPerm = new JsonArray();
                this.globalPerm.forEach((perm, bool) -> {
                    if (bool)
                        ((JsonArray) gPerm).add(perm.toString());
                });
            } else {
                gPerm = new JsonObject();
                this.globalPerm.forEach((perm, bool) -> ((JsonObject) gPerm).addProperty(perm.toString(), bool));
            }
            obj.add("GlobalPerms", gPerm);
        }
        if (!this.permissions.isEmpty()) {
            JsonObject perms = new JsonObject();
            this.permissions.forEach((s, pmap) -> {
                JsonObject group = new JsonObject();
                pmap.forEach((perm, bool) -> group.addProperty(perm.toString(), bool));
                perms.add(s, group);
            });
            obj.add("PermGroup", perms);
        }
        if (!this.playersGroups.isEmpty()) {
            JsonObject pl = new JsonObject();
            this.playersGroups.forEach((uuid, s) -> pl.addProperty(uuid.toString(), s));
            obj.add("PlayerPerms", pl);
        }
        if (!this.subClaims.isEmpty()) {
            JsonArray list = new JsonArray();
            this.subClaims.forEach(p -> list.add(p.toJson(new JsonObject())));
            obj.add("SubClaims", list);
        }
        if (!this.fakePlayers.isEmpty()) {
            JsonArray list = new JsonArray();
            this.fakePlayers.forEach(uuid -> list.add(uuid.toString()));
            obj.add("FakePlayers", list);
        }
        return obj;
    }

    @Override
    public int hashCode() {
        return this.claimID == null ? Arrays.hashCode(this.getDimensions()) : this.claimID.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof Claim other) {
            if (this.claimID == null && other.claimID == null)
                return Arrays.equals(this.getDimensions(), ((Claim) obj).getDimensions());
            if (this.claimID != null)
                return this.claimID.equals(((Claim) obj).claimID);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Claim:[ID=%s, Owner=%s, from: [x=%d,z=%d], to: [x=%d,z=%d]", this.claimID != null ? this.claimID.toString() : "null", this.owner != null ? this.owner.toString() : "Admin", this.minX, this.minZ, this.maxX, this.maxZ);
    }

    public String nameAndPosition() {
        String name = this.getClaimName();
        if (name.isEmpty())
            return String.format("[x=%d,z=%d]-[x=%d,z=%d]", this.minX, this.minZ, this.maxX, this.maxZ);
        return String.format("%s:[x=%d,z=%d]-[x=%d,z=%d]", name, this.minX, this.minZ, this.maxX, this.maxZ);
    }

    public String formattedClaim() {
        String name = this.getClaimName();
        if (name.isEmpty())
            return String.format("[x=%d,z=%d] - [x=%d,z=%d] = %d blocks", this.minX, this.minZ, this.maxX, this.maxZ, this.getPlane());
        return String.format("%s:[x=%d,z=%d] - [x=%d,z=%d] = %d blocks", name, this.minX, this.minZ, this.maxX, this.maxZ, this.getPlane());
    }

    public List<Component> infoString(ServerPlayer player, InfoType infoType) {
        boolean perms = this.canInteract(player, BuiltinPermission.EDITPERMS, player.blockPosition());
        List<Component> l = new ArrayList<>();
        l.add(PermHelper.translatedText("=============================================", ChatFormatting.GREEN));
        String ownerName = this.isAdminClaim() ? "Admin" : player.getServer().getProfileCache().get(this.owner).map(GameProfile::getName).orElse("<UNKNOWN>");
        String claimName = this.getClaimName();
        if (this.parent == null) {
            if (claimName.isEmpty())
                l.add(PermHelper.translatedText("flan.claimBasicInfo", ownerName, this.minX, this.minZ, this.maxX, this.maxZ, this.subClaims.size(), ChatFormatting.GOLD));
            else
                l.add(PermHelper.translatedText("flan.claimBasicInfoNamed", ownerName, this.minX, this.minZ, this.maxX, this.maxZ, this.subClaims.size(), claimName, ChatFormatting.GOLD));
        } else {
            if (claimName.isEmpty())
                l.add(PermHelper.translatedText("flan.claimBasicInfoSub", ownerName, this.minX, this.minZ, this.maxX, this.maxZ, ChatFormatting.GOLD));
            else
                l.add(PermHelper.translatedText("flan.claimBasicInfoSubNamed", ownerName, this.minX, this.minZ, this.maxX, this.maxZ, claimName, ChatFormatting.GOLD));
        }
        if (perms) {
            if (infoType == InfoType.ALL || infoType == InfoType.GLOBAL)
                l.add(fromPermissionMap("claimInfoPerms", this.globalPerm));
            if (infoType == InfoType.ALL || infoType == InfoType.GROUP) {
                l.add(PermHelper.translatedText("flan.claimGroupInfoHeader", ChatFormatting.GOLD));
                Map<String, List<String>> nameToGroup = new HashMap<>();
                for (Map.Entry<UUID, String> e : this.playersGroups.entrySet()) {
                    player.getServer().getProfileCache().get(e.getKey()).ifPresent(pgroup ->

                            nameToGroup.merge(e.getValue(), Lists.newArrayList(pgroup.getName()), (old, val) -> {
                                old.add(pgroup.getName());
                                return old;
                            })
                    );
                }
                for (Map.Entry<String, Map<ResourceLocation, Boolean>> e : this.permissions.entrySet()) {
                    l.add(PermHelper.translatedText(String.format("  %s:", e.getKey()), ChatFormatting.YELLOW));
                    l.add(fromPermissionMap("claimGroupPerms", e.getValue()));
                    l.add(PermHelper.translatedText("flan.claimGroupPlayers", nameToGroup.getOrDefault(e.getKey(), new ArrayList<>()), ChatFormatting.RED));
                }
            }
        }
        l.add(PermHelper.translatedText("=============================================", ChatFormatting.GREEN));
        return l;
    }

    private static Component fromPermissionMap(String lang, Map<ResourceLocation, Boolean> map) {
        MutableComponent mapComp = Component.literal("[").withStyle(ChatFormatting.GRAY);
        int i = 0;
        for (Map.Entry<ResourceLocation, Boolean> entry : map.entrySet()) {
            MutableComponent pComp = Component.literal((i != 0 ? ", " : "") + entry.getKey() + "=").withStyle(ChatFormatting.GRAY);
            pComp.append(Component.literal(entry.getValue().toString()).withStyle(entry.getValue() ? ChatFormatting.GREEN : ChatFormatting.RED));
            mapComp.append(pComp);
            i++;
        }
        mapComp.append("]");
        return Component.translatable(lang, mapComp).withStyle(ChatFormatting.DARK_BLUE);
    }

    public DisplayBox display() {
        return new ClaimDisplayBox(this, () -> new DisplayBox.Box(this.minX, this.minY, this.minZ, this.maxX, this.level.getMaxBuildHeight(), this.maxZ), this::isRemoved);
    }

    public enum InfoType {
        ALL,
        SIMPLE,
        GLOBAL,
        GROUP
    }

    interface ClaimUpdater {

        Map<Integer, ClaimUpdater> updater = Config.createHashMap(map -> map.put(2, claim -> claim.globalPerm.put(BuiltinPermission.LOCKITEMS, true)));

        static void updateClaim(Claim claim) {
            updater.entrySet().stream().filter(e -> e.getKey() > ConfigHandler.CONFIG.preConfigVersion).map(Map.Entry::getValue)
                    .forEach(up -> up.update(claim));
        }

        void update(Claim claim);
    }
}
