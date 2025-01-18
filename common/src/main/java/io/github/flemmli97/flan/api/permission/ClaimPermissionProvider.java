package io.github.flemmli97.flan.api.permission;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provider for datagen to generate claim permissions
 */
public abstract class ClaimPermissionProvider implements DataProvider {

    private final Map<ResourceLocation, ClaimPermission.Builder> data = new LinkedHashMap<>();

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> lookup;

    public ClaimPermissionProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        this.output = output;
        this.lookup = lookup;
    }

    protected abstract void add(HolderLookup.Provider provider);

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.lookup.thenApply(provider -> {
            this.add(provider);
            return provider;
        }).thenCompose(provider -> CompletableFuture.allOf(this.data.entrySet().stream().map(entry -> {
            ResourceLocation res = entry.getKey();
            Path path = this.output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(res.getNamespace() + "/" + PermissionManager.DIRECTORY + "/" + res.getPath() + ".json");
            JsonElement obj = ClaimPermission.Builder.CODEC.encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), entry.getValue())
                    .getOrThrow();
            return DataProvider.saveStable(cache, obj, path);
        }).toArray(CompletableFuture<?>[]::new)));
    }

    @Override
    public String getName() {
        return "Permissions";
    }

    public void addPermission(ResourceLocation res, ClaimPermission.Builder permission) {
        if (this.data.put(res, permission) != null) {
            throw new IllegalStateException("Permission already registered for " + res);
        }
    }

    public Map<ResourceLocation, ClaimPermission.Builder> getData() {
        return ImmutableMap.copyOf(this.data);
    }
}