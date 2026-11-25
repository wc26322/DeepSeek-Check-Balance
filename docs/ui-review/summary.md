# DeepSeek 余额 App — UI 协调性审计报告

- 日期：2026-11-26
- Skill：`compose-ui-consistency-review`
- 模式：**audit**（未提供人工确认的 `baselinePath`，自动降级；本报告不输出验收通过/失败结论）
- 约束：未修改界面代码、未生成或使用界面截图、未访问真实账号数据

## 1. 本次输入

| 参数 | 取值 |
| --- | --- |
| `projectRoot` | `DeepSeek-Check-Balance` |
| `sourceGlob` | `app/src/main/java/**/*.kt` |
| `pages` | 首页、设置页、玻璃效果实验室、底部导航 |
| `runGeometry` | false；同时因缺少 testTag / fake 数据 / instrumentation 测试而 blocked |
| `allowVisualRegression` | false |
| `reportDir` | `docs/ui-review` |

## 2. 前置检查

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| Kotlin 源码可读 | 通过 | 扫描 40 个文件，13 个含布局属性 |
| Python 与报告目录 | 通过 | 扫描脚本与报告均可复现 |
| 人工基线 / 阈值 / 白名单 | 缺失 | 只能运行 audit，不做 gate 判定 |
| testTag | 缺失 | 全项目未找到 `testTag` / `testTagsAsResourceId` |
| fake 页面数据与状态驱动 | 缺失 | 无法稳定进入成功/错误等被测状态 |
| 真实凭证扫描 | 通过 | 仅 `MainScreen.kt:237` 预览使用 `sk-test` 占位，不构成真实凭证 |
| 几何量化与多尺寸矩阵 | blocked | 缺测试基础设施；不记为产品缺陷 |

## 3. 静态事实摘要

| 维度 | 事实 |
| --- | --- |
| 水平 padding | `4 / 6 / 10 / 14 / 16 / 20 / 24 dp` 共 7 种；页面级主要为 `24`，面板与分组内部混用 `16 / 20 / 14` |
| 卡片内边距 | 设置页统一 `padding(20.dp)` 共 9 处；实验室示例卡片为 `16.dp` |
| 间距表达 | `Spacer` 与 `Arrangement.spacedBy` 并存；`Spacer height` 主要为 `12 / 8 / 10 / 4 / 24 / 16` |
| 非 4 倍数间距 | 出现 `2 / 3 / 5 / 6 / 10 / 14 dp`，需要逐项决定保留或归并 |
| 圆角 | `RoundedCornerShape` 出现 `12 / 16 / 24 / 28 / 4 / 10 / 14 / 8 / 2`；玻璃面板 `RoundedRectangle(32.dp)` 1 处 |
| 字号 | 直接 `fontSize = N.sp` 出现 `14 / 15 / 16 / 12 / 13 / 11` 等，部分文字绕过 typography |
| 比例布局 | `fillMaxHeight(0.72f)` 1 处；`maxHeight * 比例` 等计算尺寸属于静态扫描盲区 |

详细逐文件、逐行数据见 `stage1-scan.json`；取值分布见 `stage1-scan-report.md`。

## 4. 一致性候选（audit，不判缺陷）

| 编号 | 候选 | 级别建议 | 需要人工确认 |
| --- | --- | --- | --- |
| C-01 | 页面 / 面板 / 选项框三级容器的水平 padding 未统一 | P1 | 三级容器各自固定为哪一档 |
| C-02 | 设置页卡片内边距 `20`，实验室示例卡片 `16` | P1 | 统一为 `20`，或把实验室登记为紧凑例外 |
| C-03 | 同类间距混用 `Spacer` 与 `spacedBy` | P1 | 是否约束一种表达方式 |
| C-04 | 非 4 倍数间距 `2 / 3 / 5 / 6 / 10 / 14` | P1 | 保留为微调令牌或归并 |
| C-05 | 圆角档位较多且用途未声明 | P1 | 普通容器 / 卡片 / 弹窗 / 玻璃面板各固定档位 |
| C-06 | 部分文字直接写 `sp` | P1 | 转 typography 或登记白名单 |
| C-07 | 液态玻璃圆角、模糊、折射由 `LiquidGlassTuning` 动态控制 | 盲区 | 静态扫描无法评价，需几何量化与人工评审 |

## 5. P0 候选（必须几何确认，当前不判 fail）

| 编号 | 风险 | 当前证据 | 所需验证 |
| --- | --- | --- | --- |
| G-01 | 实验室展开时示例按钮与面板空间冲突 —— **已修复并通过量化验证** | 已加 testTag；布局改为预览区 `weight(1.2f)` + 面板 `weight(1f)`，面板列表占面板剩余空间；相交面积 = 0 | `GlassPlaygroundGeometryTest` 5/5 通过，详见 `geometry-report.json` |

文本溢出、对称、基线、中心线等其余 P0/P1 几何项，本次均未测量，不能以“未发现问题”表述。

## 6. 覆盖率

| 范围 | 状态 |
| --- | --- |
| Kotlin 源码静态扫描 | 40 / 40 文件 |
| 首页、设置、实验室、底部导航几何量化 | 0 / 4，blocked：缺 testTag、fake 数据与 instrumentation 测试 |
| 深色模式、fontScale、多尺寸矩阵 | 未执行：依赖几何量化 |
| 折射、模糊、色散、动效、留白与信息密度 | 只能人工评审 |

## 7. 产物

- `stage0-inputs.md`：输入、环境与判定规则草案
- `stage1-method.md`：扫描口径、局限与候选解释
- `stage1-scan.json`：原始结构化事实
- `stage1-scan-report.md`：取值分布报告
- `summary.md`：本报告

复现：`python tools/ui_layout_scan.py`

## 8. 建议处理顺序

1. 人工确认 C-01～C-06 的令牌与例外规则，形成 `baseline.md`。
2. 经批准后，为关键容器补 testTag、为首页/用量补 fake 数据驱动（属于可测性改造，不是界面样式调整）。
3. 将 G-01 作为第一个几何量化用例，输出坐标与相交面积后再决定修复方式。
4. 跑核心页面的主题 / fontScale / 尺寸矩阵，再进入 gate 验收。
5. 最后人工评审玻璃质感、留白、信息密度和动效。
## 9. 2026-11-26 量化复测结果

- **baseline**：`baseline.md` v1 已建立（间距/圆角/字体令牌、P0/P1/P2 判定与白名单）。
- **可测性改造**：实验室加入 `lab:preview:root/topBar/balanceCard/sampleButton`、`lab:panel:root/expandButton` 标签；构建接入 `ui-test-junit4` 与 `AndroidJUnitRunner`。
- **G-01 修复**：预览区与面板按 `1.2 : 1` 分配高度，面板列表占面板剩余空间；展开时面板贴底，收起时由 `Spacer` 撑开，示例区内容始终优先于面板空间。
- **结果**：`GlassPlaygroundGeometryTest` **5/5 通过**（360×800 展开/收起、360×640、411×891、fontScale 1.3），所有配置相交面积 = 0，按钮完整可见或滚动可达。
- **同页对齐整改**：面板水平外边距由 `20dp` 统一为 `24dp`，与页面内容一致；5 个配置均新增“示例卡片与面板左右边缘差 ≤0.5dp”断言并通过。
- **测试写法修正记录**：同一测试方法不得重复 `setContent`；大过设备的尺寸必须使用 `requiredSize`，否则被父约束裁剪导致“假通过”。
- **仍未覆盖**：精确文本基线、`hasVisualOverflow` 插桩、真实余额成功态、真实多设备和人工审美项；详见 `manual-review-checklist.md`。

## 10. 全页面可自动部分完成情况

- **测试总结果**：12 个 instrumentation 用例，**12/12 通过，0 失败、0 跳过**。
- **首页**：空态与 fake 用量成功态均验证内容左右 24dp 对称边距。
- **设置页**：可见卡片左右边缘一致，滚动到底后 about 卡片仍与首张卡片对齐；主列表与调参弹窗列表标签已分离。
- **底部导航**：浅色和深色模式下两项宽度差均 ≤0.5dp；testTag 仅挂在可交互节点，隐藏录制副本不进入语义树。
- **实验室**：展开/收起、多尺寸、fontScale 1.3、深色模式均验证无面板遮挡；卡片与面板左右对齐；面板标题/说明/首个滑块左边缘一致；标题与展开按钮垂直中心线差 ≤1dp。
- **设计令牌**：新增 `DesignTokens.kt`，核心页面与共享组件已接入 `UiSpacing`、`UiRadius`、`UiComponentSize`。
- **人工部分**：玻璃质感、留白、信息密度、动效与真实使用边界不能由自动化替代，已整理为 `manual-review-checklist.md`。
- **明确未测**：精确文本基线和 `hasVisualOverflow` 需要文本插桩；真实余额成功态与真实多设备未执行。这些不计入“已验证”。