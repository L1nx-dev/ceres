#!/bin/sh
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
gradle installDist -q || exit 1
exec build/install/ceres/bin/ceres "$@"
