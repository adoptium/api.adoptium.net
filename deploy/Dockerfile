######################################################
## Build
FROM eclipse-temurin:21-jammy as build

# BUILDS FOR ADOPTIUM, NOT ADOPTOPENJDK
ARG MAVEN_FLAGS="-Padoptium,-adoptopenjdk"
ENV MAVEN_FLAGS=$MAVEN_FLAGS

ARG REPOSITORY="https://github.com/adoptium/api.adoptium.net.git"
ENV REPOSITORY=$REPOSITORY

ARG BRANCH="main"
ENV BRANCH=$BRANCH

RUN     mkdir /tmp/build

WORKDIR /tmp/build

COPY . /tmp/build

RUN     ./mvnw clean install $MAVEN_FLAGS && \
        mkdir -p /api/deployment && \
        cp adoptium-updater-parent/adoptium-api-v3-updater/target/adoptium-api-v3-updater-*-jar-with-dependencies.jar /api/deployment/updater.jar && \
        cp -r adoptium-frontend-parent/adoptium-api-v3-frontend/target/quarkus-app/* /api/deployment/ && \
        mv /api/deployment/quarkus-run.jar /api/deployment/frontend.jar && \
        cp deploy/run.sh /api/deployment/ && \
        chmod +x /api/deployment/run.sh && \
        cd /tmp && \
        rm -rf /tmp/build ~/.m2 && \
        cd /api/ && find


######################################################
## Build Deployment
FROM eclipse-temurin:21-jammy

RUN     apt-get update && apt-get -y install openssl adduser && \
        useradd -ms /bin/bash api && \
        mkdir -p /logs

COPY --chown=api:api --from=build /api/deployment /home/api/deployment

USER api

WORKDIR /home/api/deployment/

ENV JAVA_OPTS=""
ARG type=frontend
ENV typeEnv=$type

CMD ./run.sh ${typeEnv}

