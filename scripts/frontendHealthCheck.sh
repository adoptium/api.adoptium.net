#!/bin/bash

# Temp monitoring for native memory leak from platform
if [ -f "/tmp/frontend.log" ]; then
  cat /tmp/frontend.log | grep OutOfMemoryError > /dev/null
  if [ $? -eq 0 ];
  then
    exit 1
  fi
fi

uptime=$(jcmd 1 VM.uptime | egrep -o "^[0-9]+" | tail -n 1)
if [ $uptime -gt 1800 ];
then
    exit 1
fi

returnCode=$(curl -o /dev/null -s -w "%{http_code}\n" "localhost:8080/v3/assets/feature_releases/8/ga?heap_size=normal&image_type=jdk&page=0&page_size=10&project=jdk&sort_order=DESC&vendor=adoptopenjdk")
if [ $returnCode -ne 200 ];
then
    exit 1
fi
exit 0
