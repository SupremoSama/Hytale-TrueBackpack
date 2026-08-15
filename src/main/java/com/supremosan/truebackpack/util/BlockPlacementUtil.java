package com.supremosan.truebackpack.util;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import javax.annotation.Nonnull;

public final class BlockPlacementUtil {

    private BlockPlacementUtil() {
    }

    public static int getRotationIndex(@Nonnull World world, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null) return RotationTuple.NONE_INDEX;

        BlockSection blockSection = sectionRef.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        if (blockSection == null) return RotationTuple.NONE_INDEX;

        return blockSection.getRotationIndex(x, y, z);
    }

    public static boolean placeBlock(
            @Nonnull World world,
            int x, int y, int z,
            @Nonnull String blockTypeId,
            @Nonnull Rotation yaw,
            @Nonnull Rotation pitch,
            @Nonnull Rotation roll) {

        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeId);
        if (blockType == null) return false;

        int index = BlockType.getAssetMap().getIndex(blockTypeId);
        if (index == AssetMapWithIndexes.NOT_FOUND) return false;

        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null) return false;

        return BlockOperations.setBlock(
                world.getChunkStore(), sectionRef, x, y, z,
                index, blockType,
                RotationTuple.index(yaw, pitch, roll),
                FillerBlockUtil.NO_FILLER,
                SetBlockSettings.NONE);
    }
}
