## TNT-Optimizer v3.2

### Features
- Folia / Canvas 26.2 compatible
- Async ray tracing with ForkJoinPool work-stealing
- Spatial clustering for TNT chains
- Multi-region parallel writeback
- Original vanilla explosion formula
- Compatible with EntityExplodeEvent plugins

### Fixes
- NegativeArraySizeException in WorldSnapshot.bake
- Temp TNTPrimed entity for EntityExplodeEvent
- Vector mutation bug in knockback
- Non-blocking region scheduling

### Build
- Gradle 9.2
- Java 21+
- Folia API 1.21.4-R0.1-SNAPSHOT
