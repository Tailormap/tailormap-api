FROM eclipse-temurin:11.0.13_8-jre-alpine

ARG TAILORMAP_API_VERSION=0.1
ARG TZ="Europe/Amsterdam"
ARG DEBIAN_FRONTEND="noninteractive"

LABEL org.opencontainers.image.authors="@mprins" \
      description="Tailormap API service" \
      nl.b3p.vendor="B3Partners" \
      nl.b3p.image="Tailormap API" \
      nl.b3p.version=$TAILORMAP_API_VERSION


RUN set -eux;ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /home

COPY ./target/tailormap-api.jar tailormap-api.jar

EXPOSE 8080

HEALTHCHECK CMD curl -f http://localhost:8080/tailormap-api/ || exit 1

ENTRYPOINT ["java", "-jar", "tailormap-api.jar"]

# TODO volume for logfiles
# VOLUME [""]