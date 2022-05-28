#!/bin/bash

for n in {1..500}
do
  curl -s "http://localhost:8686/hello?time=120" > /dev/null &
done