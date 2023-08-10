package me.noryea.unofficialsodiumcullingpatch.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.graph.VisibilityEncoding;
import me.jellysquid.mods.sodium.client.util.DirectionUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(VisibilityEncoding.class)
@Environment(EnvType.CLIENT)
public abstract class VisibilityEncodingMixin {

    /**
     * @author Noryea
     * @reason Revert <a href="https://github.com/CaffeineMC/sodium-fabric/commit/8a8aad0df3ec36d5246d6a2a6efc1d34a7e092b1">...</a>
     */
//    @Overwrite(remap = false)
//    public static long encode(@NotNull ChunkOcclusionData occlusionData) {
//        long visibilityData = 0L;
//
//        for(Direction from : DirectionUtil.ALL_DIRECTIONS) {
//            for(Direction to : DirectionUtil.ALL_DIRECTIONS) {
//                if (occlusionData.isVisibleThrough(from, to)) {
//                    visibilityData |= (1L << ((from.ordinal() << 3) + to.ordinal()));
//                }
//            }
//        }
//
//        return visibilityData;
//    }

}
