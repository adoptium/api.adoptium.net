#!/bin/bash

rm -r assets || true

mkdir assets
mkdir assets/repo
mkdir assets/keys

cp -r ../exampleRepositories/realData/* ./assets/repo/
cp ../exampleRepositories/keys/adoptium.pub ./assets/keys
cp ../adoptium-marketplace-server/adoptium-marketplace-updater-parent/adoptium-marketplace-updater/target/adoptium-marketplace-updater-1.0.0-SNAPSHOT-jar-with-dependencies.jar ./assets/
cp ../adoptium-marketplace-server/adoptium-marketplace-frontend-parent/adoptium-marketplace-frontend/target/adoptium-marketplace-frontend-1.0.0-SNAPSHOT-jar-with-dependencies.jar ./assets/

docker-compose rm
docker-compose build
docker-compose up
