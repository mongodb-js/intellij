#!/bin/sh

./gradlew --quiet --console=plain :packages:jetbrains-plugin:runIdeForUiTests > idea.log &
IDEA_PID=$!

waitForUrl() {
    echo "Testing $1"
    timeout -s TERM 90 bash -c \
    'while [[ "$(curl -s -o /dev/null -L -w ''%{http_code}'' ${0})" != "200" ]];\
    do echo "Waiting for ${0}" && sleep 2;\
    done' $1
    echo "OK!"
}

waitForUrl "http://localhost:8082"

./gradlew --quiet --console=plain :packages:jetbrains-plugin:cleanTest :packages:jetbrains-plugin:uiTest

RESULT=$?

mv idea.log packages/jetbrains-plugin/build/reports/idea.log
kill $IDEA_PID
exit $RESULT