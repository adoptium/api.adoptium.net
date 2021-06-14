FROM adoptopenjdk/openjdk11 as build

RUN mkdir /tmp/build

WORKDIR /tmp/build

COPY . /tmp/build

RUN ./mvnw package

FROM adoptopenjdk/openjdk11

RUN mkdir -p /deployments

COPY --from=build /tmp/build/adoptopenjdk-frontend-parent/adoptopenjdk-api-v3-frontend/target/quarkus-app/ /deployments/app/
COPY --from=build /tmp/build/adoptopenjdk-updater-parent/adoptopenjdk-api-v3-updater/target/adoptopenjdk-api-v3-updater-*-jar-with-dependencies.jar /deployments/adoptopenjdk-api-v3-updater-runner.jar
RUN mv /deployments/app/quarkus-run.jar /deployments/app/adoptopenjdk-api-v3-frontend.jar

CMD ["java", "-jar", "/deployments/app/adoptopenjdk-api-v3-frontend.jar"]
