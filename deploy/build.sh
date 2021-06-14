#!/bin/bash

rm -r ./frontend || true
mkdir ./frontend
rm ./updater.jar || true

cp -r ../adoptopenjdk-frontend-parent/adoptopenjdk-api-v3-frontend/target/quarkus-app/* ./frontend
mv ./frontend/quarkus-run.jar ./frontend/frontend.jar

cp ../adoptopenjdk-updater-parent/adoptopenjdk-api-v3-updater/target/adoptopenjdk-api-v3-updater-*-jar-with-dependencies.jar ./updater.jar

docker build --build-arg type=updater -t adopt-api-v3-updater .
docker build --build-arg type=frontend -t adopt-api-v3-frontend .
