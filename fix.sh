#!/bin/sh
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
gradlew inspectInstallDistDuplicates
