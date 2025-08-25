#!/usr/bin/env bash
# Copyright (C) 2025 B3Partners B.V.
#
# SPDX-License-Identifier: MIT
set -euo pipefail

# Calculate the previous hour
current_hour=$(date +%H)
hour=$((10#$current_hour - 1))
if [ "$hour" -lt 0 ]; then
  hour=23
fi
dateHour="$(date +%Y-%m-%d) $(printf "%02d" $hour)"

# Function to generate metrics
generate_metrics() {
  local metric_name=$1
  local labels=$2

  for i in $(seq 0 59); do
    timestamp=$(date -d "${dateHour}:$(printf "%02d" "$i"):00" +%s)
    value=$((RANDOM % 100 + 1))
    echo "${metric_name}{${labels}} $value $timestamp"
  done
}

# Output static headers
cat << EOF
# HELP tailormap_app_request_total Generated test data for tailormap_app_request_total
# TYPE tailormap_app_request_total counter
EOF

# Generate app request metrics
generate_metrics "tailormap_app_request_total" 'appId="1",appName="default",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_app_request_total" 'appId="5",appName="austria",appType="app",application="tailormap-api",hostname="localhost"'

cat << EOF
# HELP tailormap_applayer_switched_on_total Generated test data for tailormap_applayer_switched_on_total
# TYPE tailormap_applayer_switched_on_total counter
EOF

# Generate app layer switched-on metrics
generate_metrics "tailormap_applayer_switched_on_total" 'appId="1",appLayerId="lyr:snapshot-geoserver:postgis:begroeidterreindeel",appName="default",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="1",appLayerId="lyr:snapshot-geoserver:postgis:kadastraal_perceel",appName="default",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="1",appLayerId="lyr:snapshot-geoserver:postgis:bak",appName="default",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="1",appLayerId="lyr:openbasiskaart:osm",appName="default",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="5",appLayerId="lyr:at-basemap:geolandbasemap",appName="austria",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="5",appLayerId="lyr:at-basemap:orthofoto_2",appName="austria",appType="app",application="tailormap-api",hostname="localhost"'
generate_metrics "tailormap_applayer_switched_on_total" 'appId="5",appLayerId="lyr:osm:xyz",appName="austria",appType="app",application="tailormap-api",hostname="localhost"'


echo "# EOF"