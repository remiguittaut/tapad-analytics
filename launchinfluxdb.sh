mkdir influx-data
docker run --name local-influx -v "$PWD"/influx-data:/var/lib/influxdb2 -p 8086:8086/tcp \
-e 'DOCKER_INFLUXDB_INIT_MODE=setup' -e 'DOCKER_INFLUXDB_INIT_USERNAME=user' -e 'DOCKER_INFLUXDB_INIT_PASSWORD=password123' \
-e 'DOCKER_INFLUXDB_INIT_ORG=tapad' -e 'DOCKER_INFLUXDB_INIT_BUCKET=analytics' -e 'DOCKER_INFLUXDB_INIT_RETENTION=1w' \
-e 'DOCKER_INFLUXDB_INIT_ADMIN_TOKEN=auth-token' -d influxdb:2.5-alpine

