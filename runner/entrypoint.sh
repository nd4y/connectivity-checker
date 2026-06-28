#!/bin/bash
set -e

# Required env vars:
#   GITHUB_REPO  — e.g. https://github.com/username/repo
#   RUNNER_TOKEN — registration token from GitHub repo settings

./config.sh \
    --url "${GITHUB_REPO}" \
    --token "${RUNNER_TOKEN}" \
    --name "${RUNNER_NAME:-docker-runner}" \
    --labels "${RUNNER_LABELS:-self-hosted,Linux,android}" \
    --work "_work" \
    --unattended \
    --replace

cleanup() {
    ./config.sh remove --token "${RUNNER_TOKEN}"
}
trap cleanup EXIT

./run.sh
