package com.wut.screenmsgsx.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MsgTopicConfig {
    private static final String TOPIC_NAME_TRAJ = "traj";
    private static final String TOPIC_NAME_WIND = "wind-realtime";
    private static final int TOPIC_TRAJ_PARTITIONS = 1;
    private static final int TOPIC_WIND_PARTITIONS = 3;
    private static final int TOPIC_DEFAULT_REPLICAS = 1;

    @Bean("topicTraj")
    public NewTopic topicTraj() {
        return TopicBuilder.name(TOPIC_NAME_TRAJ).partitions(TOPIC_TRAJ_PARTITIONS).replicas(TOPIC_DEFAULT_REPLICAS).build();
    }

    @Bean("topicWind")
    public NewTopic topicWind() {
        return TopicBuilder.name(TOPIC_NAME_WIND).partitions(TOPIC_WIND_PARTITIONS).replicas(TOPIC_DEFAULT_REPLICAS).build();
    }

}
