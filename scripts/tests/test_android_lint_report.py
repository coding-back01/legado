import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts" / "android_lint_report.py"


class AndroidLintReportTest(unittest.TestCase):

    def run_inventory(self, report: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "inventory", str(report)],
            cwd=REPOSITORY_ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def run_assert_zero(self, report: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "assert-zero", str(report)],
            cwd=REPOSITORY_ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_missing_report_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_inventory(Path(directory) / "missing.xml")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("报告不存在", result.stderr)

    def test_malformed_xml_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<issues><issue>", encoding="utf-8")
            result = self.run_inventory(report)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("XML 解析失败", result.stderr)

    def test_wrong_root_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<report />", encoding="utf-8")
            result = self.run_inventory(report)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("根节点必须为 <issues>", result.stderr)

    def test_empty_report_succeeds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<issues />", encoding="utf-8")
            result = self.run_inventory(report)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("occurrence\tseverity\tid\tfile\tline\tcolumn\tmessage", result.stdout)
        self.assertIn("总计: 0", result.stderr)

    def test_inventory_expands_locations_and_summarizes_severity_and_id(self) -> None:
        xml = textwrap.dedent(
            """\
            <issues>
              <issue id="Overdraw" severity="Warning" message="background">
                <location file="/repo/a.xml" line="7" column="3" />
                <location file="/repo/b.xml" line="9" column="5" />
              </issue>
              <issue id="TrimLambda" severity="Hint" message="trim">
                <location file="/repo/c.kt" line="11" column="13" />
              </issue>
            </issues>
            """
        )
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text(xml, encoding="utf-8")
            result = self.run_inventory(report)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("1\tWarning\tOverdraw\t/repo/a.xml\t7\t3\tbackground", result.stdout)
        self.assertIn("1\tWarning\tOverdraw\t/repo/b.xml\t9\t5\tbackground", result.stdout)
        self.assertIn("2\tHint\tTrimLambda\t/repo/c.kt\t11\t13\ttrim", result.stdout)
        self.assertIn("总计: 2", result.stderr)
        self.assertIn("Hint/TrimLambda: 1", result.stderr)
        self.assertIn("Warning/Overdraw: 1", result.stderr)

    def test_assert_zero_accepts_empty_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<issues />", encoding="utf-8")
            result = self.run_assert_zero(report)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("总计: 0", result.stdout)
        self.assertIn("Android lint 零 issue 断言通过", result.stdout)

    def test_assert_zero_rejects_warning_with_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text(
                '<issues><issue id="Overdraw" severity="Warning" /></issues>',
                encoding="utf-8",
            )
            result = self.run_assert_zero(report)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("总计: 1", result.stderr)
        self.assertIn("Warning/Overdraw: 1", result.stderr)

    def test_assert_zero_rejects_hint_with_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text(
                '<issues><issue id="TrimLambda" severity="Hint" /></issues>',
                encoding="utf-8",
            )
            result = self.run_assert_zero(report)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("总计: 1", result.stderr)
        self.assertIn("Hint/TrimLambda: 1", result.stderr)

    def test_assert_zero_rejects_missing_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_assert_zero(Path(directory) / "missing.xml")

        self.assertEqual(2, result.returncode)
        self.assertIn("报告不存在", result.stderr)

    def test_assert_zero_rejects_wrong_root(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<report />", encoding="utf-8")
            result = self.run_assert_zero(report)

        self.assertEqual(2, result.returncode)
        self.assertIn("根节点必须为 <issues>", result.stderr)

    def test_assert_zero_rejects_malformed_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "lint.xml"
            report.write_text("<issues><issue>", encoding="utf-8")
            result = self.run_assert_zero(report)

        self.assertEqual(2, result.returncode)
        self.assertIn("XML 解析失败", result.stderr)


if __name__ == "__main__":
    unittest.main()
