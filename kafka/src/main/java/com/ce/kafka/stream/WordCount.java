package com.ce.kafka.stream;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Arrays;
import java.util.Properties;

public class WordCount {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "word-count-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "124.221.111.233:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> textLines = builder.stream("");
        KTable<String, Long> wordCount = textLines.flatMapValues(textLine -> Arrays.asList(textLine.split("w"))).groupBy((key, word) -> word).count();
        wordCount.toStream().to("WordsWithCountsTopic", Produced.with(Serdes.String(), Serdes.Long()));
        try (KafkaStreams streams = new KafkaStreams(builder.build(), props)) {
            streams.start();
        }

    }
}
