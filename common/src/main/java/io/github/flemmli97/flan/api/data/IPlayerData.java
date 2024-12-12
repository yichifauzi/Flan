package io.github.flemmli97.flan.api.data;

import io.github.flemmli97.flan.config.ConfigHandler;

public interface IPlayerData {

    int getClaimBlocks();

    int getAdditionalClaims();

    int usedClaimBlocks();

    int remainingClaimBlocks();

    void setAdditionalClaims(int amount);

    default boolean canUseClaimBlocks(int amount) {
        if (ConfigHandler.CONFIG.maxClaimBlocks == -1)
            return true;
        int usedClaimsBlocks = this.usedClaimBlocks();
        return usedClaimsBlocks + amount <= this.getClaimBlocks() + this.getAdditionalClaims();
    }
}
