#!/usr/bin/env python3
"""Keep the translations honest.

Checks that every string resource is defined in every language, that format
placeholders match across translations, and that no string mixes a literal
percent sign with format arguments in a way that would blow up at runtime.

Run with no arguments from the repository root.
"""

from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "app", "src", "main", "res")
BASE = os.path.join(RES, "values", "strings.xml")

# %s, %1$s, %.2f, %1$d ... but not the escaped literal "%%".
_SPEC = r"(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]"
PLACEHOLDER = re.compile(rf"(?<!%)%{_SPEC}")
# A '%' that starts neither an escape nor a valid conversion.
LITERAL_PERCENT = re.compile(rf"(?<!%)%(?!%)(?!{_SPEC})")

# Strings the app deliberately never translates.
UNTRANSLATED = {"app_name"}

failures: list[str] = []


def entries(path: str) -> dict[str, str]:
    """name -> text, for both <string> and <plurals> (items concatenated)."""
    out: dict[str, str] = {}
    for child in ET.parse(path).getroot():
        name = child.get("name")
        if name is None:
            continue
        out[name] = " ".join("".join(part.itertext()) for part in child) or \
            "".join(child.itertext())
    return out


def placeholders(text: str) -> list[str]:
    return sorted(m.group(0) for m in PLACEHOLDER.finditer(text))


def check_positional_arguments(language: str, values: dict[str, str]) -> None:
    """
    AAPT2 rejects a string with more than one substitution unless every one of
    them is numbered ("%1$s" rather than "%s"), so catch it here rather than at
    build time.
    """
    for name, text in values.items():
        found = PLACEHOLDER.findall(text)
        if len(found) > 1 and any(index == "" for index in found):
            failures.append(
                f"{language}/{name}: multiple substitutions must be numbered, e.g. '%1$s'"
            )


def check_percent_usage(language: str, values: dict[str, str]) -> None:
    """
    A literal '%' is only safe in a string that never reaches String.format.
    Anything with format arguments must escape it as '%%'; anything without
    must not use '%%', which would then be shown to the user verbatim.
    """
    for name, text in values.items():
        formatted = bool(placeholders(text))
        if formatted and LITERAL_PERCENT.search(text):
            failures.append(
                f"{language}/{name}: has format arguments and a bare '%' — escape it as '%%'"
            )
        if not formatted and "%%" in text:
            failures.append(
                f"{language}/{name}: '%%' in a string with no format arguments would "
                f"be shown literally"
            )


def main() -> None:
    if not os.path.exists(BASE):
        sys.exit(f"base strings not found: {BASE}")

    base = entries(BASE)
    check_percent_usage("values", base)
    check_positional_arguments("values", base)
    print(f"values: {len(base)} resources")

    translations = sorted(
        d for d in os.listdir(RES)
        if d.startswith("values-") and os.path.exists(os.path.join(RES, d, "strings.xml"))
    )
    if not translations:
        print("no translations to check")

    for directory in translations:
        path = os.path.join(RES, directory, "strings.xml")
        values = entries(path)
        print(f"{directory}: {len(values)} resources")

        for name in sorted(set(base) - set(values) - UNTRANSLATED):
            failures.append(f"{directory}: missing '{name}'")
        for name in sorted(set(values) - set(base)):
            failures.append(f"{directory}: '{name}' has no entry in values/strings.xml")

        for name in sorted(set(base) & set(values)):
            want, got = placeholders(base[name]), placeholders(values[name])
            if want != got:
                failures.append(
                    f"{directory}/{name}: placeholders {got} do not match {want}"
                )

        check_percent_usage(directory, values)
        check_positional_arguments(directory, values)

    if failures:
        print()
        for failure in failures:
            print("  FAIL ", failure)
        sys.exit(1)
    print("\nall string resources consistent")


if __name__ == "__main__":
    main()
