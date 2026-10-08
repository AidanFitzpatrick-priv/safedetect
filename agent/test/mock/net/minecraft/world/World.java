package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class World {
    public final List<EntityPlayer> playerEntities = new ArrayList<EntityPlayer>();
    public final Map<BlockPos, Block> blocks = new HashMap<BlockPos, Block>();

    public IBlockState getBlockState(BlockPos pos) {
        Block block = blocks.get(pos);
        return new IBlockState(block == null ? Blocks.air : block);
    }

    public MovingObjectPosition rayTraceBlocks(Vec3 start, Vec3 end) {
        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        for (int i = 1; i <= 40; i++) {
            double t = i / 40.0;
            int x = (int) Math.floor(start.xCoord + dx * t);
            int y = (int) Math.floor(start.yCoord + dy * t);
            int z = (int) Math.floor(start.zCoord + dz * t);
            BlockPos pos = new BlockPos(x, y, z);
            Block block = blocks.get(pos);
            if (block != null && block != Blocks.air) {
                return new MovingObjectPosition(pos);
            }
        }
        return null;
    }
}
