#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"
./init-databases.sh
./init-storage.sh

echo "==> initialization complete"
