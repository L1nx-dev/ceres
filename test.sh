#!/bin/sh

cd "$(dirname "$0")" || exit 1
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"

verbose=""
filters=""
while [ $# -gt 0 ]; do
  case "$1" in
    -i|--info) verbose="-i" ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *)         filters="$filters --tests *$1*" ;;
  esac
  shift
done

set -f
gradle cleanTest test --console=plain $verbose $filters
status=$?
set +f

echo
echo "report: file://$PWD/build/reports/tests/test/index.html"
exit $status
