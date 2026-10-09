#!/bin/sh
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export LD_LIBRARY_PATH="/run/current-system/sw/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
exec gradle --quiet runGui
