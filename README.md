# TNT-Optimizer v2.0.0

Folia 多线程 TNT 爆炸优化插件 — 基于 Minecraft 26.2 反编译验证的真实爆炸机制。

## 特性

### 原版还原（反编译 `mojang_26.2.jar` 验证）
- 1352 条射线 × 0.225 衰减 × `(resist+0.3)*0.3` 吸收
- 实体伤害 `(pow²+pow)/2 * 7 * 2R + 1`，exposure AABB 采样遮蔽
- 击退 `(1-dist) * exposure * (1-knockbackResist)`
- TNT 方块被炸 → `PrimedTnt` fuse 10~29 tick + 初速度 + 重力 0.04 下落
- TNT 实体被炸 → `hurtServer()=false` 不受伤不缩短 fuse，但被击退
- 掉落物 `ApplyExplosionDecay` 衰减（TNT 约 25% 保留）

### 多核优化
- **ForkJoinPool work-stealing**：空闲核心自动偷任务，上限 64 线程
- **细粒度射线批次**：1352 条射线按 8 条/批拆成 ~169 个独立任务
- **实体伤害并行**：按 10 个/批拆分，与射线同时并行
- **跨区域并行写回**：方块按 Region 分组，各区域线程同时破坏
- **扁平数组快照**：`float[]` 吸收值数组，O(1) 射线访问
- **预计算射线方向表**：1352 个方向只算一次，所有任务共享

### 兼容性
- `folia-supported: true`
- 触发真实 `EntityExplodeEvent`，兼容床战争/TNT 大炮/爆炸保护等插件
- 不修改 NMS，仅使用 Bukkit/Paper API
- 与 Canvas 优化层完全解耦

## 安装

1. 将 `TNT-Optimizer-2.0.0.jar` 放入 `plugins/` 目录
2. 首次启动会生成 `plugins/TNT-Optimizer/config.yml`
3. 根据需要调整配置后 `/tntoptimizer reload`

## 命令

| 命令 | 说明 |
|------|------|
| `/tntoptimizer` | 查看状态 |
| `/tntoptimizer reload` | 重载配置 |
| `/tntoptimizer status` | 查看处理统计 |
| `/tntoptimizer toggle` | 开关优化 |

## 配置

```yaml
settings:
  enabled: true
  async-thread-pool-size: 0          # 0=自动(CPU核心数), 正数=自定义(上限64)
  clustering:
    enabled: true
    min-tnt-count: 3
    distance-multiplier: 2.5
    max-cluster-size: 64
  snapshot:
    radius: 8
  explosion:
    ray-mode: ADAPTIVE               # NORMAL(1352) | REDUCED(512) | ADAPTIVE
    adaptive-ray-multiplier: 80
    max-rays: 1352
    fire-entity-explode-event: true  # TNT 插件兼容
    drop-mode: VANILLA               # VANILLA(25%衰减) | FULL(100%)
    multi-region-writeback: true     # 跨区域并行写回
  metrics:
    enabled: true
    log-interval: 100
  debug: false
```

## 构建要求

- JDK 21+
- Gradle 9.2.0+
- Folia API `dev.folia:folia-api:1.21.4-R0.1-SNAPSHOT`
