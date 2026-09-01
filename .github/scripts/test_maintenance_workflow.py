#!/usr/bin/env python3
"""维护门禁与路径范围分类的仓库契约测试。"""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
TEST_WORKFLOW_PATH = REPOSITORY_ROOT / ".github/workflows/test.yml"
RELEASE_WORKFLOW_PATH = REPOSITORY_ROOT / ".github/workflows/release.yml"
STALE_WORKFLOW_PATH = REPOSITORY_ROOT / ".github/workflows/stale.yml"
CLASSIFIER_PATH = REPOSITORY_ROOT / ".github/scripts/classify-maintenance-scope.sh"
WEB_PACKAGE_PATH = REPOSITORY_ROOT / "modules/web/package.json"

TARGET_ACTION_MAJORS = {
    "actions/checkout": 7,
    "actions/setup-java": 6,
    "gradle/actions/setup-gradle": 6,
    "actions/upload-artifact": 7,
    "actions/download-artifact": 8,
    "actions/stale": 11,
    "pnpm/action-setup": 6,
    "actions/setup-node": 7,
}


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
        cls.workflow = TEST_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.release_workflow = RELEASE_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.stale_workflow = STALE_WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.web_package = json.loads(WEB_PACKAGE_PATH.read_text(encoding="utf-8"))

    def assert_action_matrix(
        self,
        workflow: str,
        expected_actions: set[str],
    ) -> None:
        actual: dict[str, set[int]] = {}
        for action, major in re.findall(r"uses:\s+([^\s@]+)@v(\d+)", workflow):
            if action in TARGET_ACTION_MAJORS:
                actual.setdefault(action, set()).add(int(major))
        self.assertEqual(
            {
                action: {TARGET_ACTION_MAJORS[action]}
                for action in expected_actions
            },
            actual,
        )

    def action_steps(self, workflow: str, action: str) -> list[str]:
        return [
            step
            for step in workflow.split("\n      - ")
            if f"uses: {action}@" in step
        ]

    def test_each_workflow_uses_only_approved_action_majors(self) -> None:
        self.assert_action_matrix(
            self.workflow,
            {
                "actions/checkout",
                "actions/setup-java",
                "gradle/actions/setup-gradle",
                "actions/upload-artifact",
                "actions/download-artifact",
                "pnpm/action-setup",
                "actions/setup-node",
            },
        )
        self.assert_action_matrix(
            self.release_workflow,
            {
                "actions/checkout",
                "actions/setup-java",
                "gradle/actions/setup-gradle",
                "actions/upload-artifact",
                "actions/download-artifact",
            },
        )
        self.assert_action_matrix(self.stale_workflow, {"actions/stale"})

    def test_node24_actions_stay_on_github_hosted_runners(self) -> None:
        for workflow in (
            self.workflow,
            self.release_workflow,
            self.stale_workflow,
        ):
            self.assertNotIn("self-hosted", workflow)
            runs_on = {
                line.strip()
                for line in workflow.splitlines()
                if line.strip().startswith("runs-on:")
            }
            self.assertGreater(len(runs_on), 0)
            self.assertEqual({"runs-on: ubuntu-latest"}, runs_on)

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
            "actions/setup-java@v6",
            "java-version: 17",
            ":app:testAppDebugUnitTest",
            ":app:lintAppDebug",
            ":app:assembleAppDebug",
            "actions/upload-artifact@v7",
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
            "pnpm/action-setup@v6",
            "version: 9.15.9",
            "pnpm install --frozen-lockfile",
            "pnpm test:chapter-html",
            "pnpm test:static-links",
            "pnpm type-check",
            "pnpm exec eslint .",
            "pnpm build",
            "app/src/main/assets/web/vue",
        ):
            self.assertIn(token, self.workflow)
        self.assertNotIn("git commit", self.workflow)
        self.assertNotIn("git push", self.workflow)
        self.assertEqual(
            "node --test tests/staticLinks.test.mjs && "
            "node scripts/check-static-links.mjs",
            self.web_package["scripts"]["test:static-links"],
        )

    def test_non_push_checkouts_do_not_persist_credentials(self) -> None:
        checkout_steps = self.action_steps(self.workflow, "actions/checkout")
        self.assertGreater(len(checkout_steps), 0)
        for step in checkout_steps:
            self.assertIn("persist-credentials: false", step)

    def test_gradle_cache_and_node_cache_ownership_are_explicit(self) -> None:
        gradle_steps = self.action_steps(
            self.workflow,
            "gradle/actions/setup-gradle",
        )
        self.assertGreater(len(gradle_steps), 0)
        for step in gradle_steps:
            self.assertIn("cache-provider: basic", step)

        pnpm_steps = self.action_steps(self.workflow, "pnpm/action-setup")
        self.assertEqual(1, len(pnpm_steps))
        self.assertIn("version: 9.15.9", pnpm_steps[0])
        self.assertIn("run_install: false", pnpm_steps[0])
        self.assertIn("cache: false", pnpm_steps[0])

        node_steps = self.action_steps(self.workflow, "actions/setup-node")
        self.assertGreater(len(node_steps), 0)
        for step in node_steps:
            self.assertIn("package-manager-cache: false", step)
        pnpm_cache_steps = [step for step in node_steps if "cache: pnpm" in step]
        self.assertEqual(1, len(pnpm_cache_steps))
        self.assertIn(
            "cache-dependency-path: modules/web/pnpm-lock.yaml",
            pnpm_cache_steps[0],
        )

    def test_artifact_actions_use_named_archives_and_strict_downloads(self) -> None:
        upload_steps = self.action_steps(self.workflow, "actions/upload-artifact")
        self.assertGreater(len(upload_steps), 0)
        for step in upload_steps:
            self.assertIn("archive: true", step)
            self.assertNotIn("overwrite:", step)
            self.assertNotIn("include-hidden-files:", step)

        download_steps = self.action_steps(
            self.workflow,
            "actions/download-artifact",
        )
        self.assertEqual(1, len(download_steps))
        for token in (
            "name: action-upgrade-fixture-${{ github.run_id }}-${{ github.run_attempt }}",
            "skip-decompress: false",
            "digest-mismatch: error",
        ):
            self.assertIn(token, download_steps[0])

        for token in (
            "artifact-upload:",
            "artifact-download:",
            "needs: artifact-upload",
            "retention-days: 1",
            "sha256sum --check --strict payload.sha256",
        ):
            self.assertIn(token, self.workflow)

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

    def test_android_codeql_compiles_sources_without_gradle_build_cache(self) -> None:
        codeql_section = self.workflow.split("\n  codeql:", 1)[1].split(
            "\n  gate:", 1
        )[0]
        self.assertIn("--no-build-cache", codeql_section)
        self.assertNotIn("--build-cache", codeql_section)

    def test_stable_aggregate_gate_distinguishes_skip_and_failure(self) -> None:
        for token in (
            "name: 维护门禁",
            "needs: [scope, android, web, repository, codeql, artifact-download]",
            "needs.android.result",
            "needs.web.result",
            "needs.repository.result",
            "needs.codeql.result",
            "needs.artifact-download.result",
            'require_success "Artifact 下载核验" "$ARTIFACT_RESULT"',
        ):
            self.assertIn(token, self.workflow)

    def test_legacy_web_workflow_is_removed(self) -> None:
        self.assertFalse((REPOSITORY_ROOT / ".github/workflows/web.yml").exists())

    def test_stale_is_issue_only_with_exact_minimum_permissions(self) -> None:
        permission_lines = {
            line.strip()
            for line in self.stale_workflow.split("permissions:", 1)[1]
            .split("\n\n    steps:", 1)[0]
            .splitlines()
            if line.strip()
        }
        self.assertEqual({"issues: write", "contents: read"}, permission_lines)
        for token in (
            "only-issue-labels: needs-info",
            "exempt-issue-labels: crash,data-loss,security",
            "days-before-issue-stale: 30",
            "days-before-pr-stale: -1",
            "days-before-pr-close: -1",
            "remove-issue-stale-when-updated: true",
        ):
            self.assertIn(token, self.stale_workflow)
        for token in (
            "pull-requests: write",
            "contents: write",
            "actions: write",
            "releases: write",
        ):
            self.assertNotIn(token, self.stale_workflow)


if __name__ == "__main__":
    unittest.main(verbosity=2)
