package com.fabian.xclearlag.managers;

import com.fabian.xclearlag.XClearlag;
import com.fabian.xclearlag.utils.DebugLogger;

/**
 * 依赖管理器（已移除 Libby 运行时下载，Paper 26.2 不需要）。
 */
public class DependencyManager {

    public DependencyManager(XClearlag plugin) {
    }

    public void loadDependencies() {
        DebugLogger.debug("Dependency", "No runtime dependencies to load (Libby removed).");
    }
}
