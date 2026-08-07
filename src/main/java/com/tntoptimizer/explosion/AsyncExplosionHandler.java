package com.tntoptimizer.explosion;

import com.tntoptimizer.TNTOptimizer;
import com.tntoptimizer.config.PluginConfig;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * 异步爆炸处理器 — 基于 Minecraft 26.2 反编译验证的真实爆炸机制。
 * <p>
 * 原版机制还原：
 *   - 1352 条射线 × 0.225 衰减 × (resist+0.3)*0.3 吸收
 *   - 实体伤害 (pow²+pow)/2 * 7 * 2R + 1，exposure 遮蔽计算
 *   - 击退 (1-dist) * exposure * (1-knockbackResist)
 *   - TNT 方块被炸 → PrimedTnt fuse 10~29 tick + 初速度 + 重力 0.04
 *   - EntityExplodeEvent 兼容触发
 * <p>
 * 多线程管线（v3.0 FIX — 区域线程零阻塞）：
 *   区域线程: 快照采集 + 实体收集（<1ms），立即返回不阻塞 tick
 *   异步线程池: bake + 射线追踪 + 实体伤害 全并行
 *   跨区写回: 按 Region 分组，各区域线程并行破坏
 */
public class AsyncExplosionHandler {

    private final TNTOptimizer plugin;
    private final RegionScheduler regionScheduler;
    private ExecutorService asyncPool;

    private final ConcurrentHashMap<String, List<SpatialClusterer.TNTInfo>> regionQueues;
    private final Set<String> pendingRegions = ConcurrentHashMap.newKeySet();

    private volatile PluginConfig config;

    private static final float STEP = 0.3f;
    private static final int RAY_GRID = 16;
    private static final float POWER_DECAY_PER_STEP = 0.22500001f;
    private static final float TNT_POWER = 4.0f;

    private static final double[][] RAY_DIRECTIONS;
    private static final int FULL_RAY_COUNT;
    static {
        List<double[]> dirs = new ArrayList<>();
        for (int tx = 0; tx < RAY_GRID; tx++) {
            for (int ty = 0; ty < RAY_GRID; ty++) {
                for (int tz = 0; tz < RAY_GRID; tz++) {
                    if (tx != 0 && tx != RAY_GRID - 1
                            && ty != 0 && ty != RAY_GRID - 1
                            && tz != 0 && tz != RAY_GRID - 1) continue;
                    double dx = (tx / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double dy = (ty / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double dz = (tz / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dirs.add(new double[]{dx / d, dy / d, dz / d});
                }
            }
        }
        RAY_DIRECTIONS = dirs.toArray(new double[0][]);
        FULL_RAY_COUNT = RAY_DIRECTIONS.length;
    }

    public AsyncExplosionHandler(TNTOptimizer plugin) {
        this.plugin = plugin;
        this.config = plugin.getPluginConfig();
        this.regionScheduler = Bukkit.getRegionScheduler();
        this.regionQueues = new ConcurrentHashMap<>();
        initThreadPool();
    }

    private void initThreadPool() {
        int cores = Runtime.getRuntime().availableProcessors();
        int configured = config.getAsyncThreadPoolSize();
        int poolSize = configured > 0 ? Math.min(configured, 64)
                : Math.min(cores, Math.max(4, cores * 3 / 4));
        this.asyncPool = new ForkJoinPool(poolSize, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, true);
        plugin.getLogger().info("异步线程池: " + poolSize + " 线程 (work-stealing, "
                + cores + " 核心" + (cores > 16 ? ", 疑似双路" : "") + ")");
    }

    public void reloadConfig() {
        this.config = plugin.getPluginConfig();
        if (asyncPool != null) {
            asyncPool.shutdownNow(); // 立即终止旧任务，避免新旧池同时运行
            try { asyncPool.awaitTermination(2, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
        }
        initThreadPool();
    }

    public void shutdown() {
        if (asyncPool != null) {
            asyncPool.shutdown();
            try { asyncPool.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { asyncPool.shutdownNow(); }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 入口
    // ═══════════════════════════════════════════════════════════════════════

    public void handleExplosion(Location loc, World world, float power, UUID sourceUuid) {
        if (!plugin.isOptimizationEnabled()) return;
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        String regionKey = world.getName() + ":" + (loc.getBlockX() >> 9) + ":" + (loc.getBlockZ() >> 9);
        regionQueues.computeIfAbsent(regionKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new SpatialClusterer.TNTInfo(loc.clone(), power, sourceUuid));

        if (pendingRegions.add(regionKey)) {
            regionScheduler.execute(plugin, world, loc.getBlockX() >> 4, loc.getBlockZ() >> 4,
                    () -> processRegion(world, regionKey));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 区域线程（最小化占用 → 立即返回，不阻塞 tick）
    // ═══════════════════════════════════════════════════════════════════════

    private void processRegion(World world, String regionKey) {
        try {
            pendingRegions.remove(regionKey);
            List<SpatialClusterer.TNTInfo> tntList = regionQueues.remove(regionKey);
            if (tntList == null || tntList.isEmpty()) return;
            tntList.removeIf(t -> t.location() == null);
            if (tntList.isEmpty()) return;

            long startNanos = System.nanoTime();

            // 1. 空间聚类
            List<SpatialClusterer.Cluster> clusters = buildClusters(tntList);

            // 2. 区域线程最小化：只做快照采集 + 实体收集（bake 移到异步池）
            List<ClusterSnapshot> snapshots = new ArrayList<>();
            double maxEntityRadius = 0;
            Location searchCenter = null;
            for (SpatialClusterer.Cluster cluster : clusters) {
                int radius = Math.max(config.getSnapshotRadius(), (int) Math.ceil(cluster.radius) + 2);
                snapshots.add(new ClusterSnapshot(cluster, captureChunks(world, cluster.center, radius), new ArrayList<>()));
                if (cluster.radius * 2.0 > maxEntityRadius) {
                    maxEntityRadius = cluster.radius * 2.0;
                    searchCenter = cluster.center;
                }
            }

            // 合并实体扫描：一次 getNearbyEntities 覆盖全部集群
            if (searchCenter != null && maxEntityRadius > 0) {
                try {
                    List<EntitySnapshot> allEntities = new ArrayList<>();
                    for (Entity e : world.getNearbyEntities(searchCenter, maxEntityRadius, maxEntityRadius, maxEntityRadius))
                        allEntities.add(EntitySnapshot.from(e));
                    for (int i = 0; i < snapshots.size(); i++) {
                        ClusterSnapshot cs = snapshots.get(i);
                        double r = clusters.get(i).radius * 2.0;
                        Location cc = clusters.get(i).center;
                        List<EntitySnapshot> filtered = new ArrayList<>();
                        for (EntitySnapshot es : allEntities)
                            if (es.location.distance(cc) <= r) filtered.add(es);
                        snapshots.set(i, new ClusterSnapshot(cs.cluster, cs.snapshot, filtered));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[TNT-Optimizer] 实体收集失败: " + e.getMessage());
                }
            }

            if (config.isDebug())
                plugin.getLogger().info("[DEBUG] processRegion START: " + regionKey
                        + " TNT=" + tntList.size() + " clusters=" + snapshots.size());

            // 3. 异步管线：bake + 射线 + 实体伤害（全部异步池，区域线程立即返回）
            long asyncStart = System.nanoTime();
            List<CompletableFuture<ClusterResult>> futures = new ArrayList<>();
            for (ClusterSnapshot cs : snapshots)
                futures.add(submitClusterPipeline(cs));

            int cx = tntList.get(0).location().getBlockX() >> 4;
            int cz = tntList.get(0).location().getBlockZ() >> 4;

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        long asyncDone = System.nanoTime();
                        List<ClusterResult> results = new ArrayList<>();
                        for (CompletableFuture<ClusterResult> f : futures) {
                            ClusterResult r = f.getNow(null);
                            if (r != null) results.add(r);
                        }
                        regionScheduler.execute(plugin, world, cx, cz, () ->
                                applyResults(world, results, startNanos, asyncStart, asyncDone, cx, cz));
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "[TNT-Optimizer] 异步管线异常", ex);
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[TNT-Optimizer] processRegion 异常", e);
        }
    }

    private List<SpatialClusterer.Cluster> buildClusters(List<SpatialClusterer.TNTInfo> tntList) {
        if (config.isClusteringEnabled() && tntList.size() >= config.getMinTNTCountForClustering())
            return SpatialClusterer.cluster(tntList, config.getClusterDistanceMultiplier(), config.getMaxClusterSize());
        List<SpatialClusterer.Cluster> list = new ArrayList<>();
        for (SpatialClusterer.TNTInfo info : tntList)
            list.add(new SpatialClusterer.Cluster(List.of(info)));
        return list;
    }

    /**
     * 提交单个集群的完整异步管线：bake → 射线追踪 → 实体伤害。
     * bake 从区域线程移到异步池（ChunkSnapshot.getBlockType 线程安全），
     * 把区域线程的阻塞时间从 ~5ms/集群 降到 ~0.5ms/集群。
     */
    private CompletableFuture<ClusterResult> submitClusterPipeline(ClusterSnapshot cs) {
        int bakeRadius = Math.max(config.getSnapshotRadius(), (int) Math.ceil(cs.cluster.radius) + 2);
        return CompletableFuture.supplyAsync(() -> {
            cs.snapshot.bake(cs.cluster.center.getBlockX(), cs.cluster.center.getBlockY(),
                    cs.cluster.center.getBlockZ(), bakeRadius);
            return cs;
        }, asyncPool).thenCompose(bakedCs -> {
            // 射线追踪
            List<CompletableFuture<Set<Long>>> rayFutures = new ArrayList<>();
            for (SpatialClusterer.TNTInfo tnt : bakedCs.cluster.members) {
                int maxRays = Math.min(computeRayCount(tnt.power()), FULL_RAY_COUNT);
                int poolSize = asyncPool instanceof ForkJoinPool fjp ? fjp.getParallelism() : 4;
                int batchCount = Math.min(poolSize * 2, maxRays);
                int batchSize = Math.max(1, (maxRays + batchCount - 1) / batchCount);
                for (int start = 0; start < maxRays; start += batchSize) {
                    final int s = start, e = Math.min(start + batchSize, maxRays);
                    final SpatialClusterer.TNTInfo ti = tnt;
                    final long seed = (long) bakedCs.cluster.center.hashCode()
                            ^ (start * 0x9E3779B97F4A7C15L)
                            ^ (ti.hashCode() * 0x6A09E667F3BCC909L);
                    rayFutures.add(CompletableFuture.supplyAsync(() ->
                            computeRayBatch(bakedCs.snapshot, ti, s, e, seed), asyncPool));
                }
            }
            // 实体伤害
            List<CompletableFuture<List<EntityDamageInfo>>> entityFutures = new ArrayList<>();
            int entityCount = bakedCs.entities.size();
            if (entityCount > 0) {
                if (entityCount <= 20) {
                    entityFutures.add(CompletableFuture.supplyAsync(() ->
                            computeEntityDamageBatch(bakedCs, 0, entityCount), asyncPool));
                } else {
                    int poolSize = asyncPool instanceof ForkJoinPool fjp ? fjp.getParallelism() : 4;
                    int bs = Math.max(10, entityCount / poolSize);
                    for (int start = 0; start < entityCount; start += bs) {
                        final int s = start, e = Math.min(start + bs, entityCount);
                        entityFutures.add(CompletableFuture.supplyAsync(() ->
                                computeEntityDamageBatch(bakedCs, s, e), asyncPool));
                    }
                }
            }
            return CompletableFuture.allOf(
                    CompletableFuture.allOf(rayFutures.toArray(new CompletableFuture[0])),
                    CompletableFuture.allOf(entityFutures.toArray(new CompletableFuture[0]))
            ).thenApply(v -> {
                Set<Long> destroyed = new HashSet<>();
                for (var f : rayFutures) destroyed.addAll(f.getNow(Set.of()));
                List<EntityDamageInfo> damages = new ArrayList<>();
                for (var f : entityFutures) damages.addAll(f.getNow(List.of()));
                return new ClusterResult(bakedCs.cluster, destroyed, damages);
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 区块快照
    // ═══════════════════════════════════════════════════════════════════════

    private WorldSnapshot captureChunks(World world, Location center, int radius) {
        WorldSnapshot snapshot = new WorldSnapshot(world);
        int minCX = (center.getBlockX() - radius) >> 4;
        int maxCX = (center.getBlockX() + radius) >> 4;
        int minCZ = (center.getBlockZ() - radius) >> 4;
        int maxCZ = (center.getBlockZ() + radius) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cz = minCZ; cz <= maxCZ; cz++)
                if (world.isChunkLoaded(cx, cz))
                    snapshot.addChunk(cx, cz, world.getChunkAt(cx, cz).getChunkSnapshot());
        return snapshot;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 异步计算
    // ═══════════════════════════════════════════════════════════════════════

    private Set<Long> computeRayBatch(WorldSnapshot snapshot, SpatialClusterer.TNTInfo tnt,
                                      int rayStart, int rayEnd, long seed) {
        Set<Long> destroyed = new HashSet<>();
        Random random = new Random(seed);
        Location center = tnt.location();
        float power = tnt.power();
        double cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int i = rayStart; i < rayEnd; i++) {
            double[] dir = RAY_DIRECTIONS[i];
            double dx = dir[0], dy = dir[1], dz = dir[2];
            float rayPower = power * (0.7f + random.nextFloat() * 0.6f);
            double x = cx, y = cy, z = cz;

            while (rayPower > 0.0f) {
                int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                if (snapshot.isOutOfWorld(by)) break;
                float absorption = snapshot.getAbsorption(bx, by, bz);
                if (absorption > 0) rayPower -= absorption;
                if (rayPower > 0.0f) destroyed.add(RayTracer.packKey(bx, by, bz));
                rayPower -= POWER_DECAY_PER_STEP;
                x += dx * STEP;
                y += dy * STEP;
                z += dz * STEP;
            }
        }
        return destroyed;
    }

    private List<EntityDamageInfo> computeEntityDamageBatch(ClusterSnapshot cs,
                                                             int entityStart, int entityEnd) {
        List<EntityDamageInfo> list = new ArrayList<>(entityEnd - entityStart);
        for (int ei = entityStart; ei < entityEnd; ei++) {
            EntitySnapshot entity = cs.entities.get(ei);
            for (SpatialClusterer.TNTInfo tnt : cs.cluster.members) {
                Location center = tnt.location();
                float power = tnt.power();
                float doubleRadius = power * 2.0f;
                double dist = entity.location.distance(center) / doubleRadius;
                if (dist > 1.0) continue;

                float exposure = (dist < 0.05f || entity.isTNT) ? 1.0f
                        : getSeenPercent(cs.snapshot, entity, center);
                double pow = (1.0 - dist) * exposure;
                float damage = (float) ((pow * pow + pow) / 2.0 * 7.0 * doubleRadius + 1.0);

                Location origin = entity.isTNT ? entity.location : entity.eyeLocation;
                Vector direction = origin.toVector().subtract(center.toVector());
                double len = direction.length();
                if (len < 0.001) direction = new Vector(0, 1, 0);
                else direction.multiply(1.0 / len);
                double knockbackPower = (1.0 - dist) * exposure * (1.0 - entity.knockbackResistance);
                // 创建新 Vector，避免原地 multiply 污染后续实体的方向计算
                Vector knockback = new Vector(
                        direction.getX() * knockbackPower,
                        direction.getY() * knockbackPower,
                        direction.getZ() * knockbackPower
                );

                list.add(new EntityDamageInfo(entity, damage, knockback));
            }
        }
        return list;
    }

    private float getSeenPercent(WorldSnapshot snapshot, EntitySnapshot entity, Location center) {
        double xs = 1.0 / ((entity.boundingBoxMaxX - entity.boundingBoxMinX) * 2.0 + 1.0);
        double ys = 1.0 / ((entity.boundingBoxMaxY - entity.boundingBoxMinY) * 2.0 + 1.0);
        double zs = 1.0 / ((entity.boundingBoxMaxZ - entity.boundingBoxMinZ) * 2.0 + 1.0);
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) return 0.0f;

        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
        int hits = 0, count = 0;

        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    double px = entity.boundingBoxMinX + (entity.boundingBoxMaxX - entity.boundingBoxMinX) * xx + xOffset;
                    double py = entity.boundingBoxMinY + (entity.boundingBoxMaxY - entity.boundingBoxMinY) * yy;
                    double pz = entity.boundingBoxMinZ + (entity.boundingBoxMaxZ - entity.boundingBoxMinZ) * zz + zOffset;
                    if (isLineOfSightClear(snapshot, px, py, pz, center.getX(), center.getY(), center.getZ()))
                        hits++;
                    count++;
                }
            }
        }
        return count > 0 ? (float) hits / count : 0.0f;
    }

    private boolean isLineOfSightClear(WorldSnapshot snapshot, double x0, double y0, double z0,
                                        double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001) return true;
        double stepX = dx / dist * 0.3, stepY = dy / dist * 0.3, stepZ = dz / dist * 0.3;
        int steps = (int) Math.ceil(dist / 0.3);
        double px = x0, py = y0, pz = z0;
        for (int i = 0; i < steps; i++) {
            if (snapshot.getAbsorption((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz)) > 0.0f)
                return false;
            px += stepX; py += stepY; pz += stepZ;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 区域线程写回
    // ═══════════════════════════════════════════════════════════════════════

    private void applyResults(World world, List<ClusterResult> clusterResults,
                              long startNanos, long asyncStart, long asyncDone,
                              int anchorChunkX, int anchorChunkZ) {
        Map<Long, List<Long>> blocksByRegion = new HashMap<>();

        for (ClusterResult cr : clusterResults) {
            List<Long> effective = fireEntityExplodeEvent(world, cr);
            for (long key : effective) {
                int[] pos = RayTracer.unpackKey(key);
                blocksByRegion.computeIfAbsent(packRegionKey(pos[0], pos[2]), k -> new ArrayList<>()).add(key);
            }
            spawnExplosionParticles(world, cr.cluster.center);
            world.playSound(cr.cluster.center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                    4.0f, (float) ((1.0f + (Math.random() - Math.random()) * 0.2f) * 0.7f));
            for (EntityDamageInfo info : cr.damageInfos)
                applyEntityDamage(info);
        }

        int destroyedTotal = 0;
        for (List<Long> l : blocksByRegion.values()) destroyedTotal += l.size();
        scheduleWriteback(world, blocksByRegion, anchorChunkX, anchorChunkZ,
                clusterResults.size(), destroyedTotal, startNanos, asyncStart, asyncDone);
    }

    private void scheduleWriteback(World world, Map<Long, List<Long>> blocksByRegion,
                                   int anchorChunkX, int anchorChunkZ, int clusterCount,
                                   int destroyedTotal, long startNanos, long asyncStart, long asyncDone) {
        if (blocksByRegion.isEmpty()) {
            finishMetrics(clusterCount, 0, startNanos, asyncStart, asyncDone);
            return;
        }

        boolean single = blocksByRegion.size() == 1;
        boolean inline = (single && !config.isMultiRegionWriteback())
                || (single && blocksByRegion.containsKey(packRegionKey(anchorChunkX << 4, anchorChunkZ << 4)));

        if (inline) {
            destroyBlocksInRegion(world, blocksByRegion.values().iterator().next());
            finishMetrics(clusterCount, destroyedTotal, startNanos, asyncStart, asyncDone);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(blocksByRegion.size());
        for (Map.Entry<Long, List<Long>> e : blocksByRegion.entrySet()) {
            List<Long> keys = e.getValue();
            int[] first = RayTracer.unpackKey(keys.get(0));
            regionScheduler.execute(plugin, world, first[0] >> 4, first[2] >> 4, () -> {
                try {
                    destroyBlocksInRegion(world, keys);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "[TNT-Optimizer] 区域写回异常", ex);
                } finally {
                    if (remaining.decrementAndGet() == 0)
                        finishMetrics(clusterCount, destroyedTotal, startNanos, asyncStart, asyncDone);
                }
            });
        }
    }

    private List<Long> fireEntityExplodeEvent(World world, ClusterResult cr) {
        List<Long> original = new ArrayList<>(cr.destroyedBlockKeys);
        if (original.isEmpty()) return original;

        if (!config.isFireEntityExplodeEvent()) return original;

        // TNT 实体在 listener 中已被移除，需要临时生成一个来触发 EntityExplodeEvent
        UUID src = cr.cluster.members.isEmpty() ? null : cr.cluster.members.get(0).sourceUuid();
        Entity source = src != null ? Bukkit.getEntity(src) : null;

        // 如果原实体还在就用它，否则临时生成一个
        boolean tempSpawned = false;
        if (source == null || !source.isValid()) {
            source = world.spawn(cr.cluster.center, TNTPrimed.class);
            ((TNTPrimed) source).setFuseTicks(0); // 标记为即将爆炸
            tempSpawned = true;
        }

        List<Block> blocks = new ArrayList<>(original.size());
        for (long key : original) {
            int[] p = RayTracer.unpackKey(key);
            blocks.add(world.getBlockAt(p[0], p[1], p[2]));
        }
        EntityExplodeEvent event = new EntityExplodeEvent(
                source, source.getLocation().clone(), blocks, 0.333F, null);
        Bukkit.getPluginManager().callEvent(event);

        // 触发完事件后移除临时实体（非临时实体不删除）
        if (tempSpawned) source.remove();

        if (event.isCancelled()) return List.of();
        List<Long> effective = new ArrayList<>();
        for (Block b : event.blockList())
            if (b.getWorld().equals(world))
                effective.add(RayTracer.packKey(b.getX(), b.getY(), b.getZ()));
        return effective;
    }

    private void destroyBlocksInRegion(World world, List<Long> keys) {
        // 拷贝后打乱，避免修改共享列表 + 保证随机顺序
        List<Long> shuffled = new ArrayList<>(keys);
        Collections.shuffle(shuffled);

        for (long key : shuffled) {
            int[] pos = RayTracer.unpackKey(key);
            int bx = pos[0], by = pos[1], bz = pos[2];
            if (by < world.getMinHeight() || by >= world.getMaxHeight()) continue;

            Block block = world.getBlockAt(bx, by, bz);
            Material mat = block.getType();
            if (!canExplosionDestroy(mat)) continue;

            if (mat == Material.TNT) {
                Location tntLoc = new Location(world, bx + 0.5, by, bz + 0.5);
                block.setType(Material.AIR, false);
                TNTPrimed primed = world.spawn(tntLoc, TNTPrimed.class);
                primed.setFuseTicks(10 + ThreadLocalRandom.current().nextInt(20));
                double rot = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                primed.setVelocity(new Vector(-Math.sin(rot) * 0.02, 0.2, -Math.cos(rot) * 0.02));
                continue;
            }
            destroyWithDrops(world, block, bx, by, bz);
        }
    }

    private void destroyWithDrops(World world, Block block, int bx, int by, int bz) {
        if (config.getDropMode() == PluginConfig.DropMode.FULL) {
            block.breakNaturally();
            return;
        }
        Collection<ItemStack> drops = block.getDrops();
        block.setType(Material.AIR, false);
        if (drops.isEmpty()) return;
        float keepChance = 1.0f / TNT_POWER;
        for (ItemStack drop : drops) {
            int kept = 0;
            for (int i = 0; i < drop.getAmount(); i++)
                if (ThreadLocalRandom.current().nextFloat() < keepChance) kept++;
            if (kept > 0) {
                ItemStack stack = drop.clone();
                stack.setAmount(kept);
                world.dropItemNaturally(new Location(world, bx + 0.5, by + 0.5, bz + 0.5), stack);
            }
        }
    }

    private void finishMetrics(int clusterCount, int destroyed,
                               long startNanos, long asyncStart, long asyncDone) {
        long now = System.nanoTime();
        plugin.recordExplosion(now - startNanos);
        if (config.isDebug()) {
            double asyncMs = (asyncDone - asyncStart) / 1_000_000.0;
            double writebackMs = (now - asyncDone) / 1_000_000.0;
            double totalMs = (now - startNanos) / 1_000_000.0;
            plugin.getLogger().info(String.format(
                    "[DEBUG] DONE — %d 簇 / %d 方块 / 异步 %.2fms + 写回 %.2fms = 总计 %.2fms",
                    clusterCount, destroyed, asyncMs, writebackMs, totalMs));
        }
        if (config.isMetricsEnabled() && plugin.getTotalProcessed() % config.getMetricsLogInterval() == 0) {
            double avgMs = (plugin.getTotalTimeNanos() / (double) plugin.getTotalProcessed()) / 1_000_000.0;
            plugin.getLogger().info(String.format("[Metrics] 已处理 %d 次爆炸, 平均耗时 %.2fms",
                    plugin.getTotalProcessed(), avgMs));
        }
    }

    private static long packRegionKey(int x, int z) {
        return ((long) (x >> 9) << 32) | ((z >> 9) & 0xFFFFFFFFL);
    }

    private void applyEntityDamage(EntityDamageInfo info) {
        Entity entity = Bukkit.getEntity(info.entity.uuid);
        if (entity == null || !entity.isValid()) return;
        entity.getScheduler().execute(plugin, () -> {
            if (!entity.isValid()) return;
            entity.setVelocity(entity.getVelocity().add(info.knockback));
            if (info.entity.isLiving && entity instanceof LivingEntity living)
                living.damage(info.damage);
        }, null, 0L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════════════════

    private int computeRayCount(float power) {
        return switch (config.getRayMode()) {
            case NORMAL -> 1352;
            case REDUCED -> 512;
            case ADAPTIVE -> Math.min((int) (power * config.getAdaptiveRayMultiplier()), config.getMaxRays());
        };
    }

    private boolean canExplosionDestroy(Material mat) {
        if (mat.isAir()) return false;
        return mat != Material.BEDROCK && mat != Material.BARRIER
                && mat != Material.COMMAND_BLOCK && mat != Material.CHAIN_COMMAND_BLOCK
                && mat != Material.REPEATING_COMMAND_BLOCK && mat != Material.STRUCTURE_BLOCK
                && mat != Material.JIGSAW && mat != Material.LIGHT;
    }

    private void spawnExplosionParticles(World world, Location center) {
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, center, 10, 1.5, 1.5, 1.5, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 8, 1.0, 1.0, 1.0, 0.05);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 内部数据类
    // ═══════════════════════════════════════════════════════════════════════

    private record ClusterSnapshot(
            SpatialClusterer.Cluster cluster, WorldSnapshot snapshot, List<EntitySnapshot> entities) {}

    public record ClusterResult(
            SpatialClusterer.Cluster cluster, Set<Long> destroyedBlockKeys, List<EntityDamageInfo> damageInfos) {}

    private record EntitySnapshot(
            UUID uuid, Location location, Location eyeLocation,
            boolean isTNT, boolean isLiving, double knockbackResistance,
            double boundingBoxMinX, double boundingBoxMinY, double boundingBoxMinZ,
            double boundingBoxMaxX, double boundingBoxMaxY, double boundingBoxMaxZ) {
        static EntitySnapshot from(Entity e) {
            Location loc = e.getLocation().clone();
            Location eye = e instanceof LivingEntity le ? le.getEyeLocation().clone() : loc.clone();
            org.bukkit.util.BoundingBox bb = e.getBoundingBox();
            double kr = 0.0;
            if (e instanceof LivingEntity le) {
                try { kr = le.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE).getValue(); }
                catch (Exception ignored) {}
            }
            return new EntitySnapshot(e.getUniqueId(), loc, eye, e instanceof TNTPrimed,
                    e instanceof LivingEntity, kr,
                    bb.getMinX(), bb.getMinY(), bb.getMinZ(), bb.getMaxX(), bb.getMaxY(), bb.getMaxZ());
        }
    }

    private record EntityDamageInfo(EntitySnapshot entity, float damage, Vector knockback) {}
}