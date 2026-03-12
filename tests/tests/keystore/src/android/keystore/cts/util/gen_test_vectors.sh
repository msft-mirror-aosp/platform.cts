#!/bin/bash

# This script generates a CA certificate, user key, and user certificate signed
# by the CA, for the set of supported algorithms, and prints their hex strings
# to stdout.
#
# The hex strings can be copied into test files (e.g. AndroidKeyStoreTest.java).

# Usage: ./gen_test_vectors.sh [rsa|ec]
ALGO=$1

if [[ -z "$ALGO" ]]; then
    echo "Usage: $0 [rsa|ec]"
    exit 1
fi

# Create and move into a temporary directory
WORK_DIR=$(mktemp -d)
ORIGINAL_DIR=$(pwd)
cd "$WORK_DIR"

# Set up minimal CA infrastructure
mkdir -p demoCA/newcerts
touch demoCA/index.txt
echo "01" > demoCA/serial

case $ALGO in
    rsa)
        KEYGEN_OPTIONS="rsa:1024"
        ;;
    ec)
        openssl ecparam -name prime256v1 -out ecparam.pem
        KEYGEN_OPTIONS="ec:ecparam.pem"
        ;;
    *)
        echo "Usage: $0 [rsa|ec]"
        cd "$ORIGINAL_DIR"
        rm -rf "$WORK_DIR"
        exit 1
        ;;
esac

echo "Generating CA..."
# -extensions v3_ca
#   Adds X.509v3 extensions to mark this cert as a CA
openssl req -new -x509 -newkey $KEYGEN_OPTIONS -nodes -days 3650 \
    -subj "/C=US/O=Android/CN=TestCA" -extensions v3_ca \
    -keyout cakey.pem -out cacert.pem 2>/dev/null

echo "Generating user key and CSR..."
openssl req -new -newkey $KEYGEN_OPTIONS -nodes \
    -subj "/C=US/O=Android/CN=TestUser" \
    -keyout userkey.pem -out userkey.req 2>/dev/null

echo "Signing user certificate with CA..."
# -batch
#   Runs in non-interactive mode (auto-confirms prompts)
# -policy policy_anything
#   Allows signing even if the user certificate's Subject (Country/Org/etc)
#   doesn't exactly match the CA's Subject
openssl ca -batch -policy policy_anything -out usercert.pem \
    -in userkey.req -cert cacert.pem -keyfile cakey.pem -days 3650 \
    -notext 2>/dev/null

echo "Exporting to DER format..."
openssl x509 -in cacert.pem -outform der -out cacert.der
openssl pkcs8 -topk8 -inform pem -outform der -in userkey.pem -nocrypt \
    -out userkey.der
openssl x509 -in usercert.pem -outform der -out usercert.der

echo "##### CA certificate hex string #####"
xxd -p cacert.der

echo "##### User private key hex string #####"
xxd -p userkey.der

echo "##### User public key certificate hex string #####"
xxd -p usercert.der

# Move back to the original directory and clean up
cd "$ORIGINAL_DIR"
rm -rf "$WORK_DIR"