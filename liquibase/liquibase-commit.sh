#!/bin/bash

docker run --rm \
     	--volume $(pwd)/liquibase/changelog:/liquibase/changelog \
     	--network="host" \
     	liquibase/liquibase \
     	--logLevel=warning \
     	--url=jdbc:postgresql://localhost:5432/journal \
     	--defaultsFile=/liquibase/changelog/liquibase.properties \
     	--changeLogFile=changelog.xml \
     	--classpath=/liquibase/changelog \
     	--username=admin \
     	--password=password \
     	update
