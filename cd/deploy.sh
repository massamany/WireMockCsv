#!/usr/bin/env bash
if [ "$TRAVIS_BRANCH" = 'release' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    mvn clean install deploy -P sign,build-extras --settings cd/mvnsettings.xml
fi
