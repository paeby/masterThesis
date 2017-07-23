#!/bin/sh

JAVA=java
CLASSPATH=/opt/reporting/lib
CONF=/opt/reporting/application.conf

# Set environment variables for service configuration
export POSTGRESQL_URL="jdbc:postgresql://$POSTGRESQL_SERVER:5432/"
export POSTGRESQL_DATABASE="$ENVIRONMENT_NAME"
export ENV=$ENVIRONMENT_NAME

run () {
    exec $JAVA -cp "$CLASSPATH/*" \
        -Dconfig.file=$CONF \
        $*
}

case "$1" in
    "init")
        echo "Initialize reporting service"
        run com.bestmile.task.ResetDatabase
        ;;
    *)
        echo "Start reporting service"
        run com.bestmile.WebServer
        ;;
esac
