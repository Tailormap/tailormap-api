#!/usr/bin/env bash
# Copyright (C) 2022 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
set -euo pipefail

export SOLR_OPTS=""
export COMPOSE_PARALLEL_LIMIT=-1

docker compose -f ./build/ci/docker-compose.yml up --pull=always --quiet-pull -d

POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
POSTGRES_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgres)
SOLR_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" solr)
_WAIT=0;

printf "%(%T)T Waiting for databases to be ready..."
while :
do
  printf " %d" "$_WAIT"
  if [ "$POSTGIS_HEALTHY" == "healthy" ] &&
      [ "$ORACLE_HEALTHY" == "healthy" ] &&
      [ "$SQLSERVER_HEALTHY" == "healthy" ] &&
      [ "$POSTGRES_HEALTHY" == "healthy" ] &&
      [ "$SOLR_HEALTHY" == "healthy" ]; then
    printf "\n%(%T)T Docker containers are healthy\n" -1
    break
  fi

  sleep 10

  _WAIT=$((_WAIT+10))
  POSTGIS_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgis)
  ORACLE_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" oracle)
  SQLSERVER_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" sqlserver)
  POSTGRES_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" postgres)
  SOLR_HEALTHY=$(docker inspect --format="{{.State.Health.Status}}" solr)
done

printf "\n%(%T)T Waiting for Oracle database to report it is ready to use... "
_WAIT=0;
while :
do
    printf " %d" "$_WAIT"
    if (docker logs oracle | grep -q 'DATABASE IS READY TO USE!'); then
        printf "\n %(%T)T Oracle database is ready to use\n\n" -1
        break
    fi
    sleep 10
    _WAIT=$((_WAIT+10))
done

# backfill Prometheus test data
printf "%(%T)T Backfilling Prometheus test data...\n"
$(dirname "$0")/prometheus-backfill.sh > /tmp/prometheus-backfill.log
docker compose -f ./build/ci/docker-compose.yml cp /tmp/prometheus-backfill.log prometheus:/prometheus/prometheus-backfill.log
docker compose -f ./build/ci/docker-compose.yml exec -T prometheus promtool tsdb create-blocks-from openmetrics /prometheus/prometheus-backfill.log
printf "\n%(%T)T Added backfill data to Prometheus\n"

# check that the data is present in Prometheus
printf "\n%(%T)T Checking that backfill data is published in Prometheus..."
BACKFILL_DATA_PRESENT=false
BACKFILL_TIMEOUT=120
BACKFILL_WAIT=0
while [ "$BACKFILL_DATA_PRESENT" = false ] && [ "$BACKFILL_WAIT" -lt "$BACKFILL_TIMEOUT" ]; do
    printf " %d" "$BACKFILL_WAIT"
    if curl -sG "http://localhost:9090/api/v1/label/__name__/values" \
        | jq -e '.status == "success" and (.data | index("tailormap_app_request_total")) and (.data | index("tailormap_applayer_switched_on_total"))' > /dev/null; then
        BACKFILL_DATA_PRESENT=true
        printf "\n%(%T)T Backfilled data is available in Prometheus\n"
        break
    fi
    sleep 10
    BACKFILL_WAIT=$((BACKFILL_WAIT+10))
done

if [ "$BACKFILL_DATA_PRESENT" = false ]; then
    printf "%(%T)T Timed out after %d seconds waiting for backfill data in Prometheus\n" -1 "$BACKFILL_TIMEOUT"
    exit 1
fi