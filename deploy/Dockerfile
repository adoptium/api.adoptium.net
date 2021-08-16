FROM eclipse-temurin:11

ARG ECOSYSTEM=adoptium
ENV ECOSYSTEM=$ECOSYSTEM

ARG REPOSITORY="https://github.com/adoptium/api.adoptium.net.git"
ENV REPOSITORY=$REPOSITORY

ARG BRANCH="main"
ENV BRANCH=$BRANCH

RUN     useradd -ms /bin/bash api && \
        mkdir -p /home/api/deployment/ && \
        mkdir -p /logs && \
        mkdir -p /home/api/deployment/lib && \
        mkdir -p /home/api/build && \
        mkdir /tmp/build && \
        chown -R api: /home/api/

WORKDIR /tmp/build

COPY . /tmp/build

RUN chown -R api: /tmp/build

USER api

RUN     ./mvnw clean install -Decosystem=${ECOSYSTEM} -P${ECOSYSTEM} && \
        cp adoptopenjdk-updater-parent/adoptopenjdk-api-v3-updater/target/adoptopenjdk-api-v3-updater-*-jar-with-dependencies.jar /home/api/deployment/updater.jar && \
        cp -r adoptopenjdk-frontend-parent/adoptopenjdk-api-v3-frontend/target/quarkus-app/* /home/api/deployment/ && \
        mv /home/api/deployment/quarkus-run.jar /home/api/deployment/frontend.jar && \
        cp deploy/run.sh /home/api/deployment/ && \
        chmod +x /home/api/deployment/run.sh && \
        cd /tmp && \
        rm -rf /tmp/build ~/.m2 && \
        cd /home/api/ && find

WORKDIR /home/api/deployment/

ENV JAVA_OPTS=""
ARG type=frontend
ENV typeEnv=$type

CMD cd /home/api/deployment/ && ./run.sh ${typeEnv}

