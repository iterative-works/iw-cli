#!/usr/bin/env bash
# PURPOSE: Start the Vite dev server for use alongside `iw dashboard --dev`
# PURPOSE: Installs dependencies and serves frontend assets with HMR on localhost:5173
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${WEBAWESOME_NPM_TOKEN:-}" ]; then
    echo "WARNING: WEBAWESOME_NPM_TOKEN is not set — yarn install may fail for licensed UI components" >&2
fi

yarn install --immutable
exec yarn dev --port 5173 --host localhost
