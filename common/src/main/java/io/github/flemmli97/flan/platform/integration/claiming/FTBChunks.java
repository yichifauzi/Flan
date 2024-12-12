package io.github.flemmli97.flan.platform.integration.claiming;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.player.display.DisplayBox;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

public class FTBChunks {

    public static void findConflicts(Claim claim, Set<DisplayBox> set) {
        if (Flan.ftbChunks && ConfigHandler.CONFIG.ftbChunksCheck) {
            ServerLevel level = claim.getLevel();
            int[] chunks = ClaimStorage.getChunkPos(claim);
            for (int x = chunks[0]; x <= chunks[1]; x++)
                for (int z = chunks[2]; z <= chunks[3]; z++) {
                    ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level.dimension(), x, z));
                    if (chunk != null && !chunk.getTeamData().isTeamMember(claim.getOwner())) {
                        int blockX = x << 4;
                        int blockZ = z << 4;
                        //There is no reason to display it since ftb chunks has a map
                        set.add(new DisplayBox(blockX, level.getMinBuildHeight(), blockZ, blockX + 15, level.getMaxBuildHeight(), blockZ + 15, () -> true));
                    }
                }
        }
    }
}
