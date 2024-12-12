package io.github.flemmli97.flan.fabric.platform.integration.permissions;

import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.platform.integration.permissions.PermissionNodeHandler;
import me.lucko.fabric.api.permissions.v0.Options;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class PermissionNodeHandlerImpl implements PermissionNodeHandler {

    @Override
    public boolean perm(CommandSourceStack src, String perm, boolean adminCmd) {
        if (Flan.permissionAPI) {
            if (adminCmd)
                return Permissions.check(src, perm, ConfigHandler.CONFIG.permissionLevel);
            return Permissions.check(src, perm, true);
        }
        return PermissionNodeHandler.super.perm(src, perm, adminCmd);
    }

    @Override
    public boolean perm(ServerPlayer src, String perm, boolean adminCmd) {
        if (Flan.permissionAPI) {
            if (adminCmd)
                return Permissions.check(src, perm, ConfigHandler.CONFIG.permissionLevel);
            return Permissions.check(src, perm, true);
        }
        return PermissionNodeHandler.super.perm(src, perm, adminCmd);
    }


    @Override
    public boolean permBelowEqVal(ServerPlayer src, String perm, int val, int fallback) {
        if (Flan.permissionAPI) {
            int max = Options.get(src, perm, fallback, Integer::parseInt);
            return val <= max;
        }
        return PermissionNodeHandler.super.permBelowEqVal(src, perm, val, fallback);
    }

    @Override
    public int permVal(ServerPlayer src, String perm, int fallback) {
        if (Flan.permissionAPI) {
            return Options.get(src, perm, fallback, Integer::parseInt);
        }
        return PermissionNodeHandler.super.permVal(src, perm, fallback);
    }
}
