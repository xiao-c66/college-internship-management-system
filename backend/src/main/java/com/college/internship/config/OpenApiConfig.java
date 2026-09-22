package com.college.internship.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 接口文档基础配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("高校实习全过程管理系统 - API 接口文档")
                        .version("1.0.0-SNAPSHOT")
                        .description("基于 Spring Boot 3 + Java 17 的全过程实习教务与学工管理服务接口文档")
                        .contact(new Contact().name("高校实习研发组").email("support@college.edu.cn"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
