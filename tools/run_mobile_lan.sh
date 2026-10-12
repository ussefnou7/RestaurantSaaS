#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mobile_root="$(cd "$repo_root/../restaurant_saas_mobile" && pwd)"

host_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
if [[ -z "${host_ip}" ]]; then
  echo "Could not determine the host LAN address." >&2
  echo "Run with: flutter run --dart-define=API_BASE_URL=http://<host-lan-ip>:2020/api" >&2
  exit 1
fi

echo "Starting mobile app against http://${host_ip}:2020/api"
echo "The phone and this computer must be on the same network."

cd "$mobile_root"
flutter run \
  --dart-define="API_BASE_URL=http://${host_ip}:2020/api" \
  "$@"
