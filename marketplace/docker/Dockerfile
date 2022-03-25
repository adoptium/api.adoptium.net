FROM eclipse-temurin:17

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

RUN     ./mvnw clean install -Padoptium,-adoptopenjdk && \
        cd marketplace && \
        ../mvnw clean install && \
        cp adoptium-marketplace-server/adoptium-marketplace-updater-parent/adoptium-marketplace-updater/target/adoptium-marketplace-updater-*-jar-with-dependencies.jar /home/api/deployment/updater.jar && \
        cp adoptium-marketplace-server/adoptium-marketplace-frontend-parent/adoptium-marketplace-frontend/target/adoptium-marketplace-frontend-*-jar-with-dependencies.jar /home/api/deployment/frontend.jar && \
        cp docker/run.sh /home/api/deployment/ && \
        chmod +x /home/api/deployment/run.sh && \
        cd /tmp && \
        rm -rf /tmp/build ~/.m2 && \
        cd /home/api/ && find

WORKDIR /home/api/deployment/

ENV JAVA_OPTS=""
ARG type=frontend
ENV typeEnv=$type

CMD cd /home/api/deployment/ && ./run.sh ${typeEnv}

