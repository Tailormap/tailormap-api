#
# Copyright (C) 2021 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
#
FROM eclipse-temurin:11.0.18_10-jre-alpine

ARG TAILORMAP_API_VERSION
ARG TZ="Europe/Amsterdam"

LABEL org.opencontainers.image.authors="support@b3partners.nl" \
      org.opencontainers.image.description="Tailormap API service provides OpenAPI REST interface for Tailormap" \
      org.opencontainers.image.vendor="B3Partners BV" \
      org.opencontainers.image.title="Tailormap API" \
      org.opencontainers.image.url="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.source="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.documentation="https://github.com/B3Partners/tailormap-api/" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version=$TAILORMAP_API_VERSION

# set-up timezone and local user
RUN set -eux; \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apk upgrade --update \
    && apk -U add --no-cache jq \
    && rm -rf /tmp/* /var/cache/apk/* /var/tmp/* \
    && addgroup spring && adduser -G spring -h /home/spring -D spring

USER spring:spring

WORKDIR /home/spring

COPY ./target/tailormap-api-exec.jar tailormap-api.jar

EXPOSE 8080

HEALTHCHECK CMD curl --fail --max-time 5 http://localhost:8080/api/actuator/health | jq -e '.status == "UP"'

# note that Spring Boot logs to the console, there is no logfile
ENTRYPOINT ["java", "-jar", "tailormap-api.jar"]
