#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test -f "$root/docs/examples/getting-started/pom.xml"

for pom in "$root"/docs/examples/*/pom.xml "$root/modules/client/examples/complete-example/pom.xml"; do
    echo "Verifying standalone example: ${pom#"$root/"}"
    "$root/mvnw" -B -ntp "$@" -f "$pom" clean verify
done
