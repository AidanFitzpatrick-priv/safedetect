package net.minecraft.block.state;

import net.minecraft.block.Block;

public class IBlockState {
    private final Block block;

    public IBlockState(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }
}
