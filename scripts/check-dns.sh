#!/usr/bin/env bash
set -euo pipefail

VPS_IPV4="${VPS_IPV4:?Set VPS_IPV4 to your shawerma VPS IPv4 address}"
KITCHEN_FQDN="xn--80aai8a.xn--e1atebk.xn--80ad9adi.xn--80aaahcle4cg3byf.xn--p1ai"
CLIENT_FQDN="xn--80aaahcle4cg3byf.xn--p1ai"
WWW_FQDN="www.xn--80aaahcle4cg3byf.xn--p1ai"
MUSEUM_FQDN="xn--e1aaahcksfb3aueo.xn--p1ai"

check() {
  local label="$1"
  local fqdn="$2"
  local expected="${3:-}"
  local resolved
  resolved="$(dig +short A "$fqdn" @8.8.8.8 | tail -1 || true)"
  if [[ -n "$expected" && "$resolved" == "$expected" ]]; then
    echo "OK   $label -> $resolved"
  elif [[ -n "$resolved" ]]; then
    echo "WARN $label -> $resolved (expected ${expected:-any})"
  else
    echo "FAIL $label -> NXDOMAIN (expected ${expected:-any})"
  fi
}

echo "DNS check (Google 8.8.8.8):"
check "client" "$CLIENT_FQDN" "$VPS_IPV4"
check "kitchen" "$KITCHEN_FQDN" "$VPS_IPV4"
check "www (kitchen fallback)" "$WWW_FQDN" "$VPS_IPV4"
check "museum" "$MUSEUM_FQDN" ""
