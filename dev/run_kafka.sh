#!/bin/bash

prefix="soad-dev"
kafka_name="${prefix}-kafka"
zookeeper_name="${prefix}-zookeeper"


local_ip=$(ipconfig getifaddr en0)

if [ ! "$(docker ps -q -f name="${zookeeper_name}")" ]; then
    if [ "$(docker ps -aq -f status=exited -f name="${zookeeper_name}")" ]; then
        echo "zookeeper exited, removing"
        docker rm "${zookeeper_name}"
    fi
    echo "starting zookeeper"
    docker run -d                              \
        --name "${zookeeper_name}"             \
        -p 2181:2181                           \
        wurstmeister/zookeeper
fi

has_kafka=$(docker ps -q -f name="${kafka_name}")
if [ ! "${has_kafka}" ]; then
    if [ "$(docker ps -aq -f status=exited -f name="${kafka_name}")" ]; then
        # cleanup
        echo "kafka exited, removing"
        docker rm "${kafka_name}"
    fi
    echo "starting kafka container"
    # run your container
    docker run -d                                       \
      --name "${kafka_name}"                            \
      --volume "$(pwd)"/temp/kafka:/kafka               \
      -e KAFKA_ADVERTISED_HOST_NAME=${local_ip}         \
      -e KAFKA_ZOOKEEPER_CONNECT=${local_ip}:2181       \
      -p 9092:9092                                      \
      -e KAFKA_CREATE_TOPICS="group-activity:1:1,artifact-details-update:6:1" \
      wurstmeister/kafka
fi

