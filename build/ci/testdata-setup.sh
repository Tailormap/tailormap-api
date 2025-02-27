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
    printf "\nDocker containers are healthy\n"
    break
  fi

  sleep 10

  _WAIT=$(($_WAIT+10))
  POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
  ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
  SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)

done

printf "\n%(%T)T Waiting for Oracle $1 database to report it is ready to use.... "
_WAIT=0;
while :
do
    printf " $_WAIT"
    if $(docker logs oracle | grep -q 'DATABASE IS READY TO USE!'); then
        printf "\n %(%T)TOracle $1 database is ready to use\n\n" -1
        break
    fi
    sleep 10
    _WAIT=$(($_WAIT+10))
done

printf "\nPostGIS logs:\n"
docker logs -t postgis
#docker inspect postgis
printf "\nOracle logs:\n"
docker logs -t oracle
#docker inspect oracle
printf "\nSQL Server logs:\n"
docker logs -t sqlserver
#docker inspect sqlserver
