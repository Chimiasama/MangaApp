#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
key_path="${DEBUG_KEYSTORE_PATH:-$repo_root/debug.keystore}"
umask 077
if [[ -n "${SIGNING_KEYSTORE_BASE64:-}" ]]; then
  printf '%s' "$SIGNING_KEYSTORE_BASE64" | base64 --decode > "$key_path"
  echo "Usando a assinatura configurada no Secret."
else
  cp "$repo_root/config/signing/mangareader-public-test.keystore" "$key_path"
  echo "Secret ausente: usando a assinatura pública de teste fixa."
fi
chmod 600 "$key_path"
keytool -list -keystore "$key_path" -storepass android -alias androiddebugkey > /dev/null
