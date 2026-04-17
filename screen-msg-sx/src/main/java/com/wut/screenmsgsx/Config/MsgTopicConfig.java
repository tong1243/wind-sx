package com.wut.screenmsgsx.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.wut.screencommonsx.Static.MsgModuleStatic.*;

@Configuration
public class MsgTopicConfig {
    @Bean("topicTraj")
    public NewTopic topicTraj() {
        return TopicBuilder.name(TOPIC_NAME_TRAJ).partitions(TOPIC_TRAJ_PARTITIONS).replicas(TOPIC_DEFAULT_REPLICAS).build();
    }

    @Bean("topicWind")
    public NewTopic topicWind() {
        return TopicBuilder.name(TOPIC_NAME_WIND).partitions(TOPIC_WIND_PARTITIONS).replicas(TOPIC_DEFAULT_REPLICAS).build();
    }

}
