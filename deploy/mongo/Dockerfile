FROM mongo:8.0

COPY deploy/mongo/init-mongo.sh /docker-entrypoint-initdb.d/init-mongo.sh
COPY deploy/mongo/run.sh /usr/local/bin/run.sh
COPY deploy/mongo/form-certs.sh /usr/local/bin/form-certs.sh

RUN chmod +x /docker-entrypoint-initdb.d/*.sh /usr/local/bin/run.sh /usr/local/bin/form-certs.sh

ENTRYPOINT ["run.sh"]

CMD ["mongod", "--tlsMode", "requireTLS", "--tlsCertificateKeyFile", "/tmp/mongodb.pem", "--tlsCAFile", "/mongo/tls.crt", "--tlsAllowConnectionsWithoutCertificates", "--wiredTigerCacheSizeGB", "1"]

