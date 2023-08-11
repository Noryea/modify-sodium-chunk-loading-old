package me.noryea.unofficialsodiumcullingpatch.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.graph.GraphDirection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.graph.VisibilityEncoding;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderListBuilder;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;

import java.util.ArrayDeque;

@Mixin(RenderSectionManager.class)
@Environment(EnvType.CLIENT)
public abstract class RenderSectionManagerMixin {
    @Unique
    private double maxVertexDistanceSquared;

    @Final
    @Shadow(remap = false) private ArrayDeque<RenderSection> iterationQueue;

    @Final
    @Shadow(remap = false) private int renderDistance;

    @Final
    @Shadow(remap = false) private ClientWorld world;

    @Shadow(remap = false) private int currentFrame;
    @Shadow(remap = false) private int effectiveRenderDistance;
    @Shadow(remap = false) private int centerChunkX;
    @Shadow(remap = false) private int centerChunkY;
    @Shadow(remap = false) private int centerChunkZ;
    @Shadow(remap = false) private boolean useBlockFaceCulling;

    @Shadow(remap = false) private boolean useOcclusionCulling;

    @Shadow(remap = false)
    protected abstract void addToRenderLists(SortedRenderListBuilder renderListBuilder, RenderSection section);

    @Shadow(remap = false)
    protected abstract boolean isOutsideViewport(RenderSection section, Viewport viewport);

    @Shadow(remap = false)
    protected abstract void addToRebuildLists(RenderSection section);

    @Shadow(remap = false)
    protected abstract int getOutwardDirections(int x, int y, int z);

    @Shadow(remap = false)
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow(remap = false)
    protected abstract void initSearchFallback(Viewport viewport, BlockPos origin, int chunkX, int chunkY, int chunkZ, int directions);

    @Shadow(remap = false)
    protected abstract void bfsEnqueue(RenderSection render, int incomingDirections);


    /**
     * @author Noryea
     * @reason from this commit <a href="https://github.com/CaffeineMC/sodium-fabric/commit/f973cb54afb302d5e1e8ed6da9a486ca6e4fd107">...</a>
     */
    @Overwrite(remap = false)
    private void searchChunks(SortedRenderListBuilder renderListBuilder, Camera camera, Viewport viewport, int frame, boolean spectator) {
        this.modifiedInitSearch(camera, viewport, frame, spectator);

        while (!this.iterationQueue.isEmpty()) {
            RenderSection section = this.iterationQueue.remove();

            if (this.centerChunkX != section.getChunkX() || this.centerChunkY != section.getChunkY() || this.centerChunkZ != section.getChunkZ()) {
                var vertexDistance = this.getClosestVertexDistanceToCamera(camera.getPos(), section);

                if (vertexDistance > this.maxVertexDistanceSquared || this.isOutsideViewport(section, viewport)) {
                    continue;
                }
            }

            this.addToRenderLists(renderListBuilder, section);

            if (section.getPendingUpdate() != null && section.getBuildCancellationToken() == null) {
                this.addToRebuildLists(section);
            }

            int connections;

            if (this.useOcclusionCulling) {
                connections = VisibilityEncoding.getConnections(section.getVisibilityData(), section.getIncomingDirections());
            } else {
                connections = GraphDirection.ALL;
            }

            connections &= this.getOutwardDirections(section.getChunkX(), section.getChunkY(), section.getChunkZ());

            if (connections != GraphDirection.NONE) {
                this.modifiedSearchNeighbors(section, connections);
            }
        }
    }

    @Unique
    private void modifiedInitSearch(Camera camera, Viewport viewport, int frame, boolean spectator) {
        this.iterationQueue.clear();
        this.currentFrame = frame;
        var options = SodiumClientMod.options();

        double maxVertexDistance = getEffectiveRenderDistanceDouble();
        this.effectiveRenderDistance = Math.min(MathHelper.ceil(maxVertexDistance / 16.0D), this.renderDistance);

        this.maxVertexDistanceSquared = maxVertexDistance * maxVertexDistance;

        this.useBlockFaceCulling = options.performance.useBlockFaceCulling;

        BlockPos origin = camera.getBlockPos();

        if (spectator && this.world.getBlockState(origin).isOpaqueFullCube(this.world, origin)) {
            this.useOcclusionCulling = false;
        } else {
            this.useOcclusionCulling = MinecraftClient.getInstance().chunkCullingEnabled;
        }

        this.centerChunkX = origin.getX() >> 4;
        this.centerChunkY = origin.getY() >> 4;
        this.centerChunkZ = origin.getZ() >> 4;

        if (this.centerChunkY < this.world.getBottomSectionCoord())
        {
            this.initSearchFallback(viewport, origin, this.centerChunkX, this.world.getBottomSectionCoord(), this.centerChunkZ, 1 << GraphDirection.DOWN);
        }
        else if (this.centerChunkY >= this.world.getTopSectionCoord()) {
            this.initSearchFallback(viewport, origin, this.centerChunkX, this.world.getTopSectionCoord() - 1, this.centerChunkZ, 1 << GraphDirection.UP);
        }
        else {
            var node = this.getRenderSection(this.centerChunkX, this.centerChunkY, this.centerChunkZ);

            if (node != null) {
                this.bfsEnqueue(node, GraphDirection.ALL);
            }
        }
    }

    @Unique
    private void modifiedSearchNeighbors(RenderSection section, int outgoing) {
        for(int direction = 0; direction < 6; ++direction) {
            if ((outgoing & 1 << direction) != 0) {
                RenderSection adj = section.getAdjacent(direction);
                if (adj != null) {
                    this.bfsEnqueue(adj, 1 << GraphDirection.opposite(direction));
                    adj.setIncomingDirections(1 << GraphDirection.opposite(direction));
                }
            }
        }

    }

    @Unique
    // aggressive distance culling
    private double getClosestVertexDistanceToCamera(Vec3d origin, RenderSection section) {
        // the offset of the vertex from the center of the chunk
        int offsetX = Integer.signum(this.centerChunkX - section.getChunkX()) * 8; // (chunk.x > center.x) ? -8 : +8
        int offsetY = Integer.signum(this.centerChunkY - section.getChunkY()) * 8; // (chunk.y > center.y) ? -8 : +8
        int offsetZ = Integer.signum(this.centerChunkZ - section.getChunkZ()) * 8; // (chunk.z > center.z) ? -8 : +8

        // the vertex's distance from the origin on each axis
        double distanceX = origin.x - (section.getOriginX() + 8 + offsetX);
        double distanceY = origin.y - (section.getOriginY() + 8 + offsetY);
        double distanceZ = origin.z - (section.getOriginZ() + 8 + offsetZ);

        // cylindrical
        if (SodiumClientMod.options().performance.useFogOcclusion &&
                MathHelper.approximatelyEquals(RenderSystem.getShaderFogColor()[3], 1.0f)) // The fog must be fully opaque in order to skip rendering of chunks behind it
        {
            return Math.max((distanceX * distanceX) + (distanceZ * distanceZ), distanceY * distanceY);
        } else {
            return (distanceX * distanceX) + (distanceZ * distanceZ);
        }
    }

    @Unique
    private double getEffectiveRenderDistanceDouble() {
        var color = RenderSystem.getShaderFogColor();
        var distance = RenderSystem.getShaderFogEnd();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (!MathHelper.approximatelyEquals(color[3], 1.0f)) {
            return this.renderDistance * 16.0D;
        }

        return Math.max(16.0D, distance);
    }
}
