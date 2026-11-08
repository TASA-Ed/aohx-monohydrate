# Monohydrate

Monohydrate 是基于 [Finality Framework](https://github.com/Finality-Framework/loader) 的《历史时代 2：DE》（Age of History II: Definitive Edition）性能优化与加载分析插件。

详细设计文档：

- [性能优化原理](docs/optimizations.md)
- [性能分析系统](docs/profiler.md)

经过测试，此插件不会破坏原版的任何逻辑。可以与 [CTAP](https://github.com/RedreamR/ctap-plugin) 兼容。

## 优化效果

在我的电脑上进行了 10 次热启动，分两组（开启优化与关闭优化）。由于加载分析模块插桩有性能损耗，因此实际原版中加载耗时可能会有差异！

### 测试环境

- AMD R5 5600H + RTX 3050 Laptop + 16 GB RAM，Windows 11 26H2。
- Monohydrate `0.1.0`、游戏 `2.01`、Finality Loader `1.6.0`；Azul OpenJDK `21.0.13+9-LTS`，G1 GC。
- 地图 `Earth14K`。
- 模组列表：地名汉化、FixChinaFlags、Monohydrate、CTAP。

### 耗时对比

| 指标（平均耗时） | 关闭优化（秒） | 开启优化（秒） | 耗时降低 |
|---|---:|---:|---:|
| 整体加载会话 `elapsedNanos` | 18.388 | 12.633 | 31.30% |
| 加载帧累计 `frames.totalNanos` | 17.441 | 11.620 | 33.37% |
| 省份加载 `loadProvince` | 5.529 | 3.376 | 38.94% |
| 省份纹理加载 `loadProvinceTexture` | 4.922 | 2.789 | 43.34% |
| 地图加载 `loadGameMap` | 1.429 | 0.682 | 52.23% |
| 城市加载 `loadCities` | 1.014 | 0.190 | 81.32% |
| ↳ 城市归属扫描 `cities.assign` | 0.905 | 0.092 | 89.88% |
| 省份加载子阶段 `province.path` | 2.051 | 0.045 | 97.83% |
| 纹理加载子阶段 `texture.path` | 2.181 | 0.017 | 99.23% |

整体耗时中位数：关闭组 **18.553 秒**，开启组 **12.685 秒**

最小至最大值分别为 **17.694–18.927 秒**、**12.294–12.842 秒**

## 安装

~~订阅创意工坊模组~~ （尚未推出），或将模组构建产物放入 `[游戏根目录]/mods/[任意模组目录]/plugins/` 中，随后使用 [Finality Loader](https://github.com/Finality-Framework/loader) 启动游戏。

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
