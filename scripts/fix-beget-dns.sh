#!/usr/bin/env bash
# Restore DNS after internetmuseum.ru A record was pointed at the shawerma VPS.
#
# Requires Beget API credentials:
#   export BEGET_LOGIN=your_beget_login
#   export BEGET_PASSWORD=your_beget_password
#
# What it does:
# 1. Adds A record for kitchen subdomain -> SHAWARMA_VPS_IP
# 2. Removes shawerma VPS IP from internetmuseum.ru (sets Beget parking IP)

set -euo pipefail

SHAWARMA_VPS_IP="${SHAWARMA_VPS_IP:?Set SHAWARMA_VPS_IP to your shawerma VPS IPv4}"
BEGET_PARKING_IP="${BEGET_PARKING_IP:-185.50.27.12}"
KITCHEN_FQDN="xn--80aai8a.xn--e1atebk.xn--80ad9adi.xn--80aaahcle4cg3byf.xn--p1ai"
MUSEUM_FQDN="xn--e1aaahcksfb3aueo.xn--p1ai"

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

echo "Setting kitchen A record: ${KITCHEN_FQDN} -> ${SHAWARMA_VPS_IP}"
api changeRecords "{\"fqdn\":\"${KITCHEN_FQDN}\",\"records\":{\"A\":[{\"priority\":10,\"value\":\"${SHAWARMA_VPS_IP}\"}]}}"

echo "Setting museum A record: ${MUSEUM_FQDN} -> ${BEGET_PARKING_IP}"
api changeRecords "{\"fqdn\":\"${MUSEUM_FQDN}\",\"records\":{\"A\":[{\"priority\":10,\"value\":\"${BEGET_PARKING_IP}\"}]}}"

echo "Done. DNS may take up to 10 minutes to propagate."
