# 阶段 1：静态事实扫描规则与初步发现（草案）

> 状态：草案，待评审。扫描**只读源码**，不修改界面、不生成截图、不做缺陷判定。
> 工具：`tools/ui_layout_scan.py`；产物：`stage1-scan.json`、`stage1-scan-report.md`（本目录）。

## 1. 扫描口径

| 类别 | 识别内容 | 能回答的问题 |
| --- | --- | --- |
| `padding_uniform` | `padding(20.dp)`、`PaddingValues(20.dp)`、`padding(all = …)` | 容器统一内边距有哪些取值 |
| `padding_named` | `start/end/top/bottom/horizontal/vertical = N.dp` | 页面与面板的水平、垂直边距分布 |
| `spacer` | `Spacer(Modifier.height/width(N.dp))` | 显式空白间距取值 |
| `spaced_by` | `Arrangement.spacedBy(N.dp)` | 列表/列的项间距 |
| `rounded_corner_shape` / `rounded_rectangle` | `RoundedCornerShape(N.dp)`、`RoundedRectangle(N.dp)` | 普通容器与玻璃容器圆角 |
| `dimension` | `.height/.width/.size/.heightIn/.widthIn(N.dp)` | 固定尺寸与最大尺寸 |
| `font_size` | `fontSize = N.sp` | 绕过 typography 的字号 |
| `fraction` | `fillMaxHeight/Width(0.xf)` | 比例布局取值 |

## 2. 已知局限（影响结论解读）

1. 正则不解析 Compose 语义，**无法判断容器层级与组件归属**，只能按文件归类。
2. **动态取值未覆盖**：`LiquidGlassTuning`（圆角、模糊、折射等运行时令牌）、`maxHeight * 0.30f` 这类计算尺寸、状态相关 padding，都不在统计内。
3. 暂不含对齐方式、基线、约束（`weight` / `fill`）与颜色对比度，需要后续扩展或交给几何量化。
4. 统计到的是"声明值"，不等于"渲染值"；渲染差异由阶段 3 的边界框量化负责。

## 3. 现状事实摘要

- 扫描 Kotlin 文件 40 个，命中布局属性的文件 13 个。
- 命名方向 padding 出现 **4 / 6 / 10 / 14 / 16 / 20 / 24** 等 7 种水平取值；页面级内容水平 padding 为 `24`（`MainActivity`、`MainScreen`、`SettingsScreen`），面板与分组内部为 `16 / 20 / 14` 等。
- 统一 `padding(20.dp)` 出现 9 次（设置页卡片内边距）；实验室示例卡片当前为 `16`，与设置页 `20` 不一致，**是否统一需评审**。
- `Spacer` 高度以 `12 / 8 / 10 / 4 / 24 / 16` 为主，另有 `2 / 3 / 5 / 6 / 20 / 72 / 96`；`10 / 2 / 3 / 5 / 6` 不在常见 4dp 栅格上。
- `RoundedCornerShape` 取值 `12 / 16 / 24 / 28 / 4 / 10 / 14 / 8 / 2`；玻璃形状 `RoundedRectangle(32)` 仅 1 处（实验室面板）。
- `fontSize` 直接取值 `14 / 15 / 16 / 12 / 13 / 57 / 45 / 36 / 32 / 28 / 24 / 22 / 11`，其中 `14 / 15 / 16` 多用于组件内部文字。
- `fillMaxHeight(0.72f)` 仅 1 处（`NavGlassDialog`）；实验室已改为 `heightIn(max = maxHeight * 比例)`，该类计算尺寸未被正则统计。

## 4. 待评审的"一致性候选"（不是缺陷结论）

| # | 事实 | 需要人工决定的问题 |
| --- | --- | --- |
| 1 | 页面内容水平 padding `24`，面板内部同时存在 `4 / 14 / 16 / 20` | 按"页面 / 面板 / 选项框"三级容器分别固定取值 |
| 2 | 设置页卡片内边距 `20`，实验室示例卡片 `16` | 是否统一为 `20`；若实验室为紧凑预览，是否登记为例外 |
| 3 | `Spacer` 与 `spacedBy` 混用表达组件间距 | 同一种间距是否只允许一种表达方式 |
| 4 | 非 4 的倍数间距 `2 / 3 / 5 / 6 / 10 / 14` | 哪些保留为"微调令牌"，哪些归并到 4/8/12/16 |
| 5 | 组件文字同时使用 typography 与直接 `sp` | 直接 `sp` 的位置是否全部转为令牌或登记白名单 |
| 6 | 圆角有 `12 / 14 / 16 / 24 / 28 / 32` 等多档 | 普通容器、卡片、弹窗、玻璃面板各自固定档位 |

## 5. 复现方式

```bash
python tools/ui_layout_scan.py
```

- 输出 JSON（逐文件、逐行号）与人读报告，供阶段 2 评审后直接标注"接受 / 归并 / 例外"。
- 扫描脚本与报告均不改变现有界面行为，可随时删除。