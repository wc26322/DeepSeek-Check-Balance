# 阶段 1 静态事实扫描结果（自动生成）

> 只读统计，不代表缺陷判定；取值是否合理由人工在阶段 2 评审。

- 扫描 Kotlin 文件：40 个
- 命中布局属性的文件：13 个

## 命名 padding（方向 → dp）（padding_named）

| 取值 | 次数 |
| --- | --- |
| `top=8` | 4 |
| `horizontal=16` | 4 |
| `horizontal=24` | 3 |
| `horizontal=14` | 3 |
| `horizontal=20` | 3 |
| `bottom=16` | 2 |
| `start=24` | 2 |
| `end=24` | 2 |
| `start=16` | 2 |
| `vertical=8` | 2 |
| `vertical=12` | 2 |
| `vertical=16` | 2 |
| `start=20` | 1 |
| `end=20` | 1 |
| `bottom=92` | 1 |
| `end=8` | 1 |
| `end=16` | 1 |
| `bottom=4` | 1 |
| `horizontal=4` | 1 |
| `bottom=88` | 1 |
| `top=72` | 1 |
| `bottom=96` | 1 |
| `start=8` | 1 |
| `vertical=14` | 1 |
| `vertical=10` | 1 |
| `horizontal=10` | 1 |
| `vertical=5` | 1 |
| `horizontal=6` | 1 |

## 统一 padding / PaddingValues（padding_uniform）

| 取值 | 次数 |
| --- | --- |
| `20` | 9 |
| `16` | 3 |
| `0` | 1 |
| `24` | 1 |
| `14` | 1 |

## Spacer 间距（spacer）

| 取值 | 次数 |
| --- | --- |
| `height(12)` | 24 |
| `height(8)` | 20 |
| `height(10)` | 12 |
| `height(4)` | 8 |
| `height(24)` | 7 |
| `height(16)` | 6 |
| `width(4)` | 5 |
| `width(6)` | 4 |
| `width(2)` | 4 |
| `height(2)` | 4 |
| `height(20)` | 3 |
| `width(16)` | 2 |
| `width(8)` | 2 |
| `height(3)` | 1 |
| `height(72)` | 1 |
| `height(6)` | 1 |
| `height(96)` | 1 |
| `width(10)` | 1 |
| `width(5)` | 1 |

## Arrangement.spacedBy（spaced_by）

| 取值 | 次数 |
| --- | --- |
| `12` | 2 |
| `16` | 2 |
| `64` | 1 |
| `0` | 1 |
| `8` | 1 |
| `10` | 1 |

## height/width/size/heightIn 尺寸（dimension）

| 取值 | 次数 |
| --- | --- |
| `height(12)` | 24 |
| `height(8)` | 21 |
| `height(10)` | 12 |
| `height(4)` | 8 |
| `height(24)` | 7 |
| `height(16)` | 6 |
| `width(4)` | 5 |
| `size(16)` | 4 |
| `width(6)` | 4 |
| `width(2)` | 4 |
| `height(2)` | 4 |
| `size(20)` | 3 |
| `height(20)` | 3 |
| `size(14)` | 3 |
| `size(18)` | 2 |
| `width(16)` | 2 |
| `size(36)` | 2 |
| `width(8)` | 2 |
| `size(8)` | 2 |
| `height(52)` | 2 |
| `width(360)` | 2 |
| `height(3)` | 1 |
| `height(72)` | 1 |
| `height(6)` | 1 |
| `height(96)` | 1 |
| `size(15)` | 1 |
| `size(24)` | 1 |
| `height(56)` | 1 |
| `width(10)` | 1 |
| `size(10)` | 1 |
| `width(5)` | 1 |
| `height(200)` | 1 |

## RoundedRectangle 玻璃圆角（rounded_rectangle）

| 取值 | 次数 |
| --- | --- |
| `32` | 1 |

## RoundedCornerShape 圆角（rounded_corner_shape）

| 取值 | 次数 |
| --- | --- |
| `12` | 6 |
| `16` | 3 |
| `24` | 2 |
| `28` | 2 |
| `4` | 2 |
| `10` | 2 |
| `14` | 1 |
| `8` | 1 |
| `2` | 1 |

## fontSize 字号（font_size）

| 取值 | 次数 |
| --- | --- |
| `14` | 5 |
| `15` | 3 |
| `16` | 2 |
| `12` | 2 |
| `13` | 1 |
| `57` | 1 |
| `45` | 1 |
| `36` | 1 |
| `32` | 1 |
| `28` | 1 |
| `24` | 1 |
| `22` | 1 |
| `11` | 1 |

## fillMax 比例（fraction）

| 取值 | 次数 |
| --- | --- |
| `0.72` | 1 |

## 各文件水平/垂直 padding 取值

| 文件 | 命名 padding 取值 |
| --- | --- |
| `com/deepseek/balance/MainActivity.kt` | `bottom=16`, `horizontal=24` |
| `com/deepseek/balance/ui/GlassPlaygroundPage.kt` | `bottom=16`, `bottom=4`, `bottom=92`, `end=16`, `end=20`, `end=24`, `end=8`, `horizontal=4`, `start=16`, `start=20`, `start=24`, `top=8`, `vertical=8` |
| `com/deepseek/balance/ui/MainScreen.kt` | `bottom=88`, `horizontal=16`, `horizontal=24`, `top=72`, `top=8` |
| `com/deepseek/balance/ui/SettingsScreen.kt` | `bottom=96`, `end=24`, `horizontal=14`, `horizontal=16`, `horizontal=20`, `horizontal=24`, `start=24`, `start=8`, `top=8`, `vertical=12`, `vertical=14`, `vertical=16` |
| `com/deepseek/balance/ui/TopBar.kt` | `horizontal=20` |
| `com/deepseek/balance/ui/UsageSection.kt` | `horizontal=10`, `horizontal=14`, `horizontal=16`, `horizontal=6`, `vertical=10`, `vertical=16`, `vertical=5`, `vertical=8` |
