package com.tntoptimizer.explosion;

import org.bukkit.Location;

import java.util.*;

/**
 * 空间聚类器 — 将密集 TNT 按爆炸重叠范围智能分组。
 * <p>
 * 核心思路：使用 Union-Find 将空间中距离足够近的 TNT 归为同一簇。
 * 同一簇内的 TNT 爆炸范围重叠，必须串行处理（不能并行）；
 * 不同簇之间互不重叠，可以安全并行。
 * <p>
 * 复杂度：O(n²) 在最坏情况下，n 为 TNT 数量（通常 < 100）。
 */
public final class SpatialClusterer {

    private SpatialClusterer() {}

    /**
     * TNT 信息（用于聚类）
     *
     * @param location    TNT 位置
     * @param power       爆炸威力（半径）
     * @param sourceUuid  引爆 TNT 实体的 UUID（用于触发 EntityExplodeEvent 兼容事件）
     */
    public record TNTInfo(Location location, float power, UUID sourceUuid) {
        public double distanceTo(TNTInfo other) {
            return location.distance(other.location);
        }
    }

    /**
     * 聚类结果
     */
    public static class Cluster {
        public final List<TNTInfo> members;
        /** 聚类的总爆炸范围（中心 + 半径） */
        public final Location center;
        public final double radius;

        Cluster(List<TNTInfo> members) {
            this.members = List.copyOf(members);
            // 计算聚类中心
            double cx = 0, cy = 0, cz = 0;
            for (TNTInfo t : members) {
                cx += t.location.getX();
                cy += t.location.getY();
                cz += t.location.getZ();
            }
            int n = members.size();
            this.center = members.get(0).location.clone();
            this.center.setX(cx / n);
            this.center.setY(cy / n);
            this.center.setZ(cz / n);

            // 计算聚类半径（最远 TNT 距离 + 该 TNT 的爆炸半径）
            double maxDist = 0;
            for (TNTInfo t : members) {
                double d = center.distance(t.location) + t.power * 1.3;
                if (d > maxDist) maxDist = d;
            }
            this.radius = maxDist;
        }
    }

    /**
     * 对 TNT 列表进行空间聚类
     *
     * @param tntList           待聚类的 TNT 列表
     * @param distanceMultiplier 聚类距离倍率（实际距离 = max(power) * multiplier）
     * @param maxClusterSize    最大聚类大小
     * @return 聚类列表
     */
    public static List<Cluster> cluster(
            List<TNTInfo> tntList,
            double distanceMultiplier,
            int maxClusterSize
    ) {
        int n = tntList.size();

        if (n <= 1) {
            return n == 0
                    ? List.of()
                    : List.of(new Cluster(List.of(tntList.get(0))));
        }

        // Union-Find
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // O(n²) 两两比较
        for (int i = 0; i < n; i++) {
            TNTInfo a = tntList.get(i);
            for (int j = i + 1; j < n; j++) {
                TNTInfo b = tntList.get(j);
                double threshold = Math.max(a.power, b.power) * distanceMultiplier;
                if (a.distanceTo(b) <= threshold) {
                    union(parent, i, j);
                }
            }
        }

        // 按根节点分组
        Map<Integer, List<TNTInfo>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(tntList.get(i));
        }

        // 构建聚类列表，处理超大聚类
        List<Cluster> clusters = new ArrayList<>();
        for (List<TNTInfo> group : groups.values()) {
            if (group.size() > maxClusterSize) {
                // 超大聚类拆分为多个子聚类
                clusters.addAll(splitOversizedCluster(group, maxClusterSize, distanceMultiplier));
            } else {
                clusters.add(new Cluster(group));
            }
        }

        return clusters;
    }

    /**
     * 拆分超大聚类为多个子聚类
     */
    private static List<Cluster> splitOversizedCluster(
            List<TNTInfo> group, int maxSize, double distanceMultiplier
    ) {
        List<Cluster> subClusters = new ArrayList<>();
        for (int i = 0; i < group.size(); i += maxSize) {
            int end = Math.min(i + maxSize, group.size());
            subClusters.add(new Cluster(group.subList(i, end)));
        }
        return subClusters;
    }

    // ──────────── Union-Find ────────────

    private static int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]); // 路径压缩
        }
        return parent[x];
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}