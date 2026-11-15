#!/usr/bin/env python3
"""FFmpegStudio 工程静态自检（不需要 JDK / Android SDK）。

检查项：
1. Gradle version catalog 别名是否都有定义
2. Kotlin 文件括号配对、package 与目录一致性
3. 项目内自定义 import 是否存在对应声明
4. Manifest / Kotlin 中引用的资源是否存在，所有 XML 是否合法
5. Manifest 中声明的 Activity / Service / Application 是否存在
6. 导航路由是否都有对应页面与 import
用法：python scripts/selfcheck.py
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main"
KOTLIN_DIRS = [APP / "java", ROOT / "app" / "src" / "test" / "java"]
RES = APP / "res"

issues = []
checks = 0


def fail(message):
    issues.append(message)


def kotlin_files():
    out = []
    for d in KOTLIN_DIRS:
        if d.exists():
            out.extend(sorted(d.rglob("*.kt")))
    out.extend(sorted(ROOT.rglob("*.kts")))
    return out


def strip_and_check_brackets(path, text):
    """跳过字符串/字符/注释后检查括号配对，返回是否有错误。"""
    stack = []
    pairs = {"}": "{", ")": "(", "]": "["}
    i, line, n = 0, 1, len(text)
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j < 0:
                fail(f"{path.name}: 块注释未闭合 L{line}")
                return
            line += text[i:j + 2].count("\n")
            i = j + 2
            continue
        if c == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                if text[i] == "\n":
                    line += 1
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c in "({[":
            stack.append((c, line))
        elif c in ")}]":
            if not stack or stack[-1][0] != pairs[c]:
                fail(f"{path.name}: L{line} 括号不匹配 '{c}'")
                return
            stack.pop()
        i += 1
    if stack:
        fail(f"{path.name}: 括号未闭合 {stack[:3]}")


def check_version_catalog():
    global checks
    toml = ROOT / "gradle" / "libs.versions.toml"
    if not toml.exists():
        fail("缺少 gradle/libs.versions.toml")
        return
    libs, plugins, section = set(), set(), ""
    for line in toml.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            section = stripped[1:-1]
            continue
        m = re.match(r"^([A-Za-z0-9_.-]+)\s*=", stripped)
        if not m:
            continue
        if section == "libraries":
            libs.add(m.group(1))
        elif section == "plugins":
            plugins.add(m.group(1))
    for gradle in ROOT.rglob("*.gradle.kts"):
        if "build" in gradle.parts and "buildSrc" in gradle.parts:
            continue
        for m in re.finditer(r"libs\.([A-Za-z0-9_.]+)", gradle.read_text(encoding="utf-8")):
            checks += 1
            full = m.group(1)
            if full.startswith("plugins."):
                key = full[len("plugins."):].replace(".", "-")
                if key not in plugins:
                    fail(f"{gradle.name}: 未定义的插件别名 {full}")
            else:
                key = full.replace(".", "-")
                if key not in libs:
                    fail(f"{gradle.name}: 未定义的依赖别名 {full}")


def check_kotlin():
    global checks
    declared = set()
    for f in kotlin_files():
        text = f.read_text(encoding="utf-8")
        checks += 1
        strip_and_check_brackets(f, text)
        declared.add(f.stem)
        for m in re.finditer(
            r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:private\s+|internal\s+)?"
            r"(?:data\s+class|enum\s+class|sealed\s+class|class|object|interface|fun|(?:const\s+)?val|typealias)\s+"
            # 支持扩展函数 / 扩展属性：fun Modifier.softAppear(...)
            r"(?:[\w.<>?]+\s*\.\s*)?(\w+)",
            text,
            re.M,
        ):
            declared.add(m.group(1))
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
        if pkg and str(f).replace("\\", "/").find("/src/test/") < 0 and f.suffix == ".kt":
            name = pkg.group(1)
            expected = "com/framebetween/app" + (
                "" if name == "com.framebetween.app" else "/" + name.replace("com.framebetween.app.", "").replace(".", "/")
            )
            if expected not in f.parent.as_posix():
                fail(f"{f.name}: package {name} 与目录不一致")

    for f in kotlin_files():
        for m in re.finditer(r"^import\s+(com\.framebetween\.app\.[\w.]+)", f.read_text(encoding="utf-8"), re.M):
            checks += 1
            name = m.group(1).split(".")[-1]
            # R 类由 AGP 生成，源码中不存在
            if name == "R":
                continue
            if name not in declared:
                fail(f"{f.name}: 引用了不存在的声明 {m.group(1)}")
    return declared


def resource_names(kind):
    names = set()
    for f in RES.rglob("*.xml"):
        text = f.read_text(encoding="utf-8")
        if kind == "style":
            names.update(re.findall(r'<style\s+name="([^"]+)"', text))
        elif kind == "string":
            names.update(re.findall(r'<string\s+name="([^"]+)"', text))
        elif kind == "color":
            names.update(re.findall(r'<color\s+name="([^"]+)"', text))
    return names


def check_resources():
    global checks
    for f in list(RES.rglob("*.xml")) + [APP / "AndroidManifest.xml"]:
        checks += 1
        try:
            ET.parse(f)
        except ET.ParseError as e:
            fail(f"{f.relative_to(ROOT)}: XML 解析失败 {e}")

    styles = resource_names("style")
    strings = resource_names("string")
    colors = resource_names("color")
    drawables = {
        p.stem for p in RES.rglob("*") if p.is_file()
        and any(part.startswith("drawable") for part in p.parts)
        and p.suffix.lower() in {".xml", ".png", ".webp", ".jpg", ".jpeg"}
    }
    xmls = {p.stem for p in (RES / "xml").glob("*.xml")} if (RES / "xml").exists() else set()
    mipmaps = {p.stem for p in RES.rglob("*.xml") if p.parent.name.startswith("mipmap")}

    manifest_path = APP / "AndroidManifest.xml"
    manifest = manifest_path.read_text(encoding="utf-8")
    sources = [(manifest_path, manifest)] + [
        (f, f.read_text(encoding="utf-8")) for f in kotlin_files() if f.suffix == ".kt"
    ]
    mapping = {
        "style": styles,
        "string": strings,
        "color": colors,
        "drawable": drawables,
        "xml": xmls,
        "mipmap": mipmaps,
    }
    for path, text in sources:
        if path.suffix == ".xml":
            for kind, available in mapping.items():
                for m in re.finditer(r"@" + kind + r"/([\w.]+)", text):
                    checks += 1
                    name = m.group(1)
                    if name not in available:
                        fail(f"{path.name}: 引用了不存在的 {kind} 资源 '{name}'")
        for m in re.finditer(r"R\.(string|drawable|xml|mipmap|color|style)\.(\w+)", text):
            checks += 1
            kind, name = m.group(1), m.group(2)
            if name not in mapping.get(kind, set()):
                fail(f"{path.name}: R.{kind}.{name} 未定义")

    for cls in re.findall(r'android:name="\.([\w.]+)"', manifest):
        checks += 1
        rel = cls.replace(".", "/")
        if not list((APP / "java").rglob(rel + ".kt")):
            fail(f"AndroidManifest: 找不到类 {cls}")


def check_routes():
    global checks
    root = ROOT / "app" / "src" / "main" / "java" / "com" / "framebetween" / "app" / "ui" / "AppRoot.kt"
    text = root.read_text(encoding="utf-8")
    consts = set(re.findall(r'const val (\w+) = "(\w+)"', text))
    defined = {name for name, _ in consts}
    used = set(re.findall(r"Routes\.(\w+)", text))
    checks += len(used)
    for name in used - defined:
        fail(f"AppRoot.kt: 引用了未定义的路由 Routes.{name}")
    for name, value in consts:
        checks += 1
        if f'composable(Routes.{name})' not in text and name != "HOME":
            fail(f"AppRoot.kt: 路由 {value} 没有对应的 composable 页面")
    screens = {p.stem for p in (APP / "java" / "com" / "framebetween" / "app" / "ui").rglob("*Screen.kt")}
    for screen in screens:
        checks += 1
        if screen not in text:
            fail(f"AppRoot.kt: 页面 {screen} 未接入导航")


def main():
    check_version_catalog()
    check_kotlin()
    check_resources()
    check_routes()
    print(f"静态自检：执行 {checks} 项检查，扫描 {len(kotlin_files())} 个 Kotlin 文件")
    if issues:
        print(f"发现 {len(issues)} 个问题：")
        for item in issues:
            print("  - " + item)
        return 1
    print("全部通过：依赖别名 / 括号与包路径 / 自定义引用 / 资源与清单 / 导航路由")
    return 0


if __name__ == "__main__":
    sys.exit(main())
