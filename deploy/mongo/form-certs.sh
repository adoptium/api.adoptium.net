#!/bin/bash

if [ -z ${MONGO_CERT_FILE+x} ];
then
  MONGO_CERT_FILE=/mongo/tls.crt
fi

if [ -z ${MONGO_KEY_FILE+x} ];
then
  MONGO_KEY_FILE=/mongo/tls.key
fi

if [ -f "${MONGO_CERT_FILE}" ] && [ -f "${MONGO_KEY_FILE}" ];
then
  echo "MongoDB certificate and key found"
else
  echo "MongoDB certificate and key not found"
  exit 1
fi

cat "$MONGO_KEY_FILE" "$MONGO_CERT_FILE" > /tmp/mongodb.pem
