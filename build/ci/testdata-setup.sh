#!/usr/bin/env bash
# Copyright (C) 2022 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
set -e

docker compose -f ./build/ci/docker-compose.yml up --pull=always -d --wait

POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
_WAIT=0;

printf "Waiting for databases to be ready...\n"
while :
do
  printf " %d" "$_WAIT"
  if [ "$POSTGIS_HEALTHY" == "healthy" ] && [ "$ORACLE_HEALTHY" == "healthy" ] && [ "$SQLSERVER_HEALTHY" == "healthy" ]; then
    printf "\nDone waiting for databases to be ready\n"
    break
  fi

  sleep 10

  _WAIT=$(($_WAIT+10))
  POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
  ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
  SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)

done

printf "\nPostGIS logs:\n"
docker logs postgis
#docker inspect postgis
printf "\nOracle logs:\n"
docker logs oracle
#docker inspect oracle
printf "\nSQL Server logs:\n"
docker logs sqlserver
#docker inspect sqlserver
