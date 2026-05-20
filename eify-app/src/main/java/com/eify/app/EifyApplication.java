package com.eify.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Eify 启动类
 */
@SpringBootApplication(
    scanBasePackages = "com.eify"
)
@ComponentScan(basePackages = "com.eify")
@MapperScan({"com.eify.**.mapper", "com.eify.**.repository"})
public class EifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(EifyApplication.class, args);
    }
}
