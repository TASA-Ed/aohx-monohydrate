# Monohydrate

Monohydrate 是基于 [Finality Framework](https://github.com/Finality-Framework/loader) 的《历史时代 2：DE》（Age of History II: Definitive Edition）性能优化与加载分析插件。

详细设计文档：

- [性能优化原理](docs/optimizations.md)
- [性能分析系统](docs/profiler.md)

## 安装

订阅创意工坊模组（尚未推出），或将模组构建产物放入 `[游戏根目录]/mods/[任意模组目录]/plugins/` 中，随后使用 [Finality Loader](https://github.com/Finality-Framework/loader) 启动游戏。

## JVM 参数

所有优化项与性能分析默认启用。如需禁用特定功能，可在启动命令的 `-jar` 之前添加以下 JVM 系统属性（配置在类首次加载时读取一次，修改后需重启游戏）：

| 参数 | 作用 |
|---|---|
| `-DDisableMonohydrateOptimizations=true` | 优化总开关，禁用 Monohydrate 的全部优化逻辑（优先于各单项开关） |
| `-DDisableMonohydrateCityOptimizations=true` | 禁用城市归属扫描优化 |
| `-DDisableMonohydrateProvinceCandidateFilter=true` | 禁用省份数据、纹理及地图资源的候选模组目录过滤 |
| `-DDisableMonohydrateNativeTextureFormat=true` | 禁用默认纹理格式优化 |
| `-DDisableMonohydrateBorderFontSharing=true` | 禁用文明名称字体共享 |
| `-DDisableCimHeaderRead=true` | 禁用 CIM 头部读取优化 |
| `-DDisableMonohydrateProfiler=true` | 关闭加载性能分析 |

示例：

```bash
java -DDisableMonohydrateOptimizations=true -DDisableMonohydrateProfiler=true -jar loader-1.6.0.jar
```

## 许可证

本项目采用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 开源许可协议。
