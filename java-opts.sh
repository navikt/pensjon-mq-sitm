#!/usr/bin/env sh

if [ ! -z "${OTEL_EXPORTER_OTLP_ENDPOINT}" ]; then
    JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"
    export JAVA_TOOL_OPTIONS
fi

export MQ_USERNAME=$(cat /secrets/srvuser/username)
export MQ_PASSWORD=$(cat /secrets/srvuser/password)
