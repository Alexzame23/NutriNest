"""Pull the SQL the app actually runs out of the Kotlin sources.

Nothing here re-types the statements: the harness executes the same strings the
migration and importer execute, so a typo in either shows up as a failed test
rather than as a crash on someone's phone.
"""

from __future__ import annotations

import re


def _parse_string_expr(text: str) -> str | None:
    """Evaluate a Kotlin string expression: literals joined by '+', .trimIndent()."""
    parts = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch.isspace() or ch == "+":
            i += 1
            continue
        if text.startswith('"""', i):
            end = text.index('"""', i + 3)
            parts.append(text[i + 3:end])
            i = end + 3
            continue
        if ch == '"':
            j = i + 1
            buf = []
            while j < n and text[j] != '"':
                if text[j] == "\\":
                    nxt = text[j + 1]
                    buf.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\"}.get(nxt, nxt))
                    j += 2
                else:
                    buf.append(text[j])
                    j += 1
            parts.append("".join(buf))
            i = j + 1
            continue
        if text.startswith(".trimIndent()", i):
            i += len(".trimIndent()")
            continue
        # Anything else (a comma introducing arrayOf args, etc.) ends the expression.
        break
    return "".join(parts) if parts else None


def _split_top_level_args(text: str) -> list[str]:
    """Split a call's argument list on commas that are not nested or quoted."""
    args, depth, start, i, n = [], 0, 0, 0, len(text)
    while i < n:
        if text.startswith('"""', i):
            i = text.index('"""', i + 3) + 3
            continue
        if text[i] == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        if text[i] in "([{":
            depth += 1
        elif text[i] in ")]}":
            depth -= 1
        elif text[i] == "," and depth == 0:
            args.append(text[start:i])
            start = i + 1
        i += 1
    args.append(text[start:])
    return args


def extract_exec_sql(source: str, substitutions: dict[str, str] | None = None) -> list[str]:
    """Every `execSQL(...)` statement in a Kotlin file, in source order."""
    return extract_sql_calls(source, "execSQL", substitutions)


def extract_sql_calls(
    source: str, call: str, substitutions: dict[str, str] | None = None
) -> list[str]:
    """The SQL passed to every `call(...)` in a Kotlin file, in source order.

    Used for both `execSQL` and `compileStatement`, so the harness sees every
    statement the app runs regardless of how it is issued.
    """
    statements = []
    for match in re.finditer(rf"\b{re.escape(call)}\s*\(", source):
        i = match.end()
        depth, start, n = 1, i, len(source)
        while i < n and depth:
            if source.startswith('"""', i):
                i = source.index('"""', i + 3) + 3
                continue
            if source[i] == '"':
                i += 1
                while i < n and source[i] != '"':
                    i += 2 if source[i] == "\\" else 1
                i += 1
                continue
            if source[i] == "(":
                depth += 1
            elif source[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = source[start:i]
        sql = _parse_string_expr(_split_top_level_args(body)[0])
        if sql:
            statements.append(_substitute(sql, substitutions))
    return statements


def extract_string_list(source: str, marker: str) -> list[str]:
    """The string literals of the `listOf(...)` that follows `marker`."""
    start = source.index(marker)
    open_paren = source.index("listOf(", start) + len("listOf(")
    depth, i, n = 1, open_paren, len(source)
    while i < n and depth:
        if source[i] == '"':
            i += 1
            while i < n and source[i] != '"':
                i += 2 if source[i] == "\\" else 1
        elif source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    return re.findall(r'"((?:[^"\\]|\\.)*)"', source[open_paren:i])


def _strip_line_comments(text: str) -> str:
    """Drop whole-line `//` comments, whose prose can contain commas."""
    return "\n".join(
        "" if line.lstrip().startswith("//") else line
        for line in text.splitlines()
    )


def _substitute(sql: str, substitutions: dict[str, str] | None) -> str:
    if substitutions:
        for key, value in substitutions.items():
            sql = sql.replace(key, value)
    return "\n".join(line.rstrip() for line in sql.strip().splitlines())


def extract_copy_steps(source: str, substitutions: dict[str, str] | None = None) -> list[dict]:
    """The importer's CopyStep list: its read/write SQL pairs, in order.

    The harness replays exactly these statements, so a change to either column
    list shows up as a failed check rather than as bad data on a device.
    """
    start = source.index("COPY_STEPS = listOf(")
    open_paren = source.index("(", start + len("COPY_STEPS = listOf")) + 1
    depth, i, n = 1, open_paren, len(source)
    while i < n and depth:
        if source.startswith('"""', i):
            i = source.index('"""', i + 3) + 3
            continue
        if source[i] == '"':
            i += 1
            while i < n and source[i] != '"':
                i += 2 if source[i] == "\\" else 1
        elif source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    block = source[open_paren:i]

    steps = []
    for match in re.finditer(r"\bCopyStep\s*\(", block):
        j = match.end()
        d, body_start = 1, j
        while j < len(block) and d:
            if block.startswith('"""', j):
                j = block.index('"""', j + 3) + 3
                continue
            if block[j] == '"':
                j += 1
                while j < len(block) and block[j] != '"':
                    j += 2 if block[j] == "\\" else 1
            elif block[j] == "(":
                d += 1
            elif block[j] == ")":
                d -= 1
                if d == 0:
                    break
            j += 1
        step = {}
        for arg in _split_top_level_args(_strip_line_comments(block[body_start:j])):
            name, _, expr = arg.partition("=")
            name = name.strip()
            if name in ("label", "read", "write"):
                value = _parse_string_expr(expr)
                if value is not None:
                    step[name] = _substitute(value, substitutions)
            elif name == "readArgs":
                step["readArgs"] = True
        if "read" in step and "write" in step:
            steps.append(step)
    return steps
