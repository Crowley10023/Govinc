#!/bin/bash
while true; do
  java -jar app/build/libs/app.jar
  echo "Application stopped. Restarting in 5 seconds..."
  sleep 5
done
