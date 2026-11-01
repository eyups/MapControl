#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
FIXTURE="${1:?fixture dosyası gerekli}"
if [[ ! -f "$FIXTURE" ]]; then
  FIXTURE="$ROOT/fixtures/$FIXTURE"
fi
if [[ ! -f "$FIXTURE" ]]; then
  echo "[FAIL] fixture yok: $1" >&2
  exit 1
fi

while read -r event cmd values; do
  [[ -z "${event:-}" || "$event" == \#* ]] && continue
  [[ -z "${cmd:-}" ]] && continue
  # values "2 3" gibi çoklu olabilir; send_fake_event son argümanları birleştirir.
  "$ROOT/send_fake_event.sh" "$event" "$cmd" ${values:-0}
done < "$FIXTURE"
