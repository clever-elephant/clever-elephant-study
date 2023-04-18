package com.ce.quickstart;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;

import java.util.Collections;

/**
 * 消费者
 */
public class Consumer {
    private final static String CONSUMER_GROUP = "CONSUMER_GROUP";

    public static void main(String[] args) throws ClientException, InterruptedException {
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder().setEndpoints(Constants.ENDPOINT).build();
        ClientServiceProvider clientServiceProvider = ClientServiceProvider.loadService();
        // 订阅消息的过滤规则，表示订阅所有Tag的消息。
        String tag = "*";
        FilterExpression filterExpression = new FilterExpression(tag, FilterExpressionType.TAG);
        clientServiceProvider.newPushConsumerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setConsumerGroup(CONSUMER_GROUP)
                .setMessageListener(messageView -> {
                    System.out.printf("%s receive message: %s %n", Thread.currentThread().getName(),messageView.getMessageId());
                    return ConsumeResult.SUCCESS;
                })
                .setSubscriptionExpressions(Collections.singletonMap(Constants.TOPIC_A, filterExpression))
                .build();

        System.out.println("success");
        Thread.sleep(Long.MAX_VALUE);
        // 如果不需要再使用 PushConsumer，可关闭该实例。
//        consumer.close();
    }
}
