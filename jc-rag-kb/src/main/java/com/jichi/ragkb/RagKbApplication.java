package com.jichi.ragkb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableRetry
@SpringBootApplication
@MapperScan("com.jichi.ragkb.mapper")
public class RagKbApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagKbApplication.class, args);
    }
}