/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.examples.streams;

import io.confluent.examples.streams.avro.WordCount;
import io.confluent.examples.streams.avro.Words;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Demonstrates, using the high-level KStream DSL, how to implement the WordCount program that
 * computes a simple word occurrence histogram from an input text. This example uses lambda
 * expressions and thus works with Java 8+ only.
 * <p>
 * In this example, the input stream reads from a topic named "streams-plaintext-input", where the values of
 * messages represent lines of text; and the histogram output is written to topic
 * "streams-wordcount-output", where each record is an updated count of a single word, i.e. {@code word (String) -> currentCount (Long)}.
 * <p>
 * Note: Before running this example you must 1) create the source topic (e.g. via {@code kafka-topics --create ...}),
 * then 2) start this example and 3) write some data to the source topic (e.g. via {@code kafka-console-producer}).
 * Otherwise you won't see any data arriving in the output topic.
 * <p>
 * <br>
 * HOW TO RUN THIS EXAMPLE
 * <p>
 * 1) Start Zookeeper and Kafka. Please refer to <a href='http://docs.confluent.io/current/quickstart.html#quickstart'>QuickStart</a>.
 * <p>
 * 2) Create the input and output topics used by this example.
 * <pre>
 * {@code
 * $ bin/kafka-topics --create --topic kakfa-stream-demo \
 *                    --zookeeper 100.83.16.148:2181 --partitions 1 --replication-factor 1
 * $ bin/kafka-topics --create --topic kakfa-stream-demo-output \
 *                    --zookeeper 100.83.16.148:2181 --partitions 1 --replication-factor 1
 * }</pre>
 * Note: The above commands are for the Confluent Platform. For Apache Kafka it should be {@code bin/kafka-topics.sh ...}.
 * <p>
 * 3) Start this example application either in your IDE or on the command line.
 * <p>
 * If via the command line please refer to <a href='https://github.com/confluentinc/kafka-streams-examples#packaging-and-running'>Packaging</a>.
 * Once packaged you can then run:
 * <pre>
 * {@code
 * $ export KAFKA_BOOSTRAP_SERVER=PLAINTEXT://dob2-bach-r2n09.bloomberg.com:6667
 * $ export SCHEMA_REGISTRY_SERVER=https://schema-reg-dob2.ing.spaas-nj-dev01.bce.bloomberg.com
 * $ export KAFKA_INPUT_TOPIC=kakfa-stream-demo
 * $ export KAFKA_OUTPUT_TOPIC=kakfa-stream-demo-output
 * $ java -cp target/kafka-streams-examples-4.1.1-standalone.jar io.confluent.examples.streams.WordCountLambdaExample
 * }
 * </pre>
 * 4) Write some input data to the source topic "streams-plaintext-input" (e.g. via {@code kafka-console-producer}).
 * The already running example application (step 3) will automatically process this input data and write the
 * results to the output topic "streams-wordcount-output".
 * <pre>
 * {@code
 * # Use reset proxy to write to input topic. You can then enter input data by writing some line of text, followed by ENTER:
 * $curl  https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/kakfa-stream-demo
 * $curl -X POST -H "Content-Type: application/vnd.kafka.avro.v1+json" --data '{"value_schema": "{\"type\": \"record\", \"name\": \"Words\", \"fields\": [{\"name\": \"words\", \"type\": \"string\"}]}", "records": [{"value": {"words": "hello kafka streams"}}]}' "https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/kakfa-stream-demo"
 * $curl -X POST -H "Content-Type: application/vnd.kafka.avro.v1+json" --data '{"value_schema": "{\"type\": \"record\", \"name\": \"Words\", \"fields\": [{\"name\": \"words\", \"type\": \"string\"}]}", "records": [{"value": {"words": "all streams lead to kafka"}}]}' "https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/kakfa-stream-demo"
 * $curl -X POST -H "Content-Type: application/vnd.kafka.avro.v1+json" --data '{"value_schema": "{\"type\": \"record\", \"name\": \"Words\", \"fields\": [{\"name\": \"words\", \"type\": \"string\"}]}", "records": [{"value": {"words": "join kafka summit"}}]}' "https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/topics/kakfa-stream-demo"
 * #   hello kafka streams
 * #   all streams lead to kafka
 * #   join kafka summit
 * #
 * # Every line you enter will become the value of a single Kafka message.
 * }</pre>
 * 5) Inspect the resulting data in the output topic, e.g. via {@code kafka-console-consumer}.
 * <pre>
 * {@code
 * $ curl -X POST -H "Content-Type: application/vnd.kafka.v1+json" --data '{"name": "my_consumer_instance", "format": "avro", "auto.offset.reset": "smallest"}' https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/consumers/my_avro_consumer
 * $ curl -X GET -H "Accept: application/vnd.kafka.avro.v1+json" https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/consumers/my_avro_consumer/instances/my_consumer_instance/topics/kakfa-stream-demo
 * $ curl -X POST -H "Content-Type: application/vnd.kafka.v1+json" --data '{"name": "my_consumer_instance-output", "format": "avro", "auto.offset.reset": "smallest"}' https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/consumers/my_avro_consumer
 * $ curl -X GET -H "Accept: application/vnd.kafka.avro.v1+json" https://rest-proxy-dob2.ing.spaas-nj-dev01.bce.bloomberg.com/consumers/my_avro_consumer/instances/my_consumer_instance-output/topics/kakfa-stream-demo-output
 * }</pre>
 * You should see output data similar to below. Please note that the exact output
 * sequence will depend on how fast you type the above sentences. If you type them
 * slowly, you are likely to get each count update, e.g., kafka 1, kafka 2, kafka 3.
 * If you type them quickly, you are likely to get fewer count updates, e.g., just kafka 3.
 * This is because the commit interval is set to 10 seconds. Anything typed within
 * that interval will be compacted in memory.
 * <pre>
 * {@code
 * hello    1
 * kafka    1
 * streams  1
 * all      1
 * streams  2
 * lead     1
 * to       1
 * join     1
 * kafka    3
 * summit   1
 * }</pre>
 * 6) Once you're done with your experiments, you can stop this example via {@code Ctrl-C}. If needed,
 * also stop the Kafka broker ({@code Ctrl-C}), and only then stop the ZooKeeper instance (`{@code Ctrl-C}).
 */
public class WordCountLambdaExample {

  private static final String kafka_bootstrap_server = "KAFKA_BOOTSTRAP_SERVER";
  private static final String schema_registry_server = "SCHEMA_REGISTRY_SERVER";
  private static final String kafka_input_topic = "KAFKA_INPUT_TOPIC";
  private static final String kafka_output_topic = "KAFKA_OUTPUT_TOPIC";

  public static void main(final String[] args) throws Exception {
    final String bootstrapServers = Optional.ofNullable(System.getenv(kafka_bootstrap_server)).orElse("localhost:9092");
    final String schemaRegistryUrl = Optional.ofNullable(System.getenv(schema_registry_server)).orElse("localhost:8081");
    final Properties streamsConfiguration = new Properties();
    // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
    // against which the application is run.
    streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-lambda-example");
    streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "wordcount-lambda-example-client");
    // Where to find Kafka broker(s).
    streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    streamsConfiguration.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    // Specify default (de)serializers for record keys and for record values.
    streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Long().getClass().getName());
    // Records should be flushed every 10 seconds. This is less than the default
    // in order to keep this example interactive.
    streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);
    // For illustrative purposes we disable record caches
    streamsConfiguration.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

    // Set up serializers and deserializers, which we will use for overriding the default serdes
    // specified above.
    final Serde<String> stringSerde = Serdes.String();
    final SpecificAvroSerde<Words> wordsSerde = createSerde(schemaRegistryUrl);
    final SpecificAvroSerde<WordCount> wordCountSerde = createSerde(schemaRegistryUrl);

    String input_topic = Optional.ofNullable(System.getenv(kafka_input_topic)).orElse("streams-plaintext-input");

    // In the subsequent lines we define the processing topology of the Streams application.
    final StreamsBuilder builder = new StreamsBuilder();

    // Construct a `KStream` from the input topic "kafka-streams-demo-input", where message values
    // represent lines of text (for the sake of this example, we ignore whatever may be stored
    // in the message keys).

    final KStream<String, Words> textLines = builder.stream(input_topic, Consumed.with(stringSerde, wordsSerde));

    final Pattern pattern = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS);

    final KTable<String, Long> wordCounts = textLines
      // Split each text line, by whitespace, into words.  The text lines are the record
      // values, i.e. we can ignore whatever data is in the record keys and thus invoke
      // `flatMapValues()` instead of the more generic `flatMap()`.
      .flatMapValues(value -> Arrays.asList(pattern.split(value.getWords().toLowerCase())))
      // Count the occurrences of each word (record key).
      //
      // This will change the stream type from `KStream<String, Words>` to `KTable<String, Long>`
      // (word -> count).  In the `count` operation we must provide a name for the resulting KTable,
      // which will be used to name e.g. its associated state store and changelog topic.
      //
      // Note: no need to specify explicit serdes because the resulting key and value types match our default serde settings
      .groupBy((key, word) -> word)
      .count();

    // Write the `KTable<String, Long>` to the output topic.
    wordCounts.toStream().map((key, value) -> new KeyValue<>(key,  new WordCount(key, value)))
            .to(Optional.ofNullable(System.getenv(kafka_output_topic))
            .orElse("streams-wordcount-output"), Produced.with(stringSerde, wordCountSerde));

    // Now that we have finished the definition of the processing topology we can actually run
    // it via `start()`.  The Streams application as a whole can be launched just like any
    // normal Java application that has a `main()` method.
    final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
    // Always (and unconditionally) clean local state prior to starting the processing topology.
    // We opt for this unconditional call here because this will make it easier for you to play around with the example
    // when resetting the application for doing a re-run (via the Application Reset Tool,
    // http://docs.confluent.io/current/streams/developer-guide.html#application-reset-tool).
    //
    // The drawback of cleaning up local state prior is that your app must rebuilt its local state from scratch, which
    // will take time and will require reading all the state-relevant data from the Kafka cluster over the network.
    // Thus in a production scenario you typically do not want to clean up always as we do here but rather only when it
    // is truly needed, i.e., only under certain conditions (e.g., the presence of a command line flag for your app).
    // See `ApplicationResetExample.java` for a production-like example.
    streams.cleanUp();
    streams.start();

    // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
  }

  private static <VT extends SpecificRecord> SpecificAvroSerde<VT> createSerde(final String schemaRegistryUrl) {

    final SpecificAvroSerde<VT> serde = new SpecificAvroSerde<>();
    final Map<String, String> serdeConfig = Collections.singletonMap(
            AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    serde.configure(serdeConfig, false);
    return serde;
  }
}
