package com.ce.kafka.quickstart;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class CallbackProducer {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "124.221.111.233:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties);) {
            for (int i = 0; i < 5; i++) {
                producer.send(new ProducerRecord<>("first", "hello"), (metadata, exception) -> System.out.println("主题：" + metadata.topic() + " 分区：" + metadata.partition()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
