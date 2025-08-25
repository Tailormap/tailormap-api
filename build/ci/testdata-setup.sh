#!/usr/bin/env bash
# Copyright (C) 2022 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
set -e

export SOLR_OPTS=""

docker compose -f ./build/ci/docker-compose.yml up --pull=always -d

POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
POSTGRES_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgres)
SOLR_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" solr)
_WAIT=0;

printf "Waiting for databases to be ready...\n"
while :
do
  printf " %d" "$_WAIT"
  if [ "$POSTGIS_HEALTHY" == "healthy" ] &&
      [ "$ORACLE_HEALTHY" == "healthy" ] &&
      [ "$SQLSERVER_HEALTHY" == "healthy" ] &&
      [ "$POSTGRES_HEALTHY" == "healthy" ] &&
      [ "$SOLR_HEALTHY" == "healthy" ]; then
    printf "\nDocker containers are healthy\n"
    break
  fi

  sleep 10

  _WAIT=$(($_WAIT+10))
  POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
  ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
  SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
  POSTGRES_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgres)
  SOLR_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" solr)
done

printf "\n%(%T)T Waiting for Oracle database to report it is ready to use.... "
_WAIT=0;
while :
do
    printf " $_WAIT"
    if $(docker logs oracle | grep -q 'DATABASE IS READY TO USE!'); then
        printf "\n %(%T)TOracle database is ready to use\n\n" -1
        break
    fi
    sleep 10
    _WAIT=$(($_WAIT+10))
done

#printf "\nPostGIS logs:\n"
#docker logs -t postgis
#
#printf "\nOracle logs:\n"
#docker logs -t oracle
#
#printf "\nSQL Server logs:\n"
#docker logs -t sqlserver

# backfill Prometheus test data
echo "Backfilling Prometheus test data..."
$(dirname "$0")/prometheus-backfill.sh > /tmp/prometheus-backfill.log
docker compose -f ./build/ci/docker-compose.yml cp /tmp/prometheus-backfill.log prometheus:/prometheus/prometheus-backfill.log
docker compose -f ./build/ci/docker-compose.yml exec -T prometheus promtool tsdb create-blocks-from openmetrics /prometheus/prometheus-backfill.log
