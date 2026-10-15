#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REGISTER_FILE="${ROOT_DIR}/data/ops/vendor_register.example.csv"

if [[ ! -f "${REGISTER_FILE}" ]]; then
  echo "[vendor-register-check] Missing file: data/ops/vendor_register.example.csv"
  exit 1
fi

header_expected="vendor,service_owner,service_workflow,data_sensitivity,risk_tier,review_cadence,control_checks,stakeholders"
header_actual="$(head -n 1 "${REGISTER_FILE}" | tr -d '\r')"

if [[ "${header_actual}" != "${header_expected}" ]]; then
  echo "[vendor-register-check] Invalid CSV header"
  echo "[vendor-register-check] Expected: ${header_expected}"
  echo "[vendor-register-check] Actual:   ${header_actual}"
  exit 1
fi

line_count=$(wc -l < "${REGISTER_FILE}")
if (( line_count < 2 )); then
  echo "[vendor-register-check] Vendor register has no entries"
  exit 1
fi

critical_count=0

while IFS=',' read -r vendor owner workflow sensitivity tier cadence checks stakeholders; do
  if [[ "${vendor}" == "vendor" ]]; then
    continue
  fi

  if [[ -z "${vendor}" || -z "${owner}" || -z "${workflow}" || -z "${sensitivity}" || -z "${tier}" || -z "${cadence}" || -z "${checks}" || -z "${stakeholders}" ]]; then
    echo "[vendor-register-check] Blank required field in row: ${vendor:-<empty>}"
    exit 1
  fi

  case "${sensitivity}" in
    PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED) ;;
    *)
      echo "[vendor-register-check] Invalid data_sensitivity for ${vendor}: ${sensitivity}"
      exit 1
      ;;
  esac

  case "${tier}" in
    LOW|MEDIUM|HIGH|CRITICAL) ;;
    *)
      echo "[vendor-register-check] Invalid risk_tier for ${vendor}: ${tier}"
      exit 1
      ;;
  esac

  case "${cadence}" in
    monthly|quarterly|semi-annual|annual) ;;
    *)
      echo "[vendor-register-check] Invalid review_cadence for ${vendor}: ${cadence}"
      exit 1
      ;;
  esac

  if [[ "${tier}" == "CRITICAL" ]]; then
    critical_count=$((critical_count + 1))
  fi
done < "${REGISTER_FILE}"

if (( critical_count == 0 )); then
  echo "[vendor-register-check] At least one CRITICAL dependency is required"
  exit 1
fi

echo "[vendor-register-check] PASS (${line_count}-1 entries, ${critical_count} critical dependencies)"
