curl  https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/$1

curl -X POST -H "Content-Type: application/vnd.kafka.avro.v1+json" --data '{"key_schema": "{\"name\":\"user_id\"  ,\"type\": \"string\"   }", "value_schema": "{\"type\": \"record\", \"name\": \"Words\", \"fields\": [{\"name\": \"words\", \"type\": \"string\"}]}", "records": [{"key" : "1" , "value": {"words": "hello kafka streams"}}]}'  "https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/kakfa-stream-demo-input"
