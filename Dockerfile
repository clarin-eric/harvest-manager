FROM registry.gitlab.com/clarin-eric/docker-alpine-supervisor-java-base:openjdk11-2.2.0 as build

RUN apk --no-cache add maven=3.6.1-r0

# install OAI Harvester
    
COPY . /tmp
RUN cd /tmp && \
    mvn clean package

WORKDIR /tmp/oai
RUN tar -xzf /tmp/target/oai-harvest-manager-1.2*.tar.gz

### Package stage

FROM registry.gitlab.com/clarin-eric/docker-alpine-supervisor-java-base:openjdk11-1.2.12

# app workdir
RUN mkdir -p /app/workdir &&\
    mkdir -p /app/oai
WORKDIR /app/oai

COPY --from=build /tmp/oai /app/oai

ENTRYPOINT ["/app/oai/run-harvester.sh"]

# cleanup
RUN rm -rf /var/lib/apt/lists/* && \
    rm -rf /tmp/*
