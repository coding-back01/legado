#!/usr/bin/env bash

set -euo pipefail

if [[ -z "${GITHUB_OUTPUT:-}" ]]; then
  echo "GITHUB_OUTPUT 未设置，无法输出维护范围" >&2
  exit 1
fi

android=false
web=false

if [[ "$#" -gt 0 ]]; then
  changed_paths=("$@")
else
  changed_paths=()
  while IFS= read -r -d '' path; do
    changed_paths+=("$path")
  done
fi

for path in "${changed_paths[@]}"; do
  case "$path" in
    modules/web/*)
      web=true
      ;;
    app/src/main/assets/web/vue/*)
      android=true
      web=true
      ;;
    app/*|modules/book/*|modules/rhino/*|gradle/*|gradlew|gradlew.bat|build.gradle|settings.gradle|gradle.properties)
      android=true
      ;;
    .github/workflows/test.yml|.github/scripts/classify-maintenance-scope.sh|.github/scripts/test_maintenance_workflow.py)
      android=true
      web=true
      ;;
    docs/*|openspec/*|README.md|English.md|AGENTS.md|LICENSE|.gitignore|.github/ISSUE_TEMPLATE/*|.github/dependabot.yml)
      ;;
    *)
      # 未知代码或构建入口采用 fail-safe：两个构建范围都执行。
      android=true
      web=true
      ;;
  esac
done

printf 'android=%s\nweb=%s\n' "$android" "$web" >> "$GITHUB_OUTPUT"
