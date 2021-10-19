#!/bin/bash

rm -r ./frontend || true
mkdir ./frontend
rm ./updater.jar || true

cp -r ../adoptium-frontend-parent/adoptium-api-v3-frontend/target/quarkus-app/* ./frontend
mv ./frontend/quarkus-run.jar ./frontend/frontend.jar

cp ../adoptium-updater-parent/adoptium-api-v3-updater/target/adoptium-api-v3-updater-*-jar-with-dependencies.jar ./updater.jar

docker build --build-arg type=updater -t adopt-api-v3-updater .
docker build --build-arg type=frontend -t adopt-api-v3-frontend .
