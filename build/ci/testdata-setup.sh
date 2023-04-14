#!/usr/bin/env bash
# Copyright (C) 2022 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
set -e

docker compose -f ./build/ci/docker-compose.yml up --pull -d --wait

POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
_WAIT=0;

while :
do
  printf " $_WAIT"
  if [ "$POSTGIS_HEALTHY" == "healthy" ] && [ "$ORACLE_HEALTHY" == "healthy" ] && [ "$SQLSERVER_HEALTHY" == "healthy" ]; then
    printf " Done waiting for databases to be ready\n"
    break
  fi

  sleep 10

  _WAIT=$(($_WAIT+10))
  POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
  ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
  SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)

done

docker logs postgis
docker inspect postgis
docker logs oracle
docker inspect oracle
docker logs sqlserver
docker inspect sqlserver
