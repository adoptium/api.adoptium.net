#!/bin/bash

echo "Generating a weak test key do not use this for anything other than tests"

export GNUPGHOME="$(mktemp -d)"

gpg --verbose --batch --gen-key ./keyspec
gpg --export -a --no-default-keyring > public.key
gpg --export-secret-keys -a --no-default-keyring > private.key
