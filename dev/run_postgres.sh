#!/bin/bash

postgres_name="soad-dev-postgres"

if [ ! "$(docker ps -q -f name="${postgres_name}")" ]; then
    if [ "$(docker ps -aq -f status=exited -f name="${postgres_name}")" ]; then
        # cleanup
        echo "postgres exited, removing"
        docker rm "${postgres_name}"
    fi
    echo "starting postgres container"
    # run your container
    docker run -d \
        --name "${postgres_name}" \
    	--volume "$(pwd)"/temp/postgres_data:/var/lib/postgresql/data \
    	-e POSTGRES_USER=admin \
    	-e POSTGRES_PASSWORD=password \
    	-e POSTGRES_DB=default \
    	-p 5432:5432 \
    	postgres:15-alpine \
    	postgres -N 500
fi
