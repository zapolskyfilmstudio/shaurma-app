#!/usr/bin/env bash
# Restore DNS after internetmuseum.ru A record was pointed at the shawerma VPS.
#
# Requires Beget API credentials:
#   export BEGET_LOGIN=your_beget_login
#   export BEGET_PASSWORD=your_beget_password
#
# What it does:
# 1. Adds A record for kitchen subdomain -> VPS_IPV4
# 2. Optionally moves internetmuseum.ru off the shawerma VPS (Beget parking IP)

set -euo pipefail

VPS_IPV4="${VPS_IPV4:?Set VPS_IPV4 to your shawerma VPS IPv4 address}"
BEGET_PARKING_IP="${BEGET_PARKING_IP:-185.50.27.12}"
KITCHEN_FQDN="xn--80aai8a.xn--e1atebk.xn--80ad9adi.xn--80aaahcle4cg3byf.xn--p1ai"
MUSEUM_FQDN="xn--e1aaahcksfb3aueo.xn--p1ai"
FIX_MUSEUM_DNS="${FIX_MUSEUM_DNS:-0}"

if [[ -z "${BEGET_LOGIN:-}" || -z "${BEGET_PASSWORD:-}" ]]; then
  echo "Set BEGET_LOGIN and BEGET_PASSWORD environment variables." >&2
  exit 1
fi

api() {
  local method="$1"
  local input_data="$2"
  curl -fsS "https://api.beget.com/api/dns/${method}" \
    --data-urlencode "login=${BEGET_LOGIN}" \
    --data-urlencode "passwd=${BEGET_PASSWORD}" \
    --data-urlencode "input_format=json" \
    --data-urlencode "output_format=json" \
    --data-urlencode "input_data=${input_data}"
}

check_dns() {
  local fqdn="$1"
  local expected_ip="$2"
  local resolved
  resolved="$(dig +short A "$fqdn" @8.8.8.8 | tail -1 || true)"
  if [[ "$resolved" == "$expected_ip" ]]; then
    echo "OK  $fqdn -> $resolved"
    return 0
  fi
  echo "BAD $fqdn -> ${resolved:-NXDOMAIN} (expected $expected_ip)"
  return 1
}

set_a_record() {
  local fqdn="$1"
  local ip="$2"
  echo "Setting A record: ${fqdn} -> ${ip}"
  api changeRecords "{\"fqdn\":\"${fqdn}\",\"records\":{\"A\":[{\"priority\":10,\"value\":\"${ip}\"}]}}"
}

echo "==> Before"
check_dns "$KITCHEN_FQDN" "$SHAWARMA_VPS_IP" || true
if [[ "$FIX_MUSEUM_DNS" == "1" ]]; then
  check_dns "$MUSEUM_FQDN" "$BEGET_PARKING_IP" || true
fi

echo "==> Updating Beget DNS"
set_a_record "$KITCHEN_FQDN" "$SHAWARMA_VPS_IP"

if [[ "$FIX_MUSEUM_DNS" == "1" ]]; then
  set_a_record "$MUSEUM_FQDN" "$BEGET_PARKING_IP"
fi

echo "==> After (may take up to 10 minutes to propagate globally)"
sleep 3
check_dns "$KITCHEN_FQDN" "$SHAWARMA_VPS_IP" || echo "Kitchen DNS not visible yet; retry in a few minutes."
if [[ "$FIX_MUSEUM_DNS" == "1" ]]; then
  check_dns "$MUSEUM_FQDN" "$BEGET_PARKING_IP" || true
fi

echo "Done."
