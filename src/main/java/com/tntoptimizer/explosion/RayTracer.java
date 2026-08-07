package com.tntoptimizer.explosion;

import org.bukkit.Location;

/**
 * 工具方法：坐标打包/解包（用于爆炸结果传输）
 */
public final class RayTracer {

    private RayTracer() {}

    /** 将方块坐标打包为 long key */
    public static long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFF);
    }

    /** 从 long key 解包为方块坐标 [x, y, z] */
    public static int[] unpackKey(long key) {
        int x = (int) (key >> 38) & 0x3FFFFFF;
        if ((x & 0x2000000) != 0) x -= 0x4000000; // sign extend
        int y = (int) (key >> 26) & 0xFFF;
        if ((y & 0x800) != 0) y -= 0x1000;
        int z = (int) key & 0x3FFFFFF;
        if ((z & 0x2000000) != 0) z -= 0x4000000;
        return new int[] { x, y, z };
    }
}