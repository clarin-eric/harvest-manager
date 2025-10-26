#!/bin/bash 

#set -x

JAVA_TARGET_DIR="$(cd "$(dirname $0)" && pwd)/target"
JAVA_IMAGE=registry.gitlab.com/clarin-eric/docker-alpine-supervisor-java-base:openjdk11-2.2.0
CONTAINER_CONF_FILE_PATH='/tmp/harvester.conf'
JAVA_CMD="java -Xms10G -Xmx10G -Dlogdir=/logdir -jar /target/harvest-manager*.jar workdir=/workdir ${CONTAINER_CONF_FILE_PATH}"
WORKDIR="${WORKDIR:-$(pwd)/run/workdir}"
LOGDIR="${LOGDIR:-$(pwd)/run/log}"
CONFIG_FILE="$1"

if ! [ "${CONFIG_FILE}" ]; then
	echo "Usage: $0 <config file>" >&2
	exit 1
fi

if ! [ -e "${CONFIG_FILE}" ]; then
	echo "File does not exist: $1" >&2
	exit 1
fi

if ! [ -d "${JAVA_TARGET_DIR}" ]; then
	echo "Target dir ${JAVA_TARGET_DIR} does not exist. Run build.sh first?" >&2
	exit 1
fi

echo "Work dir: ${WORKDIR}"
echo "Log dir: ${LOGDIR}"

docker run -it --rm \
	--name "oai-harvest-test-${RANDOM}" \
	--volume "${JAVA_TARGET_DIR}:/target" \
	--volume "$(realpath "$CONFIG_FILE"):${CONTAINER_CONF_FILE_PATH}" \
	--volume "${WORKDIR}:/workdir" \
	--volume "${LOGDIR}:/logdir" \
	--entrypoint bash \
	"${JAVA_IMAGE}" -c "cd /workdir && ${JAVA_CMD}"
