# 性能优化原理

本文介绍 Monohydrate 针对《历史时代 2：DE》（Age of History II: Definitive Edition）加载阶段的各项优化功能与实现原理。

---

## 默认纹理格式

### 功能介绍

在加载未显式指定像素格式的图片时，直接保留 LibGDX 解码得到的原生格式，省去 CPU 端将无透明通道图片（`RGB888`）转换为 `RGBA8888` 的像素重排与内存拷贝开销，同时降低显存与内存占用。

### 实现原理

- `MixinIMGManager` 拦截 `ImagesManager` 中未指定 `Pixmap.Format` 的 `addIMG`、`addIMG2`、`loadImage` 和 `loadTexture` 重载入口，将底层传入的默认格式参数改为 `null`。
- LibGDX 解码图片时若格式参数为 `null`，会直接使用源文件的像素格式：无透明通道图片保持 `RGB888`，带透明通道图片保持 `RGBA8888`。

---

## CIM 头部读取

### 功能介绍

将省份背景纹理（`.cim` 格式）12 字节文件头的逐字节读取改为单次批量读取与大端序位运算解析，减少流调用次数。

### 实现原理

- `MixinPixmapIOCIM` 接管 `PixmapIO$CIM.read`，在 `CIM` 原有的 32,000 字节静态缓冲区锁内，调用 `DataInputStream.readFully(readBuffer, 0, 12)` 一次性读入 12 字节头部。
- 直接从缓冲区按大端字节序解析宽度、高度与 `Pixmap.Format`，随后复用同一缓冲区继续解压读取像素数据。

---

## 文明名称字体共享

### 功能介绍

原版在初始化文明名称渲染字体时，会使用相同的参数连续执行两次 FreeType 字体光栅化以生成 `fontBorder` 和 `fontBorder2`。该优化使两者共享同一套底层纹理页，仅执行一次 FreeType 生成。

### 实现原理

- **结构复制与纹理共享**：`MixinCFGBorderFont` 在 `CFG.loadFontBorder()` 生成主字体 `fontBorder` 后（尚未被渲染逻辑缩放前），直接复制其 `BitmapFontData`、字形度量（`Glyph`）、字距（`kerning`）与 UV 映射构造副字体 `fontBorder2`，并引用同一组纹理页（`TextureRegion`），跳过第二次 FreeType 生成。
- **生命周期管理**：`fontBorder` 作为共享纹理页的唯一持有者（owner）负责纹理释放；`fontBorder2` 拥有独立的 `BitmapFont`、`BitmapFontCache`、缩放比例与颜色状态。当语言、字号、描边或颜色设置变更触发重建时，先释放副字体结构，再由主字体释放旧纹理并重新生成。
- **增量字体兼容**：仅对非增量（静态预生成）FreeType 字体启用共享；若检测到增量模式（`incremental`）字体，则维持原版双实例独立生成与按需补字流程。

---

## 省份候选目录过滤

### 功能介绍

原版在加载每个省份数据、缩放纹理及地图附属资源时，会遍历所有已启用的本地模组与创意工坊模组目录并逐一执行文件存在性探测（`exists()`）。当启用多个模组而其中仅有少数模组包含地图覆盖文件时，会产生大量无效的文件系统调用。该优化预先过滤出实际包含对应资源目录的候选模组，大幅削减逐文件探测次数。

### 实现原理

- **按目录构建候选集**：在桌面端文件解析器（`FileManager$1` / `FileManager$2`）外层包装候选过滤器，针对当前地图的下列平铺资源目录分别维护有序候选根列表：
  - `data/provinces/<ID>`
  - `data/scales/provinces/<scale>/<ID>`（按实际请求的 `scale` 独立建表）
  - `army_boxes/`、`civs_template/`、`suggested_owners/`、`formable_civs/`、`provinces/`
- **探测剪枝**：某类目录首次收到文件请求时，按原版查找优先级（本地模组 → 创意工坊模组，Mac 平台含每个本地模组的 `external` → `internal` 顺序）检查各模组根下该目录是否存在，仅保留存在该目录的模组根。后续加载该目录下的文件时，只在保留的候选根上执行 `exists()`，全部未命中时回退至原版路径。
- **Classpath 资源识别**：对于 `Internal` 文件类型，除检查物理目录外，还会按需扫描已知类加载器链（包含目录根、JAR 条目与 Manifest `Class-Path`），确保打包在类路径中的资源目录不会被误筛除。
- **会话生命周期与失效同步**：候选缓存绑定于 `Menu_InitGame`（启动加载）或 `Menu_LoadMap`（切换地图）会话，并在地图/模组列表变更、省份或纹理触发异常重建、或通过 `getSaveType` 获取写入句柄时自动失效，加载完成后释放。

---

## 城市归属扫描优化

### 功能介绍

原版 `Core.buildCitiesProvinceID()` 在为城市匹配所属省份时，对每个城市遍历全部省份执行包围盒（AABB）与多边形点包含（`ptCS`）测试，时间复杂度为 $O(\text{城市数} \times \text{省份数})$。该优化通过构建二维均匀网格空间索引，将每个城市的检测范围缩小至其所在网格单元内的少数候选省份。

### 实现原理

- **32×32 空间网格建表**：在 `buildCitiesProvinceID()` 开始时，扫描全部省份的 AABB 总边界并划分为 32×32 的均匀网格；按省份 ID 升序将每个省份填入其 AABB 覆盖的所有网格单元（使用紧凑的 `int[]` 存储）。
- **单元候选查询**：遍历城市列表时，先计算城市缩放后的整数坐标并定位其所在的网格单元，仅对该单元内的候选省份按升序执行原有的 AABB 边界判断与 `ptCS` 多边形测试，首次命中后即完成该城市的归属绑定。
