#!/bin/bash

# Base URL (assuming running locally)
BASE_URL="http://localhost:8080"

# Generate random IMEI (15 digits) using jot for macOS compatibility
IMEI=$(jot -r 1 100000000000000 999999999999999)

# Payload matching Android DeviceManager.kt
PAYLOAD=$(cat <<EOF
{
  "imei": "$IMEI",
  "model": "ShellScriptVerifier",
  "os_version": "1.0",
  "tee_type": "TRUST_ZONE",
  "public_key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAz...",
  "device_mode": "FULL_POS"
}
EOF
)

echo "Sending registration request to $BASE_URL/api/v1/devices/register..."
echo "Payload: $PAYLOAD"

# Use -v for verbose output to debug connection issues
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/devices/register" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

exit_code=$?

if [ $exit_code -ne 0 ]; then
    echo "❌ Curl failed with exit code $exit_code. Is the backend running on $BASE_URL?"
    exit 1
fi

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "Response Code: $HTTP_CODE"
echo "Response Body: $BODY"

if [ "$HTTP_CODE" -eq 201 ]; then
  echo "✅ Backend compatibility verified: Registration succeeded."
  exit 0
else
  echo "❌ Backend compatibility failed: Expected 201, got $HTTP_CODE"
  exit 1
fi
