package com.ce.quickstart;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;

import java.io.IOException;

/**
 * 同步生产者
 */
public class SyncProducer {

    public static void main(String[] args) throws ClientException, InterruptedException, IOException {
        ClientConfiguration clientConfiguration = ClientConfiguration.
                newBuilder().setEndpoints(Constants.ENDPOINT).build();
        ClientServiceProvider clientServiceProvider = ClientServiceProvider.loadService();
        Producer producer = clientServiceProvider.newProducerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setTopics(Constants.TOPIC_A)
                .build();
        for (int i = 0; i < 1000; i++) {
            Message message = clientServiceProvider.newMessageBuilder().setTopic(Constants.TOPIC_A).setBody(("Hello ROCKETMQ THIS IS MESSAGE"+i).getBytes()).build();
            SendReceipt receipt = producer.send(message);
            System.out.printf("Message %s has been receipted %n",receipt);
            Thread.sleep(1000);
        }
        producer.close();
    }
}
