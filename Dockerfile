FROM eclipse-temurin:20 as build

RUN mkdir /tmp/build

WORKDIR /tmp/build

COPY . /tmp/build

RUN ./mvnw clean install -Padoptium

FROM eclipse-temurin:20

RUN mkdir -p /deployments

COPY --from=build /tmp/build/adoptium-frontend-parent/adoptium-api-v3-frontend/target/quarkus-app/ /deployments/app/
COPY --from=build /tmp/build/adoptium-updater-parent/adoptium-api-v3-updater/target/adoptium-api-v3-updater-*-jar-with-dependencies.jar /deployments/adoptium-api-v3-updater-runner.jar
RUN mv /deployments/app/quarkus-run.jar /deployments/app/adoptium-api-v3-frontend.jar

CMD ["java", "-jar", "/deployments/app/adoptium-api-v3-frontend.jar"]
