package com.college.internship;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 测试环境数据库防呆断言器 (Fail-Safe Checker)
 * 严格限制在 test profile 下运行。
 * 当底层数据库不是 'internship_db_test' 时抛出 IllegalStateException 立即中断测试容器启动。
 */
@Component
@Profile("test")
public class TestDatabaseSanityChecker implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TestDatabaseSanityChecker.class);
    public static final String REQUIRED_TEST_DB = "internship_db_test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        log.info("【测试数据库安全检查】当前连接数据库: [{}]", currentDb);
        if (!REQUIRED_TEST_DB.equalsIgnoreCase(currentDb)) {
            String fatalError = String.format(
                    "【严重安全阻断】测试环境连接了非测试数据库: [%s]！测试必须在 '%s' 中运行，严禁触碰正式数据库！",
                    currentDb, REQUIRED_TEST_DB
            );
            System.err.println("================================================================================");
            System.err.println(fatalError);
            System.err.println("================================================================================");
            throw new IllegalStateException(fatalError);
        }
        System.out.println("【测试环境防呆校验通过】已确认接入独立测试数据库: " + currentDb);
    }
}
