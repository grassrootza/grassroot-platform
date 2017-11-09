#! /bin/bash
set -o errexit -o pipefail

TEST8080="$(nc localhost 8080 < /dev/null; echo $?)"
TEST8081="$(nc localhost 8081 < /dev/null; echo $?)"
if [ "$TEST8080" = "0" ]; then
   { echo "There is another container using port 8080, please remove it and deploy again"; } 2> /dev/null
   exit 1;
fi
if [ "$TEST8081" = "0" ]; then
   { echo "There is another container using port 8081, please remove it and deploy again"; } 2> /dev/null
   exit 1;
fi
