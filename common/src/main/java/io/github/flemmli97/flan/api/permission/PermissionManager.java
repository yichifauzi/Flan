package io.github.flemmli97.flan.api.permission;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.flemmli97.flan.Flan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PermissionManager extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "claim_permissions";
    private static final Gson GSON = new GsonBuilder().create();

    public static final PermissionManager INSTANCE = new PermissionManager();

    private Map<ResourceLocation, ClaimPermission> permissions = ImmutableMap.of();
    private List<ClaimPermission> sorted = List.of();

    private PermissionManager() {
        super(GSON, DIRECTORY);
    }

    @Nullable
    public ClaimPermission get(ResourceLocation id) {
        return this.permissions.get(id);
    }

    public Collection<ResourceLocation> getIds() {
        return this.permissions.keySet();
    }

    public Collection<ClaimPermission> getAll() {
        return this.sorted;
    }

    public boolean isGlobalPermission(ResourceLocation id) {
        ClaimPermission perm = this.get(id);
        return perm != null && perm.global;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        ImmutableMap.Builder<ResourceLocation, ClaimPermission> builder = ImmutableMap.builder();
        data.forEach((res, el) -> {
            try {
                ClaimPermission.Builder props = ClaimPermission.Builder.CODEC.parse(JsonOps.INSTANCE, el)
                        .getOrThrow(false, Flan.LOGGER::error);
                if (props.verify())
                    builder.put(res, props.build(res));
            } catch (Exception ex) {
                Flan.LOGGER.error("Couldnt parse claim permission json {} {}", res, ex);
                ex.fillInStackTrace();
            }
        });
        this.permissions = builder.build();
        this.sorted = this.permissions.values().stream().sorted().toList();
    }
}
