FROM eclipse-temurin:11.0.13_8-jre-alpine

ARG TAILORMAP_API_VERSION=0.1
ARG TZ="Europe/Amsterdam"
ARG DEBIAN_FRONTEND="noninteractive"

LABEL org.opencontainers.image.authors="@mprins" \
      description="Tailormap API service" \
      nl.b3p.vendor="B3Partners" \
      nl.b3p.image="Tailormap API" \
      nl.b3p.version=$TAILORMAP_API_VERSION


RUN set -eux;ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && addgroup -S spring && adduser -S spring -G spring

USER spring:spring

WORKDIR /home

COPY ./target/tailormap-api.jar tailormap-api.jar

EXPOSE 8080
# see https://docs.spring.io/spring-boot/docs/2.5.6/actuator-api/htmlsingle/#health
HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD wget --spider --header 'Accept: application/json' http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "tailormap-api.jar"]

# TODO volume for logfiles
# VOLUME [""]