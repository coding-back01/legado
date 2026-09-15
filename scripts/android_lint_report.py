#!/usr/bin/env python3
"""只读解析 Android lint XML，并生成可审计 occurrence 清单。"""

from __future__ import annotations

import argparse
import csv
import sys
import xml.etree.ElementTree as ElementTree
from collections import Counter
from pathlib import Path
from typing import TextIO


EXIT_INVALID_REPORT = 2
EXIT_ISSUES_FOUND = 1
INVENTORY_HEADER = (
    "occurrence",
    "severity",
    "id",
    "file",
    "line",
    "column",
    "message",
)


class InvalidReportError(ValueError):
    """lint XML 缺失或格式不符合预期。"""


def parse_report(report: Path) -> list[ElementTree.Element]:
    if not report.is_file():
        raise InvalidReportError(f"报告不存在: {report}")

    try:
        root = ElementTree.parse(report).getroot()
    except ElementTree.ParseError as error:
        raise InvalidReportError(f"XML 解析失败: {report}: {error}") from error
    except OSError as error:
        raise InvalidReportError(f"报告读取失败: {report}: {error}") from error

    if root.tag != "issues":
        raise InvalidReportError(
            f"lint XML 根节点必须为 <issues>，实际为 <{root.tag}>: {report}"
        )

    return list(root.iterfind(".//issue"))


def write_inventory(issues: list[ElementTree.Element], output: TextIO) -> None:
    writer = csv.writer(output, delimiter="\t", lineterminator="\n")
    writer.writerow(INVENTORY_HEADER)
    for occurrence, issue in enumerate(issues, start=1):
        locations = issue.findall("location") or [None]
        for location in locations:
            writer.writerow(
                (
                    occurrence,
                    issue.get("severity", ""),
                    issue.get("id", ""),
                    "" if location is None else location.get("file", ""),
                    "" if location is None else location.get("line", ""),
                    "" if location is None else location.get("column", ""),
                    issue.get("message", ""),
                )
            )


def write_summary(issues: list[ElementTree.Element], output: TextIO) -> None:
    counts = Counter(
        (issue.get("severity", ""), issue.get("id", "")) for issue in issues
    )
    print(f"总计: {len(issues)}", file=output)
    for (severity, issue_id), count in sorted(counts.items()):
        print(f"{severity}/{issue_id}: {count}", file=output)


def inventory(report: Path, output_path: Path | None) -> int:
    issues = parse_report(report)
    if output_path is None:
        write_inventory(issues, sys.stdout)
    else:
        if output_path.resolve() == report.resolve():
            raise InvalidReportError("清单输出不得覆盖 lint XML 报告")
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with output_path.open("w", encoding="utf-8", newline="") as output:
            write_inventory(issues, output)
    write_summary(issues, sys.stderr)
    return 0


def assert_zero(report: Path) -> int:
    issues = parse_report(report)
    if issues:
        print("Android lint 零 issue 断言失败", file=sys.stderr)
        write_summary(issues, sys.stderr)
        return EXIT_ISSUES_FOUND

    write_summary(issues, sys.stdout)
    print("Android lint 零 issue 断言通过", file=sys.stdout)
    return 0


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    inventory_parser = subparsers.add_parser(
        "inventory", help="生成逐 occurrence TSV 清单并输出 severity/ID 汇总"
    )
    inventory_parser.add_argument("report", type=Path, help="lint XML 报告路径")
    inventory_parser.add_argument(
        "--output", "-o", type=Path, help="TSV 输出路径；省略时写入标准输出"
    )
    assert_zero_parser = subparsers.add_parser(
        "assert-zero", help="断言 lint XML 合法且不包含任何 issue"
    )
    assert_zero_parser.add_argument("report", type=Path, help="lint XML 报告路径")
    return parser


def main() -> int:
    arguments = create_parser().parse_args()
    try:
        if arguments.command == "inventory":
            return inventory(arguments.report, arguments.output)
        if arguments.command == "assert-zero":
            return assert_zero(arguments.report)
    except InvalidReportError as error:
        print(f"错误: {error}", file=sys.stderr)
        return EXIT_INVALID_REPORT
    raise AssertionError(f"未处理的命令: {arguments.command}")


if __name__ == "__main__":
    sys.exit(main())
