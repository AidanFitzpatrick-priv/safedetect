package net.minecraft.util;

public class BlockPos extends Vec3i {
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BlockPos)) {
            return false;
        }
        BlockPos pos = (BlockPos) other;
        return getX() == pos.getX() && getY() == pos.getY() && getZ() == pos.getZ();
    }

    @Override
    public int hashCode() {
        return getX() * 31 * 31 + getY() * 31 + getZ();
    }
}
