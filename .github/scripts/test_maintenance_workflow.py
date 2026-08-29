#!/usr/bin/env python3
"""维护门禁与路径范围分类的仓库契约测试。"""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPOSITORY_ROOT / ".github/workflows/test.yml"
CLASSIFIER_PATH = REPOSITORY_ROOT / ".github/scripts/classify-maintenance-scope.sh"


class MaintenanceScopeContractTest(unittest.TestCase):
    def classify(self, *paths: str) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "github-output"
            environment = os.environ.copy()
            environment["GITHUB_OUTPUT"] = str(output_path)
            subprocess.run(
                ["bash", str(CLASSIFIER_PATH), *paths],
                cwd=REPOSITORY_ROOT,
                env=environment,
                check=True,
                text=True,
                capture_output=True,
            )
            return dict(
                line.split("=", 1)
                for line in output_path.read_text(encoding="utf-8").splitlines()
            )

    def assert_scope(self, android: bool, web: bool, *paths: str) -> None:
        self.assertEqual(
            {
                "android": str(android).lower(),
                "web": str(web).lower(),
            },
            self.classify(*paths),
        )

    def test_android_only_scope(self) -> None:
        self.assert_scope(True, False, "app/src/main/java/example.kt")

    def test_web_only_scope(self) -> None:
        self.assert_scope(False, True, "modules/web/src/App.vue")

    def test_documentation_and_openspec_scope(self) -> None:
        self.assert_scope(
            False,
            False,
            "README.md",
            "docs/maintenance-baseline.md",
            "openspec/changes/example/spec.md",
        )

    def test_mixed_scope(self) -> None:
        self.assert_scope(
            True,
            True,
            "modules/book/src/main/java/Book.kt",
            "modules/web/src/App.vue",
        )

    def test_ci_implementation_changes_run_both_builds(self) -> None:
        self.assert_scope(
            True,
            True,
            ".github/workflows/test.yml",
            ".github/scripts/classify-maintenance-scope.sh",
        )


class MaintenanceWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_all_pull_requests_and_master_pushes_trigger(self) -> None:
        self.assertIn("pull_request:", self.workflow)
        self.assertIn("push:", self.workflow)
        self.assertIn("- master", self.workflow)
        self.assertNotIn("paths-ignore:", self.workflow)
        self.assertNotIn("workflow_run:", self.workflow)
        self.assertIn(
            "cancel-in-progress: ${{ github.event_name == 'pull_request' }}",
            self.workflow,
        )

    def test_android_gate_uses_jdk17_and_debug_only(self) -> None:
        for token in (
            "actions/setup-java@v5",
            "java-version: 17",
            ":app:testAppDebugUnitTest",
            ":app:lintAppDebug",
            ":app:assembleAppDebug",
            "actions/upload-artifact@v6",
            "if: always()",
            "retention-days:",
        ):
            self.assertIn(token, self.workflow)
        for secret in (
            "RELEASE_KEY_STORE",
            "RELEASE_STORE_PASSWORD",
            "RELEASE_KEY_ALIAS",
            "RELEASE_KEY_PASSWORD",
        ):
            self.assertNotIn(secret, self.workflow)

    def test_web_gate_is_frozen_and_read_only(self) -> None:
        for token in (
            "node-version: 22",
            "pnpm/action-setup@v5",
            "version: 9.15.9",
            "pnpm install --frozen-lockfile",
            "pnpm test:chapter-html",
            "pnpm type-check",
            "pnpm exec eslint .",
            "pnpm build",
            "app/src/main/assets/web/vue",
        ):
            self.assertIn(token, self.workflow)
        self.assertNotIn("git commit", self.workflow)
        self.assertNotIn("git push", self.workflow)

    def test_repository_gate_pins_openspec_and_actionlint(self) -> None:
        for token in (
            "@fission-ai/openspec@1.8.0",
            "openspec validate --all --strict",
            "ACTIONLINT_VERSION: 1.7.12",
            "git diff --check",
        ):
            self.assertIn(token, self.workflow)

    def test_codeql_runs_both_languages_without_hidden_failures(self) -> None:
        for token in (
            "github/codeql-action/init@v4",
            "github/codeql-action/analyze@v4",
            "java-kotlin",
            "javascript-typescript",
            "build-mode: manual",
            "build-mode: none",
            "security-events: write",
        ):
            self.assertIn(token, self.workflow)
        self.assertNotIn("packages: read", self.workflow)
        self.assertNotIn("continue-on-error", self.workflow)

    def test_stable_aggregate_gate_distinguishes_skip_and_failure(self) -> None:
        for token in (
            "name: 维护门禁",
            "needs: [scope, android, web, repository, codeql]",
            "needs.android.result",
            "needs.web.result",
            "needs.repository.result",
            "needs.codeql.result",
        ):
            self.assertIn(token, self.workflow)

    def test_legacy_web_workflow_is_removed(self) -> None:
        self.assertFalse((REPOSITORY_ROOT / ".github/workflows/web.yml").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
