"""阶段 1：Compose 布局静态事实扫描（只读，不修改任何源码）。

目标：不依赖截图与基线，输出"文件 × 属性 × 取值"的事实清单，
供人工评审后沉淀为设计令牌与阈值（阶段 2）。
"""
from __future__ import annotations

import collections
import json
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = PROJECT_ROOT / "app" / "src" / "main" / "java"
OUTPUT_DIR = PROJECT_ROOT / "docs" / "ui-review"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

NUMBER = r"(\d+(?:\.\d+)?)"

PATTERNS: dict[str, re.Pattern[str]] = {
    # padding(16.dp) / PaddingValues(16.dp) / padding(all = 16.dp)
    "padding_uniform": re.compile(r"(?:\.padding|PaddingValues)\(\s*(?:all\s*=\s*)?" + NUMBER + r"\.dp"),
    # start/end/top/bottom/horizontal/vertical = 16.dp
    "padding_named": re.compile(r"\b(start|end|top|bottom|horizontal|vertical)\s*=\s*" + NUMBER + r"\.dp"),
    # Spacer(modifier = Modifier.height(12.dp))
    "spacer": re.compile(r"Spacer\(modifier\s*=\s*Modifier\.(height|width)\(" + NUMBER + r"\.dp\)"),
    # Arrangement.spacedBy(16.dp)
    "spaced_by": re.compile(r"spacedBy\(" + NUMBER + r"\.dp"),
    # RoundedCornerShape(24.dp)
    "rounded_corner_shape": re.compile(r"RoundedCornerShape\(" + NUMBER + r"\.dp"),
    # RoundedRectangle(32.dp)（backdrop 玻璃形状）
    "rounded_rectangle": re.compile(r"RoundedRectangle\(" + NUMBER + r"\.dp"),
    # .height(56.dp) / .width(20.dp) / .size(18.dp) / .heightIn(max = 200.dp)
    "dimension": re.compile(r"\.(height|width|size|heightIn|widthIn)\(\s*(?:max\s*=\s*)?" + NUMBER + r"\.dp"),
    # fontSize = 15.sp
    "font_size": re.compile(r"fontSize\s*=\s*" + NUMBER + r"\.sp"),
    # fillMaxHeight(0.55f)
    "fraction": re.compile(r"fillMax(?:Height|Width)\((0?\.\d+)f\)"),
}

CATEGORY_LABELS = {
    "padding_uniform": "统一 padding / PaddingValues",
    "padding_named": "命名 padding（方向 → dp）",
    "spacer": "Spacer 间距",
    "spaced_by": "Arrangement.spacedBy",
    "rounded_corner_shape": "RoundedCornerShape 圆角",
    "rounded_rectangle": "RoundedRectangle 玻璃圆角",
    "dimension": "height/width/size/heightIn 尺寸",
    "font_size": "fontSize 字号",
    "fraction": "fillMax 比例",
}


def scan_file(path: Path) -> dict[str, list[dict]]:
    text = path.read_text(encoding="utf-8")
    findings: dict[str, list[dict]] = collections.defaultdict(list)
    for category, pattern in PATTERNS.items():
        for match in pattern.finditer(text):
            line_no = text.count("\n", 0, match.start()) + 1
            if category == "padding_named":
                value = f"{match.group(1)}={match.group(2)}"
            elif category in {"spacer", "dimension"}:
                value = f"{match.group(1)}({match.group(2)})"
            elif category == "fraction":
                value = match.group(1)
            else:
                value = match.group(match.lastindex or 1)
            findings[category].append({"value": value, "line": line_no})
    return findings


def main() -> None:
    files = sorted(SOURCE_ROOT.rglob("*.kt"))
    per_file: dict[str, dict] = {}
    global_values: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)

    for path in files:
        relative = str(path.relative_to(SOURCE_ROOT)).replace("\\", "/")
        findings = scan_file(path)
        if not findings:
            continue
        per_file[relative] = findings
        for category, items in findings.items():
            for item in items:
                global_values[category][item["value"]] += 1

    payload = {
        "mode": "audit-only",
        "note": "仅事实统计，不含任何通过/失败判定；基线由人工在阶段 2 确认。",
        "file_count": len(files),
        "files_with_findings": len(per_file),
        "global_value_distribution": {
            category: dict(counter.most_common())
            for category, counter in global_values.items()
        },
        "per_file": per_file,
    }
    json_path = OUTPUT_DIR / "stage1-scan.json"
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = ["# 阶段 1 静态事实扫描结果（自动生成）", "",
             "> 只读统计，不代表缺陷判定；取值是否合理由人工在阶段 2 评审。", "",
             f"- 扫描 Kotlin 文件：{len(files)} 个", f"- 命中布局属性的文件：{len(per_file)} 个", ""]
    for category, counter in global_values.items():
        lines.append(f"## {CATEGORY_LABELS.get(category, category)}（{category}）")
        lines.append("")
        lines.append("| 取值 | 次数 |")
        lines.append("| --- | --- |")
        for value, count in counter.most_common():
            lines.append(f"| `{value}` | {count} |")
        lines.append("")

    lines.append("## 各文件水平/垂直 padding 取值")
    lines.append("")
    lines.append("| 文件 | 命名 padding 取值 |")
    lines.append("| --- | --- |")
    for relative, findings in per_file.items():
        named = findings.get("padding_named")
        if not named:
            continue
        values = sorted({item["value"] for item in named})
        lines.append(f"| `{relative}` | {', '.join('`' + v + '`' for v in values)} |")
    lines.append("")

    report_path = OUTPUT_DIR / "stage1-scan-report.md"
    report_path.write_text("\n".join(lines), encoding="utf-8")

    print(f"files={len(files)} with_findings={len(per_file)}")
    for category, counter in global_values.items():
        print(f"\n[{category}]")
        for value, count in counter.most_common():
            print(f"  {value}: {count}")
    print(f"\nJSON: {json_path}")
    print(f"报告: {report_path}")


if __name__ == "__main__":
    main()