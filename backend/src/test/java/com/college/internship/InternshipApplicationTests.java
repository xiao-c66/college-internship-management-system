package com.college.internship;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段3工程骨架上下文加载与基础功能测试
 */
@SpringBootTest
@ActiveProfiles("test")
class InternshipApplicationTests {

    @Test
    void contextLoads() {
        assertTrue(true, "Spring Boot 测试上下文顺利装配");
    }
}
