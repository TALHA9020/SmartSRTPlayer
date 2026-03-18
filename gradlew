#!/bin/sh

# Attempt to set APP_HOME
app_path=$0
while
APP_HOME=${app_path%"${app_path##*/}"}
[ -h "$app_path" ]
do
ls=$( ls -ld "$app_path" )
link=${ls#*' -> '}
case $link in
/*) app_path=$link ;;
*) app_path=$APP_HOME$link ;;
esac
done
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
else
    JAVACMD=java
fi

set -- \
"-Dorg.gradle.appname=$APP_BASE_NAME" \
-classpath "$CLASSPATH" \
org.gradle.wrapper.GradleWrapperMain \
"$@"

exec "$JAVACMD" "$@"
