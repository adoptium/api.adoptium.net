FROM eclipse-temurin:17 as buildwebsite

ARG SIGNING_KEY_PASSWORD
ENV SIGNING_KEY_PASSWORD ${SIGNING_KEY_PASSWORD}

ARG KEYSTORE_PASSWORD
ENV KEYSTORE_PASSWORD ${KEYSTORE_PASSWORD}



RUN mkdir /tmp/build
WORKDIR /tmp/build
COPY . /tmp/build

RUN ./mvnw clean install -Padoptium -DskipTests && \
    cd marketplace/ && \
    ../mvnw clean install -DskipTests && \
    cd adoptium-marketplace-server/adoptium-repo && \
    ../../../mvnw clean package exec:java

FROM nginx
COPY --from=buildwebsite /tmp/build/marketplace/adoptium-marketplace-server/adoptium-repo/repo /usr/share/nginx/html
