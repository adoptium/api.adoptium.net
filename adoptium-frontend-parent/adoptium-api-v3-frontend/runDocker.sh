#!/bin/bash

sudo docker build -t openjdk-api .
sudo docker run -p 192.168.1.13:3001:3000 -v $(pwd):/home/ubuntu/openjdk-api -it openjdk-api