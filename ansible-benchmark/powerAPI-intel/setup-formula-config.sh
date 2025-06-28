#!/bin/bash

# Do not stop the containers, we want to keep the postgresql container running
# docker compose down

# Ensure JSON file is valid
echo "Validating the formula config..."
JSON='{
    "verbose": true,
    "stream": true,
    "input": {
        "puller_mongodb": {
            "model": "HWPCReport",
            "type": "mongodb",
            "name": "puller_mongodb",
            "uri": "mongodb://mongodb:27017",
            "db": "db_sensor",
            "collection": "prep"
        }
    },
    "output": {
        "pusher_mongodb": {
            "model": "PowerReport",
            "type": "mongodb",
            "name": "pusher_mongodb",
            "uri": "mongodb://mongodb:27017",
            "db": "db_power",
            "collection": "power_reports"
        }
    },
    "cpu-base-freq": 2600,
    "cpu-error-threshold": 2.0,
    "disable-cpu-formula": false,
    "disable-dram-formula": true,
    "sensor-reports-frequency": 1000,
    "cpu-tdp": 125
}'

# Write the config to file
echo "$JSON" > formula/smartwatts-mongodb-mongodb.json

# Show the content to validate it looks correct
echo "Config file content:"
cat formula/smartwatts-mongodb-mongodb.json

# Start the stack
echo "Starting containers..."
docker-compose up -d

echo "Done. Check logs with: docker compose logs -f formula" 