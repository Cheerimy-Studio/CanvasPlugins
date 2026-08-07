# TNT-Optimizer Release Notes

## v3.3.0 — 2026-08-07

### Bug Fixes
- **EntityExplodeEvent: 修复非临时实体被误删** — ireEntityExplodeEvent 中 else source.remove() 会删除仍然存活的 TNT 实体，已修复为仅删除临时生成的实体
- **作者署名修正** — plugin.yml 中 uthor 从 Canvas Core Team 改为 Cheerimy

### No Code Changes from v3.2.0 (除上述修复)
- WorldSnapshot isBlock() 防护：已包含
- int 溢出防护：已包含
- 击退 Vector 污染：已包含
- reloadConfig 线程池泄漏：已包含
- 射线 Random seed 碰撞：已包含

## v3.2.0 — 2026-08-07

### Initial Release
- 异步射线追踪引擎（ForkJoinPool work-stealing）
- 空间聚类（Union-Find 分组并行）
- WorldSnapshot 扁平数组 O(1) 查询
- 跨区域并行写回
- EntityExplodeEvent 兼容
- 自适应射线模式
- 原版掉落 / 全掉落模式
