package com.wut.screenappsx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableAsync
@EnableRabbit
@EnableScheduling
@EnableWebSocket
@MapperScan({"com.wut.screendbmysqlsx.Mapper", "com.wut.screenwebsx.Mapper"})
@ComponentScan(basePackages = {
        "com.wut.screenappsx",
        "com.wut.screencommonsx",
        "com.wut.screenmsgsx",
        "com.wut.screendbmysqlsx",
        "com.wut.screendbredissx",
        "com.wut.screendbtdenginesx",
        "com.wut.screenwebsx"
})
public class ScreenAppSxApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScreenAppSxApplication.class, args);
    }

}
