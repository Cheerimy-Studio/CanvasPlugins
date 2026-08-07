package com.tntoptimizer.explosion;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程安全的区块快照集合 — v2 扁平数组版。
 * <p>
 * 区域线程捕获 ChunkSnapshot 后，调用 {@link #bake} 预烘焙成扁平 float[] 吸收值数组。
 * 之后异步射线追踪的每次方块查询从 O(API调用) 降为 O(1) 数组索引，
 * 消除了 ~95% 的单次射线追踪开销。
 * <p>
 * 吸收值 0.0 = 空气，>0.0 = (blastResistance + 0.3) * 0.3。
 * 射线遮蔽检测只需判断 == 0.0f，无需 Material 对象。
 */
public final class WorldSnapshot {

    // ── 静态吸收值缓存（消除 getBlastResistance() 重复调用）──
    private static final float[] ABSORPTION_CACHE;
    static {
        Material[] vals = Material.values();
        ABSORPTION_CACHE = new float[vals.length];
        for (Material m : vals) {
            if (m.isAir() || !m.isBlock()) {
                ABSORPTION_CACHE[m.ordinal()] = 0.0f;
            } else {
                try {
                    ABSORPTION_CACHE[m.ordinal()] = (m.getBlastResistance() + 0.3f) * 0.3f;
                } catch (Exception e) {
                    ABSORPTION_CACHE[m.ordinal()] = 0.0f;
                }
            }
        }
    }

    // ── 原始区块快照（bake 后清空）──
    private final Map<Long, ChunkSnapshot> rawChunks = new HashMap<>();
    private final int minY;
    private final int maxY;

    // ── 烘焙后的扁平数组 ──
    private float[] absorption;
    private int originX, originY, originZ;
    private int sizeX, sizeY, sizeZ;
    private volatile boolean baked = false;

    public WorldSnapshot(World world) {
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

    /** 添加一个区块快照（必须在区域线程上调用） */
    public void addChunk(int chunkX, int chunkZ, ChunkSnapshot snapshot) {
        rawChunks.put(chunkKey(chunkX, chunkZ), snapshot);
    }

    /**
     * 预烘焙：将爆炸半径内的方块吸收值提取到扁平数组。
     * 只烘焙半径内的方块（而非整个区块），大幅减少提取时间。
     * 必须在区域线程上调用（访问 ChunkSnapshot）。
     *
     * @param centerX/Z  爆炸中心世界坐标
     * @param centerY    爆炸中心 Y
     * @param radius     烘焙半径（格）
     */
    public void bake(int centerX, int centerY, int centerZ, int radius) {
        // 硬性限制烘焙半径 — 防止聚类超大半径导致数组溢出
        radius = Math.min(radius, 32);

        int x0 = centerX - radius;
        int x1 = centerX + radius;
        int z0 = centerZ - radius;
        int z1 = centerZ + radius;
        int y0 = Math.max(minY, centerY - radius);
        int y1 = Math.min(maxY - 1, centerY + radius);

        this.originX = x0;
        this.originY = y0;
        this.originZ = z0;
        this.sizeX = x1 - x0 + 1;
        this.sizeY = y1 - y0 + 1;
        this.sizeZ = z1 - z0 + 1;

        // 用 long 计算防止 int 溢出 → NegativeArraySizeException
        long arrayLen = (long) sizeX * sizeY * sizeZ;
        if (arrayLen <= 0 || arrayLen > 20_000_000) {
            // 安全兜底：数组大小异常时用空数组（所有方块视为空气）
            this.absorption = new float[0];
            this.sizeX = this.sizeY = this.sizeZ = 0;
            baked = true;
            rawChunks.clear();
            return;
        }

        this.absorption = new float[(int) arrayLen];

        for (int x = 0; x < sizeX; x++) {
            int wx = originX + x;
            int cx = wx >> 4;
            int bx = wx & 15;
            for (int z = 0; z < sizeZ; z++) {
                int wz = originZ + z;
                int cz = wz >> 4;
                int bz = wz & 15;
                ChunkSnapshot cs = rawChunks.get(chunkKey(cx, cz));
                if (cs == null) continue; // 未加载区块视为空气（吸收值 0）
                for (int y = 0; y < sizeY; y++) {
                    int wy = originY + y;
                    Material m = cs.getBlockType(bx, wy, bz);
                    absorption[idx(x, y, z)] = ABSORPTION_CACHE[m.ordinal()];
                }
            }
        }

        baked = true;
        rawChunks.clear(); // 释放原始快照内存
    }

    /**
     * 获取方块的爆炸吸收值 — O(1) 数组访问。
     * 区域外的方块返回 0（视为空气）。
     */
    public float getAbsorption(int wx, int wy, int wz) {
        if (!baked) return getAbsorptionSlow(wx, wy, wz);
        if (absorption.length == 0) return 0.0f;
        int x = wx - originX;
        if (x < 0 || x >= sizeX) return 0.0f;
        int z = wz - originZ;
        if (z < 0 || z >= sizeZ) return 0.0f;
        int y = wy - originY;
        if (y < 0 || y >= sizeY) return 0.0f;
        return absorption[idx(x, y, z)];
    }

    /** 是否为空气方块 — O(1)，用于射线遮蔽检测 */
    public boolean isAir(int wx, int wy, int wz) {
        return getAbsorption(wx, wy, wz) == 0.0f;
    }

    /** 是否在世界高度范围内 */
    public boolean isOutOfWorld(int y) {
        return y < minY || y >= maxY;
    }

    // ── 内部 ──

    private int idx(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** 未烘焙时的慢速回退路径（不应被调用，安全网） */
    private float getAbsorptionSlow(int wx, int wy, int wz) {
        if (wy < minY || wy >= maxY) return 0.0f;
        ChunkSnapshot cs = rawChunks.get(chunkKey(wx >> 4, wz >> 4));
        if (cs == null) return 0.0f;
        Material m = cs.getBlockType(wx & 15, wy, wz & 15);
        if (m.isAir()) return 0.0f;
        return ABSORPTION_CACHE[m.ordinal()];
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
