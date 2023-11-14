FROM liquibase/liquibase:4.25

LABEL MAINTAINER="spongepowered"
LABEL author="spongepowered"

WORKDIR /liquibase/changelog
COPY changelog /liquibase/changelog
ENV URL 'jdbc:postgresql://localhost:5432/default';
ENV USERNAME 'admin';
ENV PASSWORD 'password';
CMD ["sh", "-c", "docker-entrypoint.sh --url=${URL} --username=${USERNAME} --password=${PASSWORD} --classpath=/liquibase/changelog --changeLogFile=changelog.xml update"]
