export KAFKA_BOOTSTRAP_SERVER="PLAINTEXT://dob2-bach-r2n09.bloomberg.com:6667,PLAINTEXT://dob2-bach-r1n09.bloomberg.com:6667,PLAINTEXT://dob2-bach-r4n09.bloomberg.com:6667"
export SCHEMA_REGISTRY_SERVER=https://schema-reg-dob2.ing.spaas-nj-dev01.bce.bloomberg.com
export KAFKA_INPUT_TOPIC=kakfa-stream-demo-input
export KAFKA_OUTPUT_TOPIC=kakfa-stream-demo-output
java -cp target/kafka-streams-examples-4.1.1-standalone.jar io.confluent.examples.streams.WordCountLambdaExample
