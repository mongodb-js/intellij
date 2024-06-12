#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
FAKE_BROWSER_OUTPUT="${SCRIPT_DIR}/FAKE_BROWSER_OUTPUT"

echo $FAKE_BROWSER_OUTPUT

rm -f "$FAKE_BROWSER_OUTPUT"
echo "$@" > "$FAKE_BROWSER_OUTPUT"