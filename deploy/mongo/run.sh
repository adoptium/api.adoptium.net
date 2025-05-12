#!/bin/bash

echo "Forming Certs"
form-certs.sh

ls -alh /data/db

echo "Running MongoDB"
docker-entrypoint.sh "$@"

