#!/bin/bash

deploymentType=$1

cd /home/api/deployment/

KEY_FILE=/ssl/tls.key
CERT_FILE=/ssl/tls.crt
MONGO_CERT_FILE=/mongo/tls.crt

if [ -f "${KEY_FILE}" ] && [ -f "${CERT_FILE}" ];
then
  JAVA_OPTS="$JAVA_OPTS -Dquarkus.http.ssl.certificate.file=${CERT_FILE}"
  JAVA_OPTS="$JAVA_OPTS -Dquarkus.http.ssl.certificate.key-file=${KEY_FILE}"
  JAVA_OPTS="$JAVA_OPTS -Dquarkus.http.insecure-requests=disabled"
fi

if [ -f "${MONGO_CERT_FILE}" ];
then
  cp $JAVA_HOME/lib/security/cacerts .
  keytool -import -alias mongodb -storepass changeit -keystore ./cacerts -file "${MONGO_CERT_FILE}" -noprompt
  JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=./cacerts -Djavax.net.ssl.trustStorePassword=changeit"
  export MONGODB_SSL="true"
fi

java $JAVA_OPTS -jar ${deploymentType}.jar
