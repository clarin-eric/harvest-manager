#!/bin/bash

JAVA="java"
READLINK="readlink"

function ensureSlash(){
  length=${1}-1

  # If the parameter passed to the function does not end with a slash, append
  # one and return the result
  if [ "{$1:length}" != "/" ]; then
    echo ${1}/
  fi
}

export LANG=en_US.UTF-8

# Do not assume the script is invoked from the directory it is located in; get
# the directory the script is located in
thisDir="$(dirname "$(${READLINK} -f "$0")")"
JAR=$thisDir/oai-harvest-manager-${versionNumber}.jar

# Determine the logging mode
if [ "z${LOG_DIR}" != "z" ]; then
  LOG_DIR=$(ensureSlash $LOG_DIR)
else
  LOG_DIR=$thisDir
fi

CLASSPATH="`find $thisDir -type f -name '*.jar' -exec echo -n "{}:" \;`$CLASSPATH"

PROPS="${PROPS} -Dlogdir=${LOG_DIR} -Dhttp.user=Mozilla/5.0"

(
    cd "${thisDir}" && nice ${JAVA} \
        -cp ${CLASSPATH}:${JAR}${PROPS} \
        nl.mpi.oai.harvester.control.Main $*
)
