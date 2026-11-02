#!/usr/bin/env bash
# Sets up a new git worktree. Orca runs it for every worktree it creates: the
# repo's Orca setup script is the one line `bash scripts/worktree-setup.sh`.
set -euo pipefail

# The main checkout holds local.properties. Orca names it; plain git worktrees fall back to git.
root="${ORCA_ROOT_PATH:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")}"

# Gradle reads sdk.dir from local.properties, which is gitignored.
if [ ! -f "$root/local.properties" ]; then
  echo "worktree-setup: no local.properties in $root. Create it with sdk.dir=<path to Android SDK>." >&2
  exit 1
fi
ln -sfn "$root/local.properties" local.properties

# Installs commitlint and the husky commit-msg hook.
npm install
