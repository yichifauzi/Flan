package io.github.flemmli97.flan.claim;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ClaimUtils {

    public static boolean check(ServerPlayer player, BlockPos pos, Claim claim, ResourceLocation perm, Consumer<Optional<Boolean>> cons) {
        if (claim == null) {
            cons.accept(Optional.empty());
            return false;
        }
        boolean hasPerm = claim.canInteract(player, perm, pos);
        cons.accept(Optional.of(hasPerm));
        return hasPerm;
    }

    public static Claim checkReturn(ServerPlayer player, ResourceLocation perm, Consumer<Optional<Boolean>> cons) {
        BlockPos pos = player.blockPosition();
        Claim claim = ClaimStorage.get(player.serverLevel()).getClaimAt(pos);
        return check(player, pos, claim, perm, cons) ? claim : null;
    }

    public static void noClaimMessage(ServerPlayer player) {
        player.displayClientMessage(translatedText("flan.noClaim", ChatFormatting.DARK_RED), false);
    }

    public static Consumer<Optional<Boolean>> genericNoPermMessage(ServerPlayer player) {
        return (b -> {
            if (!b.isPresent())
                ClaimUtils.noClaimMessage(player);
            else if (!b.get())
                player.displayClientMessage(translatedText("flan.noPermission", ChatFormatting.DARK_RED), false);
        });
    }

    public static MutableComponent translatedText(String key, Object... compArgs) {
        List<ChatFormatting> formattings = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Object obj : compArgs) {
            if (obj instanceof ChatFormatting formatting)
                formattings.add(formatting);
            else
                args.add(obj);
        }
        return Component.translatable(key,
                args.toArray()).setStyle(Style.EMPTY.applyFormats(formattings.toArray(ChatFormatting[]::new)));
    }

    public static Optional<String> fetchUsername(UUID uuid, MinecraftServer server) {
        return fetchUsername(uuid, server, false);
    }

    public static Optional<String> fetchUsername(UUID uuid, MinecraftServer server, boolean fetch) {
        String ownerName = server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(null);
        if (ownerName == null && fetch) {
            ProfileResult res = server.getSessionService().fetchProfile(uuid, true);
            ownerName = res != null ? res.profile().getName() : null;
        }
        return Optional.ofNullable(ownerName);
    }
}
