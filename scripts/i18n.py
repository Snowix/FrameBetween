#!/usr/bin/env python3
"""把工程内中文文案统一包成 L10n.t(...)，并生成中英对照表。

用法：
  python scripts/i18n.py scan    # 提取所有中文模板到 scripts/i18n_keys.json
  python scripts/i18n.py apply   # 读取 scripts/i18n_en.json 生成 L10n.kt 并改写 Kotlin 源码
注意：apply 只应执行一次（脚本会跳过已包裹的字符串）。
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "java"
L10N_FILE = SRC / "com" / "framebetween" / "app" / "core" / "L10n.kt"
KEYS_FILE = pathlib.Path(__file__).with_name("i18n_keys.json")
EN_FILE = pathlib.Path(__file__).with_name("i18n_en.json")
CJK = re.compile(r"[\u4e00-\u9fff]")
# 发给大模型的固定中文提示词 / ffprobe 媒体信息是「数据」，不是界面文案：
# 既不能被自动包成 L10n.t()，也不应随应用语言切换。
I18N_EXEMPT = {"AiPrompt.kt", "AiCommandParser.kt", "CommandRedactor.kt", "L10n.kt"}


def unescape(s):
    mapping = {"n": "\n", "t": "\t", "r": "\r", "\\": "\\", '"': '"', "'": "'", "$": "$", "b": "\b"}
    out, i = [], 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s):
            out.append(mapping.get(s[i + 1], s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def escape_kotlin(s):
    out = []
    for c in s:
        if c == "\\":
            out.append("\\\\")
        elif c == '"':
            out.append('\\"')
        elif c == "$":
            out.append("\\$")
        elif c == "\n":
            out.append("\\n")
        elif c == "\t":
            out.append("\\t")
        elif c == "\r":
            out.append("\\r")
        else:
            out.append(c)
    return "".join(out)


def find_strings(src):
    """返回 (start, end, segments)，segments 为 ('lit', raw) / ('expr', code)。"""
    result = []
    i, n = 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j + 1
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if src[i] == "'":
            # Kotlin 字符字面量，例如 '"' 或 '\''，必须整体跳过
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if src[i] == '"':
            start = i
            i += 1
            segments, buf = [], ""
            while i < n:
                c = src[i]
                if c == "\\":
                    buf += src[i:i + 2]
                    i += 2
                    continue
                if c == '"':
                    break
                if c == "$" and i + 1 < n and (src[i + 1] == "{" or src[i + 1].isalpha() or src[i + 1] == "_"):
                    if buf:
                        segments.append(("lit", buf))
                        buf = ""
                    if src[i + 1] == "{":
                        j, depth, in_str, esc = i + 2, 1, False, False
                        while j < n and depth > 0:
                            ch = src[j]
                            if esc:
                                esc = False
                            elif ch == "\\":
                                esc = True
                            elif ch == '"':
                                in_str = not in_str
                            elif not in_str and ch == "{":
                                depth += 1
                            elif not in_str and ch == "}":
                                depth -= 1
                            j += 1
                        segments.append(("expr", src[i + 2:j - 1].strip()))
                        i = j
                    else:
                        j = i + 1
                        while j < n and (src[j].isalnum() or src[j] == "_"):
                            j += 1
                        segments.append(("expr", src[i + 1:j]))
                        i = j
                    continue
                buf += c
                i += 1
            if buf:
                segments.append(("lit", buf))
            end = i + 1
            result.append((start, end, segments))
            i = end
            continue
        i += 1
    return result


def build(segments):
    has_args = any(kind == "expr" for kind, _ in segments)
    parts, exprs, idx = [], [], 0
    for kind, raw in segments:
        if kind == "lit":
            lit = unescape(raw)
            if has_args:
                lit = lit.replace("%", "%%")
            parts.append(lit)
        else:
            idx += 1
            parts.append("%" + str(idx) + "$s")
            exprs.append(raw)
    return "".join(parts), exprs


def collect():
    # 已包裹 L10n.t(...) 的文案也要收进 key 表，否则手写 L10n.t 的新文案
    # 不会出现在生成的 L10n.kt 中（英文模式会回退中文）。
    keys = []
    for f in sorted(SRC.rglob("*.kt")):
        if f.name in I18N_EXEMPT:
            continue
        src = f.read_text(encoding="utf-8")
        for start, end, segments in find_strings(src):
            template, _ = build(segments)
            if CJK.search(template):
                keys.append(template)
    return sorted(set(keys))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "scan"
    keys = collect()
    if mode == "scan":
        if KEYS_FILE.exists():
            keys = sorted(set(keys) | set(json.loads(KEYS_FILE.read_text(encoding="utf-8"))))
        KEYS_FILE.write_text(json.dumps(keys, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"提取 {len(keys)} 条中文模板 -> {KEYS_FILE}")
        return
    # 已包裹过的字面量不会再被 collect() 命中，因此以持久化的 key 列表为准，保证可重复执行
    if KEYS_FILE.exists():
        keys = sorted(set(keys) | set(json.loads(KEYS_FILE.read_text(encoding="utf-8"))))
    if mode != "apply":
        raise SystemExit("用法: python scripts/i18n.py scan|apply")

    en = json.loads(EN_FILE.read_text(encoding="utf-8")) if EN_FILE.exists() else {}
    missing = [k for k in keys if k not in en]
    if missing:
        print(f"警告：{len(missing)} 条未提供英文翻译，将回退中文：")
        for k in missing[:15]:
            print("  -", k.replace("\n", "\\n")[:90])

    changed = 0
    for f in sorted(SRC.rglob("*.kt")):
        if f.name in I18N_EXEMPT:
            continue
        src = f.read_text(encoding="utf-8")
        items = find_strings(src)
        out, last = [], 0
        touched = False
        for start, end, segments in items:
            # 回溯窗口必须足够长：`L10n.t(\n` 后跟深缩进时，24 字符内可能
            # 只剩空白，会被误判成未包裹，从而把带参数的文案二次包装成
            # L10n.t(L10n.t(...))。rstrip 会折叠中间换行与空格。
            prefix = src[max(0, start - 96):start].rstrip()
            if prefix.endswith("L10n.t("):
                continue
            template, exprs = build(segments)
            if not CJK.search(template):
                continue
            replacement = 'L10n.t("' + escape_kotlin(template) + '"'
            for expr in exprs:
                replacement += ", " + expr
            replacement += ")"
            out.append(src[last:start])
            out.append(replacement)
            last = end
            touched = True
        if not touched:
            continue
        out.append(src[last:])
        text = "".join(out)
        if "import com.framebetween.app.core.L10n" not in text:
            lines = text.splitlines(keepends=True)
            insert_at = next((i + 1 for i, l in enumerate(lines) if l.startswith("package ")), 0)
            lines.insert(insert_at, "import com.framebetween.app.core.L10n\n")
            text = "".join(lines)
        f.write_text(text, encoding="utf-8")
        changed += 1

    def kotlin_literal(s):
        return '"' + escape_kotlin(s) + '"'

    entries = []
    for k in keys:
        value = en.get(k, k)
        entries.append("        " + kotlin_literal(k) + " to " + kotlin_literal(value))
    l10n = """package com.framebetween.app.core

import java.util.Locale

/**
 * 轻量应用内文案层：以中文原文为 key，英文对照由 scripts/i18n.py 生成。
 * 纯 Kotlin 实现，不依赖 Android Context，因此 JVM 单元测试也可安全调用。
 * 切换语言后由 Activity.recreate() 触发整棵组合树重建，文案即时刷新。
 */
object L10n {

    @Volatile
    var language: String = "zh"

    private val en: Map<String, String> = mapOf(
""" + ",\n".join(entries) + """
    )

    fun t(zh: String, vararg args: Any): String {
        val template = if (language == "en") en[zh] ?: zh else zh
        if (args.isEmpty()) return template
        return runCatching { String.format(Locale.getDefault(), template, *args) }
            .getOrDefault(template)
    }
}
"""
    L10N_FILE.write_text(l10n, encoding="utf-8")
    print(f"改写 {changed} 个 Kotlin 文件；生成 {len(entries)} 条对照 -> L10n.kt")


if __name__ == "__main__":
    main()
