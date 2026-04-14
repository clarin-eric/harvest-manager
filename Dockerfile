### Note: this is using multistage build, see https://docs.docker.com/develop/develop-images/multistage-build/
### alpine: 3.23.2
FROM registry.gitlab.com/clarin-eric/docker-alpine-supervisor-java-base:openjdk21_jre-1.3.0 AS java-base

RUN apk --no-cache add \
                       bzip2=1.0.8-r6 \
                       rsync=3.4.1-r1 \
                       postgresql16-client=16.13-r0

# build stage
FROM java-base AS build

ENV HARVEST_VIEWER_VERSION=master
ENV HARVEST_MANAGER_VERSION=clarin_master
ARG HARVEST_VIEWER_REPO=https://github.com/clarin-eric/oai-harvest-viewer.git
#ARG HARVEST_MANAGER_REPO=https://github.com/clarin-eric/oai-harvest-manager.git
ARG HARVEST_MANAGER_REPO=https://github.com/CLARIAH/oai-harvest-manager.git

RUN apk --no-cache add \
                       git=2.52.0-r0 \
                       maven=3.9.11-r0

# install OAI Harvester git
RUN mkdir /tmp/oai && \
    cd /tmp && \
    git clone "$HARVEST_MANAGER_REPO" harvest-manager && \
    cd harvest-manager && \
    git checkout "$HARVEST_MANAGER_VERSION" && \
    mvn -DskipTests=true clean package

WORKDIR /tmp/oai
RUN tar -xzf /tmp/harvest-manager/target/harvest-manager-2.0*.tar.gz

### Package stage

FROM java-base

# app workdir
WORKDIR /app/workdir
WORKDIR /app/oai

COPY --from=build /tmp/oai /app/oai

ENTRYPOINT ["/app/oai/run-harvester.sh"]

# cleanup
RUN rm -rf /var/lib/apt/lists/* && \
    rm -rf /tmp/*
