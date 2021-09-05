FROM liquibase/liquibase:4.4

LABEL MAINTAINER="spongepowered"
LABEL author="spongepowered"

WORKDIR /liquibase/changelog
COPY changelog /liquibase/changelog

CMD ["sh", "-c", "docker-entrypoint.sh --url=${URL} --username=${USERNAME} --password=${PASSWORD} --classpath=/liquibase/changelog --changeLogFile=changelog.xml update"]
