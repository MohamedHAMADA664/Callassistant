#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
