#!/bin/bash
while true; do
  java -jar app/build/libs/app.jar
  echo "Application stopped. Restarting in 3 seconds..."
  sleep 3
done
