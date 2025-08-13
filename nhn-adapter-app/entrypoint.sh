#!/usr/bin/env bash
set -e

if [ "$1" = 'virksert-server' ]; then
  exec java $JAVA_OPTS -jar app.jar ${0} ${@}
fi

exec "$@"
