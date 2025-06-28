#!/bin/bash

# Generate a consistent timestamp for the entire run
export TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S")

echo "Starting benchmark with timestamp: $TIMESTAMP"

# Run the Ansible playbook
ansible-playbook -i inventory.yml playbook.yml

echo "Benchmark completed. Results are in ~/Desktop/Results_rest_q_xml_benchmarks/experiment_$TIMESTAMP/" 