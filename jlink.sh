#!/bin/bash

DEPS=$(jdeps --list-deps --ignore-missing-deps $@ | grep -v '/')

echo "Dependencies:"
echo "$DEPS"

MODULE_LIST=$(echo "$DEPS" | tr -d ' ' | tr '\n' ',' | sed -e 's/,$//')

jlink --compress=2 --no-man-pages --no-header-files --add-modules $MODULE_LIST --output jrt
