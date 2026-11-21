#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"
./init-databases.sh

echo "==> initialization complete"
