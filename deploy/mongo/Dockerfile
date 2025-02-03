FROM mongo:8.0

COPY init-mongo.sh /docker-entrypoint-initdb.d/init-mongo.sh
COPY run.sh /usr/local/bin/run.sh
COPY form-certs.sh /usr/local/bin/form-certs.sh

RUN chmod +x /docker-entrypoint-initdb.d/*.sh /usr/local/bin/run.sh /usr/local/bin/form-certs.sh

ENTRYPOINT ["run.sh"]

CMD ["mongod", "--tlsMode", "requireTLS", "--tlsCertificateKeyFile", "/tmp/mongodb.pem", "--tlsCAFile", "/mongo/tls.crt", "--tlsAllowConnectionsWithoutCertificates"]

