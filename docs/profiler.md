# 性能分析系统

Monohydrate 内置了针对游戏启动加载阶段的性能分析器（`LoadingProfiler`），用于记录加载耗时分布、热点方法、I/O 路径解析统计以及几何计算工作量。

---

## 工作原理

### 会话生命周期与计时

- **会话范围**：每个 `Menu_InitGame` 实例对应一个独立的分析会话，仅在执行 `loadAssets` 的 LibGDX 主线程上采集数据。
- **四态流转**：会话状态按 `帧间` → `帧内`（单次 `loadAssets` 调用） → `等待当前帧完成` → `已结束` 流转。计时跨度从首次进入 `loadAssets` 开始，至完成菜单跳转并从最后一次 `loadAssets` 返回时结束。
- **低开销聚合**：采集过程使用预分配的固定长度数组记录调用次数与耗时，不在每次方法调用时分配对象。分位数统计采用对数分桶直方图（每个二倍区间划分为 8 个桶，共 488 个 `long` 桶），内存占用恒定，相对上界误差小于 $12.5\%$。
- **报告导出**：加载完成后，在游戏工作目录下生成 `logs/monohydrate/loading-<epochMillis>-<sessionId>.json` 报告，并在控制台输出耗时前 10 的热点方法与关键阶段统计，随后释放采集缓冲区。

---

## JSON 报告结构（Schema v2）

报告中所有时长字段均由单调时钟 `System.nanoTime()` 测量，单位为纳秒（`ns`）；控制台日志输出单位为毫秒（`ms`）。

### 1. 通用耗时统计字段

`frames`（`loadAssets` 汇总）、`methods[]`（按 `totalNanos` 降序排列的直接调用监测点）和 `stages[]`（细分加载子阶段）均包含以下统计字段：

| 字段 | 含义 |
|---|---|
| `method` | 监测方法名或子阶段标识 |
| `calls` / `returnedCalls` / `unreturnedCalls` | 入口调用次数 / 正常匹配返回次数 / 未匹配返回次数 |
| `totalNanos` / `minNanos` / `maxNanos` / `averageNanos` | 已返回调用的总耗时、最小耗时、最大耗时与平均耗时 |
| `p50UpperBoundNanos` / `p95UpperBoundNanos` / `p99UpperBoundNanos` | 基于对数直方图估算的 P50 / P95 / P99 分位数上界（以实测 `maxNanos` 封顶） |
| `maxCallIndex` / `maxFrameIndex` | 最慢一次调用的入口序号与所在加载帧序号（从 1 开始） |
| `maxStartedOffsetNanos` | 最慢一次调用入口距离会话开始的时间偏移 |

### 2. 环境与配置元数据（`metadata`）

在会话结束时同步采集一次运行时环境快照：

| 字段 | 含义 |
|---|---|
| `versions.plugin` / `game` / `loader` | Monohydrate 插件版本、运行时 `CFG.VERSION` 游戏版本及 `Main.VERSION` 加载器版本 |
| `configuration.cityOptimizationsEnabled` / `profilerEnabled` | 当前会话实际生效的开关状态 |
| `configuration.analysisMode` | 固定为 `loading-frames-invokes-stages` |
| `map.index` / `folder` | 当前地图索引与目录名 |
| `map.assetScale` / `extraScale` / `viewportZoom` | 地图资源缩放倍率、额外缩放倍率与当前视口缩放 |
| `scenario.index` / `tag` / `name` | 加载完成时的场景索引、标识与名称 |
| `mods[]` | `{source, folder}` 列表，按桌面端实际查找优先级排列的已启用本地模组与创意工坊模组 |
| `plugins[]` | `{id, name, version}` 列表，当前加载的 Finality 插件清单 |
| `jvm` | Java/VM 版本与供应商、堆内存相关启动参数（`heapArguments`）、`MemoryMXBean` 报告的初始/最大/已提交堆字节数，以及 GC 收集器名称（`garbageCollectors`） |

### 3. 时间分账（`accounting`）

用于对账会话总耗时与各层级监测耗时，满足以下恒等关系：
- $\text{elapsedNanos} = \text{returnedFrameNanos} + \text{outsideReturnedFramesNanos}$
- $\text{returnedFrameNanos} = \text{recordedInvokeNanosInReturnedFrames} + \text{unattributedNanosInReturnedFrames}$

| 字段 | 含义 |
|---|---|
| `returnedFrameNanos` | 已返回的 `loadAssets` 帧总耗时 |
| `recordedInvokeNanosInReturnedFrames` | 已返回帧内所有匹配返回的直接调用耗时总和 |
| `unattributedNanosInReturnedFrames` | 帧内未单独插桩的代码耗时与插桩本身开销之差 |
| `outsideReturnedFramesNanos` | 帧外耗时（各次 `loadAssets` 之间的渲染与调度间隔） |
| `betweenFramesNanos` | 当所有帧均正常返回时等于 `outsideReturnedFramesNanos` |
| `timingComplete` | 是否所有帧、直接调用与子阶段均完整闭合 |
| `unreturnedInvokes` / `unexpectedEndCalls` | 未闭合的直接调用数 / 未匹配入口的结束回调数 |
| `unreturnedStages` / `unexpectedStageEndCalls` | 未闭合的子阶段数 / 未匹配入口的子阶段结束回调数 |

---

## 细分加载子阶段（`stages`）

### 1. 省份数据与背景纹理加载

在 `loadProvince` 与 `loadProvinceTexture` 内部按执行步骤拆分为以下子阶段：

| `stages[].method` | 计时范围 |
|---|---|
| `province.path` | `FileManager.loadFile` 省份数据路径解析 |
| `province.read` | `FileHandle.readBytes` 字节读取 |
| `province.deserialize` | `CFG.deserialize` 对象反序列化 |
| `province.construct` | 反序列化后的类型转换与 `Province` 实例构造 |
| `province.rebuild` | 触发异常分支时的 `build_LoadProvince` 重建耗时 |
| `texture.path` | `FileManager.loadFile` 省份纹理路径解析 |
| `texture.cimReadDecompress` | `PixmapIO.readCIM` 文件读取与解压 |
| `texture.createUpload` | `Texture(Pixmap)` 创建、过滤/环绕参数设置、`Image` 构造与 `provBG` 赋值 |
| `texture.rebuild` | 触发异常分支时的 `buildProvinceBG(false)` 纹理重建耗时 |

其中 `province.path` 与 `texture.path` 条目附带 `path` 统计对象，记录模组路径解析与候选过滤的运行指标：

| 字段 | 含义 |
|---|---|
| `localProbes` / `workshopProbes` | 本地模组 / 创意工坊候选路径实际执行 `exists()` 探测的次数 |
| `localHits` / `workshopHits` | 本地模组 / 创意工坊路径命中并返回文件句柄的次数 |
| `vanillaFallbacks` | 模组均未命中而回退至原版路径的次数 |
| `candidateFilterSkips` | 因省份候选目录过滤而直接跳过的模组文件探测次数 |
| `cacheBuildCalls` / `cacheBuildNanos` | 候选目录缓存（含必要的 classpath 清单扫描）的构建次数与总耗时 |
| `cacheBuildSuccesses` / `cacheBuildFailures` | 候选目录缓存构建成功次数 / 放弃构建并回退原解析器的次数 |
| `cacheRootsRetained` / `cacheRootsExcluded` | 缓存构建保留的候选模组根数 / 排除的模组根数累计值 |
| `originalResolverCalls` | 直接委托给原版文件解析器的调用次数 |
| `maxProvinceId` / `maxSource` / `maxSourceFolder` | 该路径解析阶段最慢一次调用对应的省份 ID、命中的来源（`LOCAL` / `WORKSHOP` / `VANILLA`）及模组目录 |

### 2. 城市归属扫描

在 `loadCities` 内部执行 `buildCitiesProvinceID()` 时记录 `cities.assign` 子阶段，包含网格构建（启用优化时）、归属判定与城市列表清理的总耗时，并附带 `cities` 几何检测工作量统计：

| 字段 | 含义 |
|---|---|
| `cityCount` | 参与归属扫描的城市总数 |
| `candidateProvinceChecks` | 进入城市与候选省份配对检测循环的总次数 |
| `aabbPasses` | 通过省份四向 AABB 包围盒粗筛的次数 |
| `ptCSCalls` | 实际执行 `ptCS` 多边形点包含精测的次数 |
