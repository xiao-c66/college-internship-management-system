package com.college.internship;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 高校实习全过程管理系统 - 主程序启动入口
 * <p>
 * 阶段4已正式接入基础数据库与持久层，开启全量 MyBatis-Plus Mapper 扫描
 */
@SpringBootApplication
@MapperScan("com.college.internship.mapper")
public class InternshipApplication {

    public static void main(String[] args) {
        SpringApplication.run(InternshipApplication.class, args);
    }
}
