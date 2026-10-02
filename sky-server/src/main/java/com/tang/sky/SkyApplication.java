package com.tang.sky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 单体应用唯一启动入口。放在根包下，便于扫描 com.tang.sky 下的控制器与后续业务组件。
 * common 和 pojo 作为依赖被使用，不需要各自启动一个 Spring 容器。
 */
@SpringBootApplication
public class SkyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
    }
}
