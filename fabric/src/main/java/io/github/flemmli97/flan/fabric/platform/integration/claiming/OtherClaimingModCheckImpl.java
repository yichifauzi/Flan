package io.github.flemmli97.flan.fabric.platform.integration.claiming;

import com.jamieswhiteshirt.rtree3i.Box;
import draylar.goml.api.ClaimUtils;
import io.github.flemmli97.flan.Flan;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.config.ConfigHandler;
import io.github.flemmli97.flan.platform.integration.claiming.FTBChunks;
import io.github.flemmli97.flan.platform.integration.claiming.OtherClaimingModCheck;
import io.github.flemmli97.flan.player.display.DisplayBox;

import java.util.Set;

public class OtherClaimingModCheckImpl implements OtherClaimingModCheck {

    @Override
    public void findConflicts(Claim claim, Set<DisplayBox> set) {
        FTBChunks.findConflicts(claim, set);
        if (Flan.gomlServer && ConfigHandler.CONFIG.gomlReservedCheck) {
            int[] dim = claim.getDimensions();
            ClaimUtils.getClaimsInBox(claim.getLevel(), ClaimUtils.createBox(dim[0] - 1, dim[4], dim[2] - 1, dim[1] + 1, claim.getLevel().getMaxBuildHeight(), dim[3] + 1))
                    .forEach(e -> {
                        if (!e.getValue().hasPermission(claim.getOwner()))
                            set.add(convertBox(e.getValue()));
                    });
        }
    }

    private static DisplayBox convertBox(draylar.goml.api.Claim claim) {
        Box box = claim.getClaimBox().toBox();
        return new DisplayBox(box.x1(), box.y1(), box.z1(), box.x2(), box.y2(), box.z2(), claim::isDestroyed);
    }
}
