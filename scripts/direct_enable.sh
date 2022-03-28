# curl -H 'X-OKAPI-TENANT: diku' -XDELETE "http://localhost:${1}/_/tenant"

curl -XPOST -H 'Content-Type: application/json' -H 'X-OKAPI-TENANT: diku' "http://localhost:${1}/_/tenant" -d '{ "parameters": [{"key": "loadSample", "value": true}, {"key": "loadReference", "value": true}]}'
