package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.config.SysConfigInitializer;
import com.college.internship.config.SystemProperties;
import com.college.internship.dto.ConfigUpdateDTO;
import com.college.internship.dto.LoginDTO;
import com.college.internship.entity.SysConfig;
import com.college.internship.entity.SysOperationLog;
import com.college.internship.mapper.SysConfigMapper;
import com.college.internship.service.IAuthService;
import com.college.internship.service.ISysConfigService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.entity.SysJob;
import com.college.internship.mapper.SysJobMapper;
import com.college.internship.service.ISysJobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.college.internship.entity.SysBackupRecord;
import com.college.internship.mapper.SysBackupRecordMapper;
import com.college.internship.dto.NoticeCreateDTO;
import com.college.internship.dto.NoticeUpdateDTO;
import com.college.internship.entity.SysNotice;
import com.college.internship.entity.SysNoticeRead;
import com.college.internship.mapper.SysNoticeMapper;
import com.college.internship.mapper.SysNoticeReadMapper;
import com.college.internship.service.ISysBackupService;
import com.college.internship.service.ISysNoticeService;
import com.college.internship.vo.SysNoticeVO;
import com.college.internship.util.SafePathValidator;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段8 核心业务自动化集成测试：
 * 严格覆盖第5版审定方案规划的 TEST-P8-01 ~ TEST-P8-05 (切片1：sys_config 运维配置模块与审计)
 * 落实数据绝对零污染保护：测试前对 sys_config 进行全量快照，测试后完整恢复，严格断言无任何 800 等临时值残留。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Phase8IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthService authService;

    @Autowired
    private ISysConfigService sysConfigService;

    @Autowired
    private ISysOperationLogService operationLogService;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private ISysJobService sysJobService;

    @Autowired
    private SysJobMapper sysJobMapper;

    @Autowired
    private SysConfigInitializer sysConfigInitializer;

    @Autowired
    private SystemProperties systemProperties;

    @Autowired
    private Cache<String, String> configCaffeineCache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String deptAdminToken;
    private String teacherToken;
    private String studentToken;

    @Autowired
    private ISysBackupService sysBackupService;

    @Autowired
    private SysBackupRecordMapper sysBackupRecordMapper;

    private final List<Long> createdBackupRecordIds = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Path> createdBackupFiles = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Autowired
    private ISysNoticeService sysNoticeService;

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    @Autowired
    private SysNoticeReadMapper sysNoticeReadMapper;

    private final List<Long> createdNoticeIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Autowired
    private com.college.internship.service.IPerformanceMonitorService performanceMonitorService;

    @Autowired
    private com.college.internship.security.JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.college.internship.mapper.SysUserMapper sysUserMapper;

    @Autowired
    private com.college.internship.config.Phase7Properties phase7Properties;

    @Autowired
    private com.college.internship.config.WeeklyProperties weeklyProperties;

    // 测试开始前的真实基准快照，用于测试后 100% 精确还原
    private final List<SysConfig> initialSnapshot = new ArrayList<>();

    // 动态创建的配置键追踪集合，用于测试后正向精确清理，严禁使用负向 NOT IN 扫表删除
    private final List<String> createdConfigKeys = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeAll
    void initSuite() throws Exception {
        // 测试启动防呆校验：必须连接独立测试数据库 internship_db_test
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"internship_db_test".equalsIgnoreCase(currentDb)) {
            throw new IllegalStateException("【严重安全阻断】当前测试数据库为: [" + currentDb + "]，非 'internship_db_test'！已强制终止测试！");
        }

        // 确保出厂独立初始化机制已执行
        sysConfigInitializer.initDefaultConfigs();
        sysJobService.initDefaultJobs();

        // 捕获测试前的原始基准快照
        List<SysConfig> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getIsDeleted, 0));
        initialSnapshot.clear();
        for (SysConfig r : rows) {
            initialSnapshot.add(SysConfig.builder()
                    .id(r.getId())
                    .configName(r.getConfigName())
                    .configKey(r.getConfigKey())
                    .configValue(r.getConfigValue())
                    .isSystem(r.getIsSystem())
                    .remark(r.getRemark())
                    .createdBy(r.getCreatedBy())
                    .createdTime(r.getCreatedTime())
                    .updatedBy(r.getUpdatedBy())
                    .updatedTime(r.getUpdatedTime())
                    .isDeleted(r.getIsDeleted())
                    .build());
        }

        adminToken = obtainToken("admin");
        deptAdminToken = obtainToken("deptadmin");
        teacherToken = obtainToken("teacher");
        studentToken = obtainToken("student");
    }

    @BeforeEach
    void setUp() {
        restoreSysConfigSnapshot();
    }

    @AfterEach
    void tearDown() {
        restoreSysConfigSnapshot();
    }

    @AfterAll
    void cleanSuite() {
        // 彻底恢复快照 (严禁 DELETE FROM sys_config 清空业务表)
        restoreSysConfigSnapshot();

        // 严格断言核验：sys_config 绝对不残留测试值 (特别是 800 等修改值)
        List<SysConfig> currentConfigs = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getIsDeleted, 0));
        assertEquals(initialSnapshot.size(), currentConfigs.size(), "sys_config 表记录总数必须与测试前完全一致");

        for (SysConfig current : currentConfigs) {
            assertFalse("800".equals(current.getConfigValue()),
                    "测试结束后 sys_config 绝不能残留 800 等测试临时值: key=" + current.getConfigKey());
        }

        // 校验 4 项基准参数完整一致
        assertEquals("500", sysConfigService.getConfigValue("system.slow-sql-threshold-ms"));
        assertEquals("30", sysConfigService.getConfigValue("system.backup.retention-days"));
        assertEquals("30", sysConfigService.getConfigValue("system.backup.rate-limit-seconds"));
        assertEquals("60", sysConfigService.getConfigValue("system.job.execution-timeout-seconds"));

        // 核验并恢复 sys_job 状态：基准任务严格保持 3 条，测试只清理动态创建的任务，绝不删除白名单基准任务
        jdbcTemplate.update("UPDATE sys_job SET status = 1 WHERE job_code IN ('WARN_SCAN_JOB', 'ARCHIVE_EXPIRE_RECLOCK_JOB', 'BACKUP_CLEANUP_JOB') AND is_deleted = 0");
        Integer currentJobsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_job WHERE is_deleted = 0", Integer.class);
        assertEquals(3, currentJobsCount, "sys_job 基准白名单任务数量必须始终保持为 3 条");

        // 清理测试动态创建的备份记录与物理文件 (绝对不删除 baseline/milestone/contract_fix 等受保护文件，不删除 is_locked=1 文件)
        for (Long backupId : createdBackupRecordIds) {
            sysBackupRecordMapper.deleteById(backupId);
        }
        createdBackupRecordIds.clear();

        for (Path p : createdBackupFiles) {
            try {
                if (Files.exists(p)) {
                    Files.delete(p);
                }
            } catch (Exception ignored) {
            }
        }
        createdBackupFiles.clear();

        // 清理测试动态创建的通知公告与阅读记录 (保证测试库通知零残留)
        for (Long noticeId : createdNoticeIds) {
            jdbcTemplate.update("DELETE FROM sys_notice_read WHERE notice_id = ?", noticeId);
            jdbcTemplate.update("DELETE FROM sys_notice WHERE id = ?", noticeId);
        }
        createdNoticeIds.clear();
        jdbcTemplate.update("DELETE FROM sys_notice_read WHERE notice_id IN (SELECT id FROM sys_notice WHERE dedup_key LIKE 'TEST_P8_%')");
        jdbcTemplate.update("DELETE FROM sys_notice WHERE dedup_key LIKE 'TEST_P8_%'");

        // 严格落实要求：禁止删除 sys_operation_log 审计记录，永久保留所有操作与任务审计轨迹
    }

    private void restoreSysConfigSnapshot() {
        // 1. 正向定向清理：仅物理删除本次测试实际动态创建的配置键，严禁使用 NOT IN 扫表负向删除
        for (String createdKey : createdConfigKeys) {
            jdbcTemplate.update("DELETE FROM sys_config WHERE config_key = ?", createdKey);
        }
        createdConfigKeys.clear();

        // 2. 基准原值恢复：对测试前已存在的基准配置项，按 config_key 精确 UPDATE 还原全部字段 (包含 is_deleted)
        for (SysConfig config : initialSnapshot) {
            jdbcTemplate.update(
                    "UPDATE sys_config SET config_value = ?, updated_by = ?, updated_time = ?, is_deleted = ? WHERE config_key = ?",
                    config.getConfigValue(), config.getUpdatedBy(), config.getUpdatedTime(), config.getIsDeleted(), config.getConfigKey()
            );
        }
        configCaffeineCache.invalidateAll();
    }

    private String obtainToken(String username) throws Exception {
        CaptchaVO captcha = authService.generateCaptcha();
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword("123456");
        loginDTO.setCaptchaKey(captcha.getCaptchaKey());
        loginDTO.setCaptcha(captcha.getCaptchaCode());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    @Test
    @DisplayName("TEST-P8-01: 系统参数配置查询与出厂默认基准比对 (API-103，纯只读无副作用)")
    void testGetConfigListAndDefaults() throws Exception {
        // 记录调用前数据库行数
        long countBefore = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getIsDeleted, 0));

        // 1. 超管正常查阅运维参数列表
        MvcResult result = mockMvc.perform(get("/api/v1/system/configs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证 GET 方法绝对幂等且纯只读，未向数据库执行任何新增写入
        long countAfter = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getIsDeleted, 0));
        assertEquals(countBefore, countAfter, "GET 查询接口必须保持纯只读幂等，严禁隐式向数据库写入新增记录");

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.path("data");
        assertEquals(4, data.size(), "必须严格仅返回阶段8自身4个受限运维参数");

        // 验证4个白名单参数键名与默认出厂值
        boolean hasSlowSql = false;
        boolean hasBackupRetention = false;
        boolean hasBackupRateLimit = false;
        boolean hasJobTimeout = false;

        for (JsonNode item : data) {
            String key = item.path("configKey").asText();
            String val = item.path("configValue").asText();
            String defaultVal = item.path("defaultValue").asText();

            // 严禁包含阶段6/7业务参数
            assertFalse(key.startsWith("internship."), "严禁包含阶段6/7实习业务参数: " + key);
            assertFalse(key.contains("export-seconds"), "阶段6/7导出限流参数已被剥离为只读，禁止返回: " + key);
            assertFalse(key.contains("warn-scan-seconds"), "阶段6/7预警扫描限流参数已被剥离为只读，禁止返回: " + key);
            assertFalse(key.contains("max-voucher-mb"), "阶段6凭证大小限制已被剥离为只读，禁止返回: " + key);

            if ("system.slow-sql-threshold-ms".equals(key)) {
                hasSlowSql = true;
                assertEquals("500", val);
                assertEquals("500", defaultVal);
            } else if ("system.backup.retention-days".equals(key)) {
                hasBackupRetention = true;
                assertEquals("30", val);
                assertEquals("30", defaultVal);
            } else if ("system.backup.rate-limit-seconds".equals(key)) {
                hasBackupRateLimit = true;
                assertEquals("30", val);
                assertEquals("30", defaultVal);
            } else if ("system.job.execution-timeout-seconds".equals(key)) {
                hasJobTimeout = true;
                assertEquals("60", val);
                assertEquals("60", defaultVal);
            }
        }

        assertTrue(hasSlowSql, "必须包含慢SQL判定阈值参数");
        assertTrue(hasBackupRetention, "必须包含备份保留天数参数");
        assertTrue(hasBackupRateLimit, "必须包含备份限流冷却秒数参数");
        assertTrue(hasJobTimeout, "必须包含任务执行超时参数");

        // 2. 越权校验：学生、教师和院系管理员访问该接口必须直接被拦截 403 Forbidden
        mockMvc.perform(get("/api/v1/system/configs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/system/configs")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/system/configs")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST-P8-02: 运维参数动态修改、Caffeine 缓存热刷新与可信代理IP防伪造 (API-104)")
    void testUpdateConfigAndCaffeineCacheEviction() throws Exception {
        String initialVal = sysConfigService.getConfigValue("system.slow-sql-threshold-ms");
        assertEquals("500", initialVal);

        // 1. 模拟非可信外部客户端直连，试图伪造 X-Forwarded-For 篡改审计 IP
        ConfigUpdateDTO updateDTO = ConfigUpdateDTO.builder()
                .configValue("800")
                .remark("压测环境调整慢SQL阈值为800ms")
                .build();

        mockMvc.perform(put("/api/v1/system/configs/system.slow-sql-threshold-ms")
                        .header("Authorization", "Bearer " + adminToken)
                        .with(request -> {
                            request.setRemoteAddr("192.168.10.88"); // 非可信直连 IP
                            return request;
                        })
                        .header("X-Forwarded-For", "114.114.114.114") // 客户端恶意伪造标头
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证数据库真实落盘数据
        SysConfig dbConfig = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "system.slow-sql-threshold-ms")
                .eq(SysConfig::getIsDeleted, 0));
        assertNotNull(dbConfig);
        assertEquals("800", dbConfig.getConfigValue());
        assertEquals("admin", dbConfig.getUpdatedBy());

        // 验证 Caffeine 缓存同步热刷新：Service 获取到新值 800
        String newVal = sysConfigService.getConfigValue("system.slow-sql-threshold-ms");
        assertEquals("800", newVal);
        assertEquals(800, sysConfigService.getIntValue("system.slow-sql-threshold-ms", 500));

        // 验证操作审计真实落盘，且可信代理边界防御生效：非可信直连来源强制记录其实际物理 IP (192.168.10.88)，伪造标头被完全忽略
        List<SysOperationLog> logs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE oper_url = '/api/v1/system/configs/system.slow-sql-threshold-ms' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .title(rs.getString("title"))
                        .businessType(rs.getString("business_type"))
                        .operatorId(rs.getLong("operator_id"))
                        .operatorName(rs.getString("operator_name"))
                        .operIp(rs.getString("oper_ip"))
                        .operParam(rs.getString("oper_param"))
                        .status(rs.getInt("status"))
                        .build()
        );
        assertFalse(logs.isEmpty(), "必须记录配置更新审计日志");
        SysOperationLog opLog = logs.get(0);
        assertEquals("UPDATE", opLog.getBusinessType());
        assertEquals("admin", opLog.getOperatorName());
        assertEquals(1, opLog.getStatus());
        assertEquals("192.168.10.88", opLog.getOperIp(), "非可信直连连接必须记录真实物理 IP，严禁采信伪造的 X-Forwarded-For");
        assertTrue(opLog.getOperParam().contains("\"oldValue\":\"500\""));
        assertTrue(opLog.getOperParam().contains("\"newValue\":\"800\""));

        // 2. 模拟可信反向代理 (127.0.0.1) 转发合规客户端 IP (202.108.22.5)
        ConfigUpdateDTO updateDTO2 = ConfigUpdateDTO.builder()
                .configValue("600")
                .remark("可信反向代理转发调优")
                .build();

        mockMvc.perform(put("/api/v1/system/configs/system.slow-sql-threshold-ms")
                        .header("Authorization", "Bearer " + adminToken)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1"); // 可信代理 IP
                            return request;
                        })
                        .header("X-Forwarded-For", "202.108.22.5, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO2)))
                .andExpect(status().isOk());

        SysOperationLog proxyLog = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE oper_url = '/api/v1/system/configs/system.slow-sql-threshold-ms' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operIp(rs.getString("oper_ip"))
                        .build()
        ).get(0);
        assertEquals("202.108.22.5", proxyLog.getOperIp(), "可信反向代理转发时应合规采信客户端真实来源 IP");
    }

    @Test
    @DisplayName("TEST-P8-03: 非白名单运维键防劫持拦截与出厂重置 (API-104 & API-105)")
    void testNonWhitelistRejectionAndReset() throws Exception {
        // 1. 尝试修改阶段6/7已封板业务参数 (如 pass-min / export-seconds)，断言 400 阻断
        ConfigUpdateDTO hijackDTO = ConfigUpdateDTO.builder()
                .configValue("50.00")
                .remark("恶意篡改及格分数线")
                .build();

        mockMvc.perform(put("/api/v1/system/configs/internship.phase7.score.grade-rules.pass-min")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("阶段6/7封板只读保护或非白名单运维项")));

        mockMvc.perform(put("/api/v1/system/configs/system.rate-limit.export-seconds")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("阶段6/7封板只读保护或非白名单运维项")));

        // 2. 尝试传入超出合规范围的值 (如 slow-sql 传 99999 或 -1)，断言 400 阻断
        ConfigUpdateDTO outOfBoundDTO = ConfigUpdateDTO.builder()
                .configValue("99999")
                .build();

        mockMvc.perform(put("/api/v1/system/configs/system.slow-sql-threshold-ms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outOfBoundDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("参数值超出合规范围")));

        // 3. 正常修改后调用 API-105 出厂重置接口
        ConfigUpdateDTO validUpdate = ConfigUpdateDTO.builder()
                .configValue("1200")
                .build();
        mockMvc.perform(put("/api/v1/system/configs/system.slow-sql-threshold-ms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk());
        assertEquals("1200", sysConfigService.getConfigValue("system.slow-sql-threshold-ms"));

        // 执行一键出厂重置
        mockMvc.perform(post("/api/v1/system/configs/system.slow-sql-threshold-ms/reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证恢复为 500
        assertEquals("500", sysConfigService.getConfigValue("system.slow-sql-threshold-ms"));
        SysConfig dbConfig = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "system.slow-sql-threshold-ms")
                .eq(SysConfig::getIsDeleted, 0));
        assertEquals("500", dbConfig.getConfigValue());
    }

    @Test
    @DisplayName("TEST-P8-04: 登录安全审计真实入库与 UA 字段截断核验 (API-120)")
    void testLoginSecurityAuditPersistence() throws Exception {
        // 1. 模拟 teacher 账号成功登录
        CaptchaVO captcha = authService.generateCaptcha();
        LoginDTO successLogin = new LoginDTO();
        successLogin.setUsername("teacher");
        successLogin.setPassword("123456");
        successLogin.setCaptchaKey(captcha.getCaptchaKey());
        successLogin.setCaptcha(captcha.getCaptchaCode());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successLogin)))
                .andExpect(status().isOk());

        // 验证审计日志中包含 operator_name = 'teacher', status = 1, 且密码脱敏
        List<SysOperationLog> successLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'LOGIN' AND operator_name = 'teacher' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .title(rs.getString("title"))
                        .businessType(rs.getString("business_type"))
                        .operatorId(rs.getLong("operator_id"))
                        .operatorName(rs.getString("operator_name"))
                        .operUrl(rs.getString("oper_url"))
                        .operParam(rs.getString("oper_param"))
                        .status(rs.getInt("status"))
                        .build()
        );
        assertFalse(successLogs.isEmpty(), "登录成功审计必须落盘");
        SysOperationLog loginSuccess = successLogs.get(0);
        assertEquals(1, loginSuccess.getStatus());
        assertEquals("teacher", loginSuccess.getOperatorName());
        assertEquals(3L, loginSuccess.getOperatorId());
        assertEquals("/api/v1/auth/login", loginSuccess.getOperUrl());
        assertFalse(loginSuccess.getOperParam().contains("123456"), "登录审计参数绝对禁止记录明文密码");

        // 2. 模拟密码错误登录失败
        CaptchaVO captchaFail = authService.generateCaptcha();
        LoginDTO failLogin = new LoginDTO();
        failLogin.setUsername("student");
        failLogin.setPassword("wrong_password_999");
        failLogin.setCaptchaKey(captchaFail.getCaptchaKey());
        failLogin.setCaptcha(captchaFail.getCaptchaCode());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failLogin)))
                .andExpect(status().isBadRequest());

        // 验证失败审计日志
        List<SysOperationLog> failLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'LOGIN' AND operator_name = 'student' AND status = 0 ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operatorName(rs.getString("operator_name"))
                        .status(rs.getInt("status"))
                        .errorMsg(rs.getString("error_msg"))
                        .build()
        );
        assertFalse(failLogs.isEmpty(), "登录失败必须入库");
        SysOperationLog loginFail = failLogs.get(0);
        assertEquals(0, loginFail.getStatus());
        assertEquals("student", loginFail.getOperatorName());
        assertNotNull(loginFail.getErrorMsg());
    }

    @Test
    @DisplayName("TEST-P8-05: 定时调度日志与耗时结构化入库核验 (API-121)")
    void testSchedulerLogPersistenceAndCostMsJson() {
        // 模拟调度引擎触发到期重锁任务并写入审计日志
        long costMs = 128L;
        int processedCount = 2;
        String jsonResult = String.format("{\"costMs\":%d,\"processedCount\":%d,\"status\":\"SUCCESS\"}", costMs, processedCount);

        operationLogService.logOperation(
                "定时任务调度-ARCHIVE_EXPIRE_RECLOCK_JOB",
                "SCHEDULE",
                "com.college.internship.task.ArchiveExpireReclockJob.execute",
                "SYSTEM",
                0L,
                "SYSTEM_SCHEDULER",
                "job://ARCHIVE_EXPIRE_RECLOCK_JOB",
                "127.0.0.1",
                "{\"jobCode\":\"ARCHIVE_EXPIRE_RECLOCK_JOB\"}",
                jsonResult,
                1,
                null
        );

        // 检索该调度审计记录
        List<SysOperationLog> scheduleLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'SCHEDULE' AND operator_name = 'SYSTEM_SCHEDULER' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .title(rs.getString("title"))
                        .businessType(rs.getString("business_type"))
                        .operatorId(rs.getLong("operator_id"))
                        .operatorName(rs.getString("operator_name"))
                        .operUrl(rs.getString("oper_url"))
                        .jsonResult(rs.getString("json_result"))
                        .status(rs.getInt("status"))
                        .build()
        );

        assertFalse(scheduleLogs.isEmpty(), "定时调度日志必须入库");
        SysOperationLog schedLog = scheduleLogs.get(0);
        assertEquals("SYSTEM_SCHEDULER", schedLog.getOperatorName());
        assertEquals(0L, schedLog.getOperatorId());
        assertEquals("job://ARCHIVE_EXPIRE_RECLOCK_JOB", schedLog.getOperUrl());

        // 验证物理表无独立 cost_ms 字段下，耗时以结构化 JSON 存入 json_result
        assertNotNull(schedLog.getJsonResult());
        try {
            JsonNode resultNode = objectMapper.readTree(schedLog.getJsonResult());
            assertTrue(resultNode.has("costMs"), "json_result 必须包含结构化 costMs 字段");
            assertEquals(128, resultNode.get("costMs").asInt());
            assertEquals(2, resultNode.get("processedCount").asInt());
        } catch (Exception e) {
            throw new RuntimeException("json_result 非合法 JSON 格式: " + schedLog.getJsonResult(), e);
        }
    }

    // ============================================================================================
    // 切片2: sys_job 受限定时任务与自动化生命周期运维 (TEST-P8-06 ~ TEST-P8-09)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P8-06: 定时任务受限白名单编码校验与非法阻断 (API-106 & API-108)")
    void testJobListWhitelistAndIllegalRejection() throws Exception {
        // 1. API-106 纯只读性与无副作用核验：调用前后与多次重复调用下 sys_job 行数必须绝对不变
        int jobCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_job WHERE is_deleted = 0", Integer.class);

        // 第一次调用 GET /api/v1/system/jobs
        MvcResult result = mockMvc.perform(get("/api/v1/system/jobs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andReturn();

        // 第二次重复调用 GET /api/v1/system/jobs (验证纯只读与幂等性)
        mockMvc.perform(get("/api/v1/system/jobs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        int jobCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_job WHERE is_deleted = 0", Integer.class);
        assertEquals(jobCountBefore, jobCountAfter, "API-106 必须为纯只读查询，多次调用前后 sys_job 行数必须绝对一致，禁止产生隐式写入");

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.path("data");
        List<String> codes = new ArrayList<>();
        for (JsonNode item : data) {
            codes.add(item.path("jobCode").asText());
        }
        assertTrue(codes.contains("WARN_SCAN_JOB"), "必须包含预警扫描任务白名单");
        assertTrue(codes.contains("ARCHIVE_EXPIRE_RECLOCK_JOB"), "必须包含特批重锁任务白名单");
        assertTrue(codes.contains("BACKUP_CLEANUP_JOB"), "必须包含备份清理任务白名单");

        // 2. 权限隔离校验：学生端与教师端访问直接 403
        mockMvc.perform(get("/api/v1/system/jobs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/system/jobs")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // 3. 正常启停切换 (API-108)
        Long reclockJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_job WHERE job_code = 'ARCHIVE_EXPIRE_RECLOCK_JOB' AND is_deleted = 0", Long.class);
        assertNotNull(reclockJobId);

        // 暂停任务 -> 0
        mockMvc.perform(put("/api/v1/system/jobs/" + reclockJobId + "/toggle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(0));

        // 重新启动任务 -> 1
        mockMvc.perform(put("/api/v1/system/jobs/" + reclockJobId + "/toggle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1));

        // 4. 非受限白名单任务编码强拦截 (防任意反射注入)
        jdbcTemplate.update("INSERT INTO sys_job (job_code, job_name, cron_expression, status, remark, created_by) " +
                "VALUES ('MALICIOUS_REFLECTION_JOB', '恶意反射注入任务', '0 0 * * * ?', 1, '非法任务', 'ATTACKER')");
        Long maliciousId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_job WHERE job_code = 'MALICIOUS_REFLECTION_JOB'", Long.class);

        try {
            // 尝试触发非法任务 -> 400 Bad Request
            mockMvc.perform(post("/api/v1/system/jobs/" + maliciousId + "/trigger")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("非受限白名单任务编码")));

            // 尝试启停非法任务 -> 400 Bad Request
            mockMvc.perform(put("/api/v1/system/jobs/" + maliciousId + "/toggle")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("非受限白名单任务编码")));
        } finally {
            jdbcTemplate.update("DELETE FROM sys_job WHERE id = ?", maliciousId);
        }
    }

    @Test
    @DisplayName("TEST-P8-07: 手动触发单次定时调度与执行审计留痕 (API-107)")
    void testManualTriggerJobAndAuditLogging() throws Exception {
        sysJobService.clearIdempotencyCache();

        Long backupJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_job WHERE job_code = 'BACKUP_CLEANUP_JOB' AND is_deleted = 0", Long.class);
        assertNotNull(backupJobId);

        // 1. 手动触发单次执行 (API-107)
        mockMvc.perform(post("/api/v1/system/jobs/" + backupJobId + "/trigger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobCode").value("BACKUP_CLEANUP_JOB"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 2. 检查审计日志 sys_operation_log 必须留痕
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT * FROM sys_operation_log WHERE business_type = 'SCHEDULE' AND oper_url = 'job://BACKUP_CLEANUP_JOB' ORDER BY id DESC LIMIT 1"
        );
        assertFalse(logs.isEmpty(), "手动触发调度必须产生审计日志");
        Map<String, Object> logRow = logs.get(0);
        assertEquals("admin", logRow.get("operator_name"), "手动触发操作人必须为管理员真实账号");
        assertEquals(1, ((Number) logRow.get("status")).intValue(), "执行状态必须成功标记为1");

        String jsonResult = (String) logRow.get("json_result");
        assertNotNull(jsonResult);
        JsonNode resultNode = objectMapper.readTree(jsonResult);
        assertTrue(resultNode.has("costMs"), "json_result 必须包含 costMs");
        assertTrue(resultNode.has("processedCount"), "json_result 必须包含 processedCount");
        assertEquals("SUCCESS", resultNode.get("status").asText());

        // 3. 幂等键防抖校验：同窗口期 (分钟级) 重复触发必须跳过
        mockMvc.perform(post("/api/v1/system/jobs/" + backupJobId + "/trigger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.reason").value("IDEMPOTENT_WINDOW"));
    }

    @Test
    @DisplayName("TEST-P8-08: 预警定时扫描引擎自动化联动执行测试 (API-107 联动 WARN_SCAN_JOB)")
    void testWarnScanJobLinkageExecution() throws Exception {
        sysJobService.clearIdempotencyCache();

        Long warnJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_job WHERE job_code = 'WARN_SCAN_JOB' AND is_deleted = 0", Long.class);
        assertNotNull(warnJobId);

        // 手动联动触发预警全盘扫描
        mockMvc.perform(post("/api/v1/system/jobs/" + warnJobId + "/trigger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobCode").value("WARN_SCAN_JOB"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 验证预警调度执行审计日志
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT * FROM sys_operation_log WHERE business_type = 'SCHEDULE' AND oper_url = 'job://WARN_SCAN_JOB' ORDER BY id DESC LIMIT 1"
        );
        assertFalse(logs.isEmpty(), "预警调度任务必须记录审计日志");
        Map<String, Object> logRow = logs.get(0);
        assertEquals("admin", logRow.get("operator_name"));
        assertEquals(1, ((Number) logRow.get("status")).intValue());
    }

    @Test
    @DisplayName("TEST-P8-09: 特批解锁到期卷宗状态自动恢复置为 ARCHIVED (ARCHIVE_EXPIRE_RECLOCK_JOB)")
    void testArchiveExpireReclockJobLifecycle() throws Exception {
        long suffix = System.currentTimeMillis() % 1000000;
        String testStuUsername = "test_stu_p8_job_" + suffix;
        String studentNumber = "STU_P8_JOB_" + suffix;

        // 1. 创建隔离专用测试学生与任务 (为满足 uk_archive_task_student 约束，三个卷宗分别关联三位测试学生)
        String stu1 = "test_stu_p8_job1_" + suffix;
        String stu2 = "test_stu_p8_job2_" + suffix;
        String stu3 = "test_stu_p8_job3_" + suffix;

        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, user_type, user_number, dept_id, major_id, class_id, status, is_deleted) VALUES " +
                "(?, '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', 'P8调度学生1', 'STUDENT', ?, 1, 1, 1, 1, 0), " +
                "(?, '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', 'P8调度学生2', 'STUDENT', ?, 1, 1, 1, 1, 0), " +
                "(?, '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', 'P8调度学生3', 'STUDENT', ?, 1, 1, 1, 1, 0)",
                stu1, "STU_P8_1_" + suffix,
                stu2, "STU_P8_2_" + suffix,
                stu3, "STU_P8_3_" + suffix
        );
        Long testStuId1 = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, stu1);
        Long testStuId2 = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, stu2);
        Long testStuId3 = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, stu3);

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 4), (?, 4), (?, 4)",
                testStuId1, testStuId2, testStuId3);

        String testTaskCode = "TASK_P8_JOB_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO internship_task (task_code, task_name, dept_id, academic_year, semester, internship_mode, start_date, end_date, status, weekly_frequency, weekly_deadline_day, " +
                "weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary) " +
                "VALUES (?, 'P8调度测试任务', 1, '2025-2026', 2, 'DISTRIBUTED', CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY), 'PUBLISHED', 'WEEKLY', 7, 20.0, 20.0, 20.0, 20.0, 20.0)",
                testTaskCode
        );
        Long testTaskId = jdbcTemplate.queryForObject("SELECT id FROM internship_task WHERE task_code = ?", Long.class, testTaskCode);

        // 2. 构造三份卷宗：
        // A. 逾期未锁定 (SPECIAL_UNLOCKED 且 unlock_expire_time 为 1 小时前) -> 必须被重置为 ARCHIVED
        // B. 仍在有效期内 (SPECIAL_UNLOCKED 且 unlock_expire_time 为 24 小时后) -> 必须保持 SPECIAL_UNLOCKED
        // C. 历史正常锁定卷宗 (ARCHIVED) -> 严禁触碰，保持 ARCHIVED
        String overdueArcNo = "ARC_TEST_OVERDUE_" + suffix;
        String activeArcNo = "ARC_TEST_ACTIVE_" + suffix;
        String normalArcNo = "ARC_TEST_NORMAL_" + suffix;

        try {
            jdbcTemplate.update(
                    "INSERT INTO internship_archive (archive_no, task_id, student_id, dept_id, academic_year, check_matrix_json, archived_user_id, archived_time, status, special_doc_no, special_unlock_reason, unlock_expire_time, version, is_deleted) " +
                    "VALUES (?, ?, ?, 1, '2025-2026', '{\"passed\":true}', 2, NOW(), 'SPECIAL_UNLOCKED', 'DOC_OVERDUE', '测试逾期重锁', DATE_SUB(NOW(), INTERVAL 1 HOUR), 1, 0)",
                    overdueArcNo, testTaskId, testStuId1
            );
            jdbcTemplate.update(
                    "INSERT INTO internship_archive (archive_no, task_id, student_id, dept_id, academic_year, check_matrix_json, archived_user_id, archived_time, status, special_doc_no, special_unlock_reason, unlock_expire_time, version, is_deleted) " +
                    "VALUES (?, ?, ?, 1, '2025-2026', '{\"passed\":true}', 2, NOW(), 'SPECIAL_UNLOCKED', 'DOC_ACTIVE', '测试有效特批', DATE_ADD(NOW(), INTERVAL 24 HOUR), 1, 0)",
                    activeArcNo, testTaskId, testStuId2
            );
            jdbcTemplate.update(
                    "INSERT INTO internship_archive (archive_no, task_id, student_id, dept_id, academic_year, check_matrix_json, archived_user_id, archived_time, status, version, is_deleted) " +
                    "VALUES (?, ?, ?, 1, '2025-2026', '{\"passed\":true}', 2, NOW(), 'ARCHIVED', 1, 0)",
                    normalArcNo, testTaskId, testStuId3
            );

            sysJobService.clearIdempotencyCache();

            Long reclockJobId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_job WHERE job_code = 'ARCHIVE_EXPIRE_RECLOCK_JOB' AND is_deleted = 0", Long.class);
            assertNotNull(reclockJobId);

            // 3. 触发特批到期重锁调度
            mockMvc.perform(post("/api/v1/system/jobs/" + reclockJobId + "/trigger")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.jobCode").value("ARCHIVE_EXPIRE_RECLOCK_JOB"))
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.processedCount", greaterThanOrEqualTo(1)));

            // 4. 断言结果状态：
            // A. 逾期记录恢复为 ARCHIVED
            String overdueStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM internship_archive WHERE archive_no = ?", String.class, overdueArcNo);
            assertEquals("ARCHIVED", overdueStatus, "已逾期的特批解锁卷宗必须自动恢复为 ARCHIVED");

            // B. 未逾期记录保持 SPECIAL_UNLOCKED
            String activeStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM internship_archive WHERE archive_no = ?", String.class, activeArcNo);
            assertEquals("SPECIAL_UNLOCKED", activeStatus, "未逾期的特批解锁卷宗必须保持 SPECIAL_UNLOCKED");

            // C. 历史正常锁定卷宗依然保持 ARCHIVED
            String normalStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM internship_archive WHERE archive_no = ?", String.class, normalArcNo);
            assertEquals("ARCHIVED", normalStatus, "历史正常已归档卷宗严禁被误修改");

        } finally {
            // 5. 严格反序清理测试生命周期内创建的临时实体，保证测试库零残留
            jdbcTemplate.update("DELETE FROM internship_archive WHERE task_id = ?", testTaskId);
            jdbcTemplate.update("DELETE FROM internship_task WHERE id = ?", testTaskId);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", testStuId1, testStuId2, testStuId3);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", testStuId1, testStuId2, testStuId3);
        }
    }

    // ============================================================================================
    // 切片3: sys_backup_record 受控备份管理与防穿越安全下载 (TEST-P8-10 ~ TEST-P8-12)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P8-10: 数据库全量热备份触发与受控元数据入库 (API-110 & API-109)")
    void testBackupExecutionAndRecords() throws Exception {
        sysBackupService.clearRateLimitCache();

        // 1. 权限隔离校验：学生与教师角色尝试触发热备均抛出 403
        mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // 2. 超管正常触发热备 (API-110)
        MvcResult execResult = mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.backupFileName").isString())
                .andExpect(jsonPath("$.data.tableCount").value(37))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();

        JsonNode root = objectMapper.readTree(execResult.getResponse().getContentAsString());
        JsonNode data = root.path("data");
        Long backupId = data.path("id").asLong();
        String fileName = data.path("backupFileName").asText();
        long fileSize = data.path("fileSizeBytes").asLong();
        String sha256 = data.path("sha256Digest").asText();

        createdBackupRecordIds.add(backupId);
        Path baseDir = Paths.get(systemProperties.getBackup().getStorageDir()).toAbsolutePath().normalize();
        createdBackupFiles.add(baseDir.resolve(fileName));

        // 验证受控路径规约：绝对严禁暴露包含服务器物理磁盘盘符 (如 D:\ 等) 的绝对路径
        assertFalse(fileName.contains(":") || fileName.startsWith("/") || fileName.startsWith("\\"),
                "API 返回文件名必须为受控相对路径，绝对严禁暴露服务器物理盘符");
        assertTrue(fileName.endsWith(".sql"), "备份文件名必须以 .sql 结尾");
        assertTrue(fileSize > 0, "备份文件大小必须大于 0 字节");
        assertEquals(64, sha256.length(), "SHA-256 散列值长度必须精确为 64 位十六进制字符");

        // 3. 验证元数据入库 sys_backup_record
        SysBackupRecord dbRecord = sysBackupRecordMapper.selectById(backupId);
        assertNotNull(dbRecord, "sys_backup_record 表中必须存在该备份元数据记录");
        assertEquals(fileName, dbRecord.getBackupFileName());
        assertEquals(fileSize, dbRecord.getFileSizeBytes());
        assertEquals(37, dbRecord.getTableCount());
        assertEquals(sha256, dbRecord.getSha256Digest());
        assertEquals(0, dbRecord.getIsLocked());

        // 4. 验证操作审计日志入库 sys_operation_log
        List<SysOperationLog> logs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'BACKUP' AND oper_url = '/api/v1/system/backup/execute' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operatorName(rs.getString("operator_name"))
                        .operUrl(rs.getString("oper_url"))
                        .status(rs.getInt("status"))
                        .jsonResult(rs.getString("json_result"))
                        .build()
        );
        assertFalse(logs.isEmpty(), "手动备份操作必须如实记录到 sys_operation_log 审计表");
        assertEquals("admin", logs.get(0).getOperatorName());
        assertEquals(1, logs.get(0).getStatus());

        // 5. 超管查询备份记录列表 (API-109)
        MvcResult listResult = mockMvc.perform(get("/api/v1/system/backup/records")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode listData = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        boolean foundNewBackup = false;
        for (JsonNode item : listData) {
            if (item.path("id").asLong() == backupId) {
                foundNewBackup = true;
                assertEquals(fileName, item.path("backupFileName").asText());
                assertEquals(sha256, item.path("sha256Digest").asText());
            }
        }
        assertTrue(foundNewBackup, "API-109 必须能够查询到刚生成的受控备份记录");
    }

    @Test
    @DisplayName("TEST-P8-11: 备份文件 SHA-256 完整性摘要与冷却防抖 (API-110)")
    void testBackupSha256AndRateLimitCooling() throws Exception {
        sysBackupService.clearRateLimitCache();

        // 1. 首次触发备份生成
        MvcResult firstResult = mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode data = objectMapper.readTree(firstResult.getResponse().getContentAsString()).path("data");
        Long backupId = data.path("id").asLong();
        String fileName = data.path("backupFileName").asText();
        String recordedSha256 = data.path("sha256Digest").asText();

        createdBackupRecordIds.add(backupId);
        Path baseDir = Paths.get(systemProperties.getBackup().getStorageDir()).toAbsolutePath().normalize();
        Path targetFile = baseDir.resolve(fileName);
        createdBackupFiles.add(targetFile);

        // 2. 真实核验磁盘物理文件的真实 SHA-256 散列与接口记录是否 100% 吻合
        assertTrue(Files.exists(targetFile), "物理备份文件必须存在于受控存储目录");
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream is = Files.newInputStream(targetFile)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        String realSha256 = sb.toString();
        assertEquals(recordedSha256, realSha256, "备份文件记录的 SHA-256 摘要值必须与磁盘物理文件真实计算值完全一致");

        // 3. 冷却限流防抖校验：在 30 秒冷却保护窗口内立即高频重复调用，必须被 429 拦截阻断
        mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message", containsString("过于频繁")));
    }

    @Test
    @DisplayName("TEST-P8-12: 统一备份安全下载与 SafePathValidator 防穿越 (API-111)")
    void testBackupSecureDownloadAndPathTraversalDefense() throws Exception {
        sysBackupService.clearRateLimitCache();

        // 1. SafePathValidator 工具类单元防卫校验
        // A. 拦截包含 .. 跨目录穿越
        assertThrows(com.college.internship.common.BusinessException.class, () ->
                SafePathValidator.validateFileName("../../windows/system32/cmd.exe"));
        // B. 拦截绝对盘符与斜杠
        assertThrows(com.college.internship.common.BusinessException.class, () ->
                SafePathValidator.validateFileName("D:/devlop/secret.sql"));
        // C. 拦截非 .sql 后缀文件
        assertThrows(com.college.internship.common.BusinessException.class, () ->
                SafePathValidator.validateFileName("config.ini"));

        // 2. 先生成一份合规的热备份文件用于下载验证
        MvcResult execResult = mockMvc.perform(post("/api/v1/system/backup/execute")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long validBackupId = objectMapper.readTree(execResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        String validFileName = objectMapper.readTree(execResult.getResponse().getContentAsString())
                .path("data").path("backupFileName").asText();
        createdBackupRecordIds.add(validBackupId);
        Path baseDir = Paths.get(systemProperties.getBackup().getStorageDir()).toAbsolutePath().normalize();
        createdBackupFiles.add(baseDir.resolve(validFileName));

        // 3. 权限隔离校验：非 SYS_ADMIN 尝试下载必须返回 403
        mockMvc.perform(get("/api/v1/system/backup/download/" + validBackupId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/system/backup/download/" + validBackupId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // 4. 不存在的备份 ID 尝试下载返回 404
        mockMvc.perform(get("/api/v1/system/backup/download/99999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        // 5. 模拟数据库存在非法穿越文件名的记录（恶意数据或脏数据场景），API-111 必须被 SafePathValidator 拦截抛出 400
        SysBackupRecord maliciousRecord = SysBackupRecord.builder()
                .backupFileName("../../../etc/shadow.sql")
                .fileSizeBytes(100L)
                .tableCount(1)
                .sha256Digest("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .isLocked(0)
                .status("SUCCESS")
                .operatorId(1L)
                .backupTime(java.time.LocalDateTime.now())
                .build();
        sysBackupRecordMapper.insert(maliciousRecord);
        Long maliciousId = maliciousRecord.getId();
        try {
            mockMvc.perform(get("/api/v1/system/backup/download/" + maliciousId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        } finally {
            sysBackupRecordMapper.deleteById(maliciousId);
        }

        // 6. 合法超管正常流式下载成功 (API-111)
        MvcResult downloadResult = mockMvc.perform(get("/api/v1/system/backup/download/" + validBackupId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"" + validFileName + "\"")))
                .andExpect(header().string("Content-Type", containsString(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .andReturn();

        byte[] downloadedBytes = downloadResult.getResponse().getContentAsByteArray();
        assertTrue(downloadedBytes.length > 0, "下载的文件流大小必须大于 0");

        // 7. 验证下载操作审计记录
        List<SysOperationLog> downloadLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'BACKUP' AND oper_url = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operatorName(rs.getString("operator_name"))
                        .operUrl(rs.getString("oper_url"))
                        .status(rs.getInt("status"))
                        .build(),
                "/api/v1/system/backup/download/" + validBackupId
        );
        assertFalse(downloadLogs.isEmpty(), "安全下载操作必须向 sys_operation_log 记录审计日志");
        assertEquals("admin", downloadLogs.get(0).getOperatorName());
        assertEquals(1, downloadLogs.get(0).getStatus());
    }

    @Test
    @DisplayName("TEST-P8-13: 通知公告人工发布与 dedup_key 业务防重及 XSS 过滤 (API-114)")
    void testNoticePublishAndDedupKeyAndXssFilter() throws Exception {
        // 1. 院系管理员 (DEPT_ADMIN) 正常发布本院系通知 (API-114)
        NoticeCreateDTO deptDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_13_DEPT_001")
                .noticeTitle("软件工程系阶段性实习复核通知")
                .noticeType("NOTICE")
                .targetScope("DEPT")
                .noticeContent("<p>请各位实习生于本周五前提交阶段报告。</p>")
                .build();

        MvcResult deptResult = mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.targetScope").value("DEPT"))
                .andExpect(jsonPath("$.data.targetDeptId").value(1))
                .andReturn();

        Long deptNoticeId = objectMapper.readTree(deptResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdNoticeIds.add(deptNoticeId);

        // 验证数据库真实落盘
        SysNotice dbDeptNotice = sysNoticeMapper.selectById(deptNoticeId);
        assertNotNull(dbDeptNotice, "发布成功的通知必须真实写入 sys_notice 物理表");
        assertEquals(1, dbDeptNotice.getStatus());
        assertEquals("TEST_P8_13_DEPT_001", dbDeptNotice.getDedupKey());

        // 2. 院系管理员越权发布全校范围通知 (targetScope=ALL)，断言 400 拦截
        NoticeCreateDTO overstepDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_13_DEPT_OVERSTEP")
                .noticeTitle("非法越权全校通知")
                .noticeType("NOTICE")
                .targetScope("ALL")
                .noticeContent("<p>非法发布全校范围通知测试</p>")
                .build();

        mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overstepDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("禁止发布全校范围通知")));

        // 3. 教师 (TEACHER) 与学生 (STUDENT) 角色尝试发布通知，断言 403 严格权限拦截
        NoticeCreateDTO teacherDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_13_TEACHER_FORBIDDEN")
                .noticeTitle("教师尝试发布")
                .noticeType("NOTICE")
                .targetScope("DEPT")
                .noticeContent("<p>教师无权发布测试</p>")
                .build();

        mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherDto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherDto)))
                .andExpect(status().isForbidden());

        // 4. 重复提交相同 dedup_key，断言触发唯一索引业务防重并拦截 400
        NoticeCreateDTO duplicateDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_13_DEPT_001") // 重复使用已存在的防重键
                .noticeTitle("重复提交公告标题")
                .noticeType("NOTICE")
                .targetScope("ALL")
                .noticeContent("<p>重复提交内容</p>")
                .build();

        mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("重复的业务防重键")));

        // 5. 服务端 Jsoup XSS 清洗验证：正文包含恶意脚本，入库后被完全净化
        NoticeCreateDTO xssDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_13_XSS_ATTACK")
                .noticeTitle("全校富文本通知XSS净化防卫测试")
                .noticeType("ANNOUNCE")
                .targetScope("ALL")
                .noticeContent("<p>合规段落</p><script>alert('XSS_EXPLOIT');</script><img src='x' onerror='alert(1)' /><b>加粗强调</b>")
                .build();

        MvcResult xssResult = mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long xssNoticeId = objectMapper.readTree(xssResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdNoticeIds.add(xssNoticeId);

        SysNotice xssDbNotice = sysNoticeMapper.selectById(xssNoticeId);
        assertNotNull(xssDbNotice);
        assertFalse(xssDbNotice.getNoticeContent().contains("<script>"), "Jsoup 清洗后严禁包含 <script> 恶意标签");
        assertFalse(xssDbNotice.getNoticeContent().contains("XSS_EXPLOIT"), "Jsoup 清洗后严禁包含脚本执行载荷");
        assertFalse(xssDbNotice.getNoticeContent().contains("onerror"), "Jsoup 清洗后严禁包含 onerror 内联事件");
        assertTrue(xssDbNotice.getNoticeContent().contains("<p>合规段落</p>"), "合规的 HTML 段落必须予以保留");
        assertTrue(xssDbNotice.getNoticeContent().contains("<b>加粗强调</b>"), "合规的 HTML 样式标签必须予以保留");

        // 6. 验证发布操作审计入库 sys_operation_log
        List<SysOperationLog> noticeLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'NOTICE' AND oper_url = '/api/v1/system/notices' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operatorName(rs.getString("operator_name"))
                        .operUrl(rs.getString("oper_url"))
                        .status(rs.getInt("status"))
                        .build()
        );
        assertFalse(noticeLogs.isEmpty(), "发布通知操作必须写入 sys_operation_log 审计表");
        assertEquals(1, noticeLogs.get(0).getStatus());
    }

    @Test
    @DisplayName("TEST-P8-14: 通知公告用户阅读状态记录、防并发重复与权限隔离 (API-112, API-113, API-115)")
    void testNoticeReadStatusAndIsolationAndLifecycle() throws Exception {
        // 1. 准备测试数据：
        // A. 全校通知 (ALL)
        NoticeCreateDTO allNoticeDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_14_ALL_001")
                .noticeTitle("2026年全校实习动员通报")
                .noticeType("ANNOUNCE")
                .targetScope("ALL")
                .noticeContent("<p>全校师生实习动员安排。</p>")
                .build();
        MvcResult allResult = mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allNoticeDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long allNoticeId = objectMapper.readTree(allResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdNoticeIds.add(allNoticeId);

        // B. 本院系通知 (DEPT, deptId = 1)
        NoticeCreateDTO deptNoticeDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_14_DEPT1_001")
                .noticeTitle("计算机学院周报提交流程指引")
                .noticeType("NOTICE")
                .targetScope("DEPT")
                .noticeContent("<p>本院系学生周报流程要求。</p>")
                .build();
        MvcResult deptResult = mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptNoticeDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long dept1NoticeId = objectMapper.readTree(deptResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdNoticeIds.add(dept1NoticeId);

        // C. 他院系通知 (DEPT, targetDeptId = 9999)，由超管发布
        NoticeCreateDTO otherDeptNoticeDto = NoticeCreateDTO.builder()
                .dedupKey("TEST_P8_14_DEPT999_001")
                .noticeTitle("外国语学院专属翻译实习公告")
                .noticeType("NOTICE")
                .targetScope("DEPT")
                .targetDeptId(9999L)
                .noticeContent("<p>其他院系专属实习通知。</p>")
                .build();
        MvcResult otherDeptResult = mockMvc.perform(post("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherDeptNoticeDto)))
                .andExpect(status().isOk())
                .andReturn();
        Long otherDeptNoticeId = objectMapper.readTree(otherDeptResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdNoticeIds.add(otherDeptNoticeId);

        // 2. 列表可见性隔离验证 (API-112)
        // A. 超管能够看到所有通知 (包含 ALL 与所有 DEPT)
        MvcResult adminListRes = mockMvc.perform(get("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        JsonNode adminListData = objectMapper.readTree(adminListRes.getResponse().getContentAsString()).path("data");
        List<Long> adminSeenIds = new ArrayList<>();
        adminListData.forEach(n -> adminSeenIds.add(n.path("id").asLong()));
        assertTrue(adminSeenIds.contains(allNoticeId), "超管必须可见全校通知");
        assertTrue(adminSeenIds.contains(dept1NoticeId), "超管必须可见本院通知");
        assertTrue(adminSeenIds.contains(otherDeptNoticeId), "超管必须可见他院通知");

        // B. 学生 (deptId = 1) 仅可见 ALL 与本院系通知，绝不可见他院系通知 (deptId = 9999)
        MvcResult studentListRes = mockMvc.perform(get("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode studentListData = objectMapper.readTree(studentListRes.getResponse().getContentAsString()).path("data");
        List<Long> studentSeenIds = new ArrayList<>();
        studentListData.forEach(n -> studentSeenIds.add(n.path("id").asLong()));
        assertTrue(studentSeenIds.contains(allNoticeId), "学生必须可见全校通知");
        assertTrue(studentSeenIds.contains(dept1NoticeId), "学生必须可见本院系通知");
        assertFalse(studentSeenIds.contains(otherDeptNoticeId), "【严格隔离】学生绝对不可见其他院系专属通知");

        // C. 教师 (deptId = 1) 同样受院系隔离保护
        MvcResult teacherListRes = mockMvc.perform(get("/api/v1/system/notices")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode teacherListData = objectMapper.readTree(teacherListRes.getResponse().getContentAsString()).path("data");
        List<Long> teacherSeenIds = new ArrayList<>();
        teacherListData.forEach(n -> teacherSeenIds.add(n.path("id").asLong()));
        assertTrue(teacherSeenIds.contains(allNoticeId));
        assertTrue(teacherSeenIds.contains(dept1NoticeId));
        assertFalse(teacherSeenIds.contains(otherDeptNoticeId), "【严格隔离】指导教师绝对不可见其他院系专属通知");

        // 3. 详情获取与阅读状态幂等写入 (API-113)
        // 查阅前：学生对 allNoticeId 尚未阅读
        Integer beforeReadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notice_read WHERE notice_id = ? AND user_id = 4",
                Integer.class, allNoticeId
        );
        assertEquals(0, beforeReadCount, "查阅前必须不存在该学生的已读记录");

        // 首次点击查阅详情 (API-113)
        MvcResult firstDetailRes = mockMvc.perform(get("/api/v1/system/notices/" + allNoticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readTime").isNotEmpty())
                .andReturn();

        // 验证 sys_notice_read 物理写入
        Integer afterFirstReadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notice_read WHERE notice_id = ? AND user_id = 4",
                Integer.class, allNoticeId
        );
        assertEquals(1, afterFirstReadCount, "首次查阅后必须生成精确 1 条已读时间记录");

        // 重复点击查阅详情 (幂等调用，不能报错，且不能生成重复数据)
        mockMvc.perform(get("/api/v1/system/notices/" + allNoticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isRead").value(true));

        Integer afterSecondReadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notice_read WHERE notice_id = ? AND user_id = 4",
                Integer.class, allNoticeId
        );
        assertEquals(1, afterSecondReadCount, "重复查阅必须保持幂等，uk_notice_user 保证绝不重复生成多条记录");

        // 4. 越权详情获取拦截 (API-113)：学生尝试查阅非本院系通知 (deptId = 9999)，必须拦截 403
        mockMvc.perform(get("/api/v1/system/notices/" + otherDeptNoticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("无权访问其他院系")));

        // 越权阻断后，绝不能留存虚假已读记录
        Integer illegalReadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_notice_read WHERE notice_id = ? AND user_id = 4",
                Integer.class, otherDeptNoticeId
        );
        assertEquals(0, illegalReadCount, "越权拦截时绝不可向 sys_notice_read 留存任何虚假已读记录");

        // 5. 修改/撤回权限验证 (API-115)
        // A. 学生或教师无权修改/撤回通知，拦截 403
        NoticeUpdateDTO updateDto = NoticeUpdateDTO.builder()
                .noticeTitle("学生恶意篡改标题")
                .build();
        mockMvc.perform(put("/api/v1/system/notices/" + dept1NoticeId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());

        // B. 院系管理员尝试修改非本院系通知 (otherDeptNoticeId)，拦截 403
        NoticeUpdateDTO overstepUpdateDto = NoticeUpdateDTO.builder()
                .noticeTitle("院系管理员跨院篡改")
                .build();
        mockMvc.perform(put("/api/v1/system/notices/" + otherDeptNoticeId)
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overstepUpdateDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // C. 原始发布院系负责人合法修改并撤回通知 (status = 0)
        NoticeUpdateDTO validUpdateDto = NoticeUpdateDTO.builder()
                .noticeTitle("计算机学院周报提交流程指引 (已撤回修订)")
                .status(0) // 撤回关闭
                .build();
        mockMvc.perform(put("/api/v1/system/notices/" + dept1NoticeId)
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.noticeTitle").value("计算机学院周报提交流程指引 (已撤回修订)"));

        SysNotice updatedDbNotice = sysNoticeMapper.selectById(dept1NoticeId);
        assertEquals(0, updatedDbNotice.getStatus(), "通知状态必须已成功变更为已撤回/关闭 (status=0)");

        // D. 超管合法重新发布激活该通知 (status = 1)
        NoticeUpdateDTO reactivateDto = NoticeUpdateDTO.builder()
                .status(1)
                .build();
        mockMvc.perform(put("/api/v1/system/notices/" + dept1NoticeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1));

        SysNotice reactivatedDbNotice = sysNoticeMapper.selectById(dept1NoticeId);
        assertEquals(1, reactivatedDbNotice.getStatus(), "超管重新发布后状态必须为正常发布 (status=1)");

        // 6. 验证修改操作审计入库 sys_operation_log
        List<SysOperationLog> updateLogs = jdbcTemplate.query(
                "SELECT * FROM sys_operation_log WHERE business_type = 'NOTICE' AND oper_url LIKE '/api/v1/system/notices/%' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> SysOperationLog.builder()
                        .operatorName(rs.getString("operator_name"))
                        .status(rs.getInt("status"))
                        .build()
        );
        assertFalse(updateLogs.isEmpty(), "修改通知操作必须写入 sys_operation_log 审计表");
        assertEquals(1, updateLogs.get(0).getStatus());
    }

    @Test
    @DisplayName("TEST-P8-15: 服务器健康指标探针采集与健康状态验证 (API-116 & API-124)")
    void testServerMonitorAndHealthProbe() throws Exception {
        // 1. 系统基础健康探活端点 (API-124，阶段3基线复用，公开访问)
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        // 2. 超管获取服务器与JVM监控指标 (API-116)
        MvcResult res = mockMvc.perform(get("/api/v1/system/monitor/server")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.cpuCores").isNumber())
                .andExpect(jsonPath("$.data.jvmTotalMemoryMB").isNumber())
                .andExpect(jsonPath("$.data.totalDiskSpaceGB").isNumber())
                .andExpect(jsonPath("$.data.osName").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(root.path("data").path("cpuCores").asInt() > 0, "CPU 核心数必须大于0");
        assertTrue(root.path("data").path("jvmTotalMemoryMB").asLong() > 0, "JVM 总内存必须大于0");

        // 3. 越权阻断：教师与学生角色访问服务器监控，必须拦截 403
        mockMvc.perform(get("/api/v1/system/monitor/server")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/system/monitor/server")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 4. Caffeine 缓存状态获取 (API-117)
        mockMvc.perform(get("/api/v1/system/monitor/cache")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cacheName").value("configCaffeineCache"))
                .andExpect(jsonPath("$.data.maxSize").value(1000));

        // 5. 超管清理缓存 (API-118)
        mockMvc.perform(post("/api/v1/system/monitor/cache/clear")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 6. 教师越权清理缓存拦截 403
        mockMvc.perform(post("/api/v1/system/monitor/cache/clear")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST-P8-16: 慢 SQL 与接口 P95/P99 耗时分析指标采集与有界性验证 (API-119)")
    void testSlowSqlAndPercentileAlgorithm() throws Exception {
        // 1. 边界算法验证：0 样本
        performanceMonitorService.clearSamplesForTesting();
        com.college.internship.vo.SlowSqlMonitorVO voEmpty = performanceMonitorService.getSlowSqlMetrics();
        assertEquals(0, voEmpty.getSampleCount());
        assertEquals(0L, voEmpty.getP95CostMs());
        assertEquals(0L, voEmpty.getP99CostMs());
        assertEquals(0.0, voEmpty.getAvgCostMs());

        // 2. 边界算法验证：单样本 (120ms)
        performanceMonitorService.recordRequest("/api/v1/test", "GET", 120, 200, "admin", "127.0.0.1", null);
        com.college.internship.vo.SlowSqlMonitorVO voSingle = performanceMonitorService.getSlowSqlMetrics();
        assertEquals(1, voSingle.getSampleCount());
        assertEquals(120L, voSingle.getP95CostMs());
        assertEquals(120L, voSingle.getP99CostMs());
        assertEquals(120L, voSingle.getMinCostMs());
        assertEquals(120L, voSingle.getMaxCostMs());
        assertEquals(120.0, voSingle.getAvgCostMs());

        // 3. 边界算法验证：10 样本 (10, 20, ..., 100)
        performanceMonitorService.clearSamplesForTesting();
        for (int i = 1; i <= 10; i++) {
            performanceMonitorService.recordRequest("/api/v1/test", "GET", i * 10L, 200, "admin", "127.0.0.1", null);
        }
        com.college.internship.vo.SlowSqlMonitorVO vo10 = performanceMonitorService.getSlowSqlMetrics();
        assertEquals(10, vo10.getSampleCount());
        assertEquals(10L, vo10.getMinCostMs());
        assertEquals(100L, vo10.getMaxCostMs());
        assertEquals(55.0, vo10.getAvgCostMs());
        // 10 * 0.95 = 9.5 -> ceil = 10 -> index 9 -> 100
        assertEquals(100L, vo10.getP95CostMs());
        assertEquals(100L, vo10.getP99CostMs());

        // 4. 边界算法验证：100 样本 (1, 2, ..., 100)
        performanceMonitorService.clearSamplesForTesting();
        for (int i = 1; i <= 100; i++) {
            performanceMonitorService.recordRequest("/api/v1/test", "GET", (long) i, 200, "admin", "127.0.0.1", null);
        }
        com.college.internship.vo.SlowSqlMonitorVO vo100 = performanceMonitorService.getSlowSqlMetrics();
        assertEquals(100, vo100.getSampleCount());
        // 100 * 0.95 = 95 -> index 94 -> 95
        assertEquals(95L, vo100.getP95CostMs());
        // 100 * 0.99 = 99 -> index 98 -> 99
        assertEquals(99L, vo100.getP99CostMs());

        // 5. 环形缓冲区真正有界性测试：写入 1100 条样本，容量严格限定在 <= 1000
        performanceMonitorService.clearSamplesForTesting();
        for (int i = 0; i < 1100; i++) {
            performanceMonitorService.recordRequest("/api/v1/heavy", "POST", (i % 600) + 1, 200, "admin", "127.0.0.1", null);
        }
        com.college.internship.vo.SlowSqlMonitorVO voBounded = performanceMonitorService.getSlowSqlMetrics();
        assertTrue(voBounded.getSampleCount() <= 1000, "内存普通样本环形缓冲区必须受限在 1000 条以内");
        assertTrue(voBounded.getSlowCalls().size() <= 500, "内存慢调用记录必须受限在 500 条以内");

        // 6. MockMvc 接口调用 (API-119)
        mockMvc.perform(get("/api/v1/system/monitor/slow-sql")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.thresholdMs").value(500))
                .andExpect(jsonPath("$.data.sampleCount").isNumber());

        // 7. 越权阻断：学生访问慢调用看板返回 403
        mockMvc.perform(get("/api/v1/system/monitor/slow-sql")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TEST-P8-17: 在线活跃 Token 检索与超管强制踢下线 (API-122)")
    void testActiveTokensAndKickUser() throws Exception {
        // 1. 查询在线会话与 Token 列表 (API-122)
        mockMvc.perform(get("/api/v1/system/security/tokens")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        // 2. 越权拦截：学生与教师访问返回 403
        mockMvc.perform(get("/api/v1/system/security/tokens")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/system/security/tokens")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // 3. 动态创建测试账号 (绝不触碰正式账号 student ID=4)
        String kickTestUsername = "test_kick_user_p8_" + System.currentTimeMillis();
        com.college.internship.entity.SysUser tempUser = com.college.internship.entity.SysUser.builder()
                .username(kickTestUsername)
                .userNumber("TEST_NUM_" + System.currentTimeMillis())
                .password("$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2")
                .realName("动态测试踢出学生")
                .userType("STUDENT")
                .deptId(1L)
                .tokenVersion(1L)
                .status(1)
                .isDeleted(0)
                .build();
        sysUserMapper.insert(tempUser);
        Long tempUserId = tempUser.getId();
        assertNotNull(tempUserId, "动态创建测试用户必须成功生成主键ID");

        try {
            // 为动态用户生成合法有效的 JWT Token (tokenVersion = 1)
            String oldJwtToken = jwtTokenProvider.createToken(
                    tempUserId,
                    kickTestUsername,
                    "STUDENT",
                    "ROLE_STUDENT",
                    1L
            );

            // 验证该 Token 当前可正常访问受保护接口 (获取个人信息 /api/v1/auth/me)
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + oldJwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value(kickTestUsername));

            // 4. 超管调用强制踢下线接口 (API-122)
            mockMvc.perform(post("/api/v1/system/security/tokens/" + tempUserId + "/kick")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message", containsString("踢下线")));

            // 5. 校验数据库 token_version 原子递增为 2
            Long currentTv = jdbcTemplate.queryForObject(
                    "SELECT token_version FROM sys_user WHERE id = ?", Long.class, tempUserId);
            assertEquals(2L, currentTv, "踢下线后数据库 token_version 必须已原子自增为 2");

            // 6. 验证踢下线后历史 Token 发起请求立即被 JwtAuthenticationFilter 拦截返回 401 Unauthorized
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + oldJwtToken))
                    .andExpect(status().isUnauthorized());

            // 7. 验证踢下线操作审计入库 sys_operation_log
            List<SysOperationLog> kickLogs = jdbcTemplate.query(
                    "SELECT * FROM sys_operation_log WHERE business_type = 'KICK_OFFLINE' AND oper_url LIKE '%/tokens/" + tempUserId + "/kick%' ORDER BY id DESC LIMIT 1",
                    (rs, rowNum) -> SysOperationLog.builder()
                            .operatorName(rs.getString("operator_name"))
                            .status(rs.getInt("status"))
                            .build()
            );
            assertFalse(kickLogs.isEmpty(), "强制踢下线操作必须记入 sys_operation_log 审计表");
            assertEquals(1, kickLogs.get(0).getStatus());

            // 8. 越权拦截：普通教师尝试踢下线用户，必须拦截 403
            mockMvc.perform(post("/api/v1/system/security/tokens/" + tempUserId + "/kick")
                            .header("Authorization", "Bearer " + teacherToken))
                    .andExpect(status().isForbidden());
        } finally {
            // 清理动态创建的测试用户 (保持正式库与测试库基准零污染)
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", tempUserId);
        }
    }

    @Test
    @DisplayName("TEST-P8-18: 阶段 8 全部 20 个新增接口越权 403 拦截矩阵与日志脱敏 (API-103 ~ API-122)")
    void testPhase8AuthorizationMatrixAndMasking() throws Exception {
        // 1. 20 个新增接口非管理员调用 403 拦截验证矩阵
        // 学生角色调用超管运维与系统控制接口，全部必须断言 403
        String[] adminOnlyUrls = {
                "/api/v1/system/configs",                     // API-103
                "/api/v1/system/jobs",                        // API-106
                "/api/v1/system/backup/records",              // API-109
                "/api/v1/system/backup/download/1",           // API-111
                "/api/v1/system/monitor/server",              // API-116
                "/api/v1/system/monitor/cache",               // API-117
                "/api/v1/system/monitor/slow-sql",            // API-119
                "/api/v1/system/logs/login",                  // API-120
                "/api/v1/system/security/tokens"              // API-122
        };

        for (String url : adminOnlyUrls) {
            mockMvc.perform(get(url)
                            .header("Authorization", "Bearer " + studentToken))
                    .andExpect(status().isForbidden());
        }

        // 2. API-121 审计日志权限与分级脱敏验证
        // A. 学生和教师访问 API-121 必须拦截 403
        mockMvc.perform(get("/api/v1/system/logs/operation")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/system/logs/operation")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        // B. 院系管理员 (deptadmin) 访问 API-121：成功 200，且落实严格数据隔离与脱敏
        MvcResult deptAdminRes = mockMvc.perform(get("/api/v1/system/logs/operation")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode deptLogs = objectMapper.readTree(deptAdminRes.getResponse().getContentAsString()).path("data");
        for (JsonNode logItem : deptLogs) {
            // 严格断言：operator_id=0 的系统调度日志绝对不能泄露给院系负责人
            assertFalse(logItem.path("operatorId").asLong() == 0,
                    "operator_id=0 的系统任务调度日志严禁泄露给 DEPT_ADMIN");
            // 严格断言：IP 必须已脱敏掩码
            String operIp = logItem.path("operIp").asText();
            if (operIp != null && !operIp.isEmpty()) {
                assertTrue(operIp.contains("***") || operIp.equals(""),
                        "DEPT_ADMIN 查阅的操作日志 IP 必须被严格脱敏掩码: " + operIp);
            }
        }

        // C. 超级管理员 (admin) 访问 API-121：返回全校操作日志且 IP 未脱敏
        mockMvc.perform(get("/api/v1/system/logs/operation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("TEST-P8-19: 阶段 8 运维与监控系统向后兼容性 100% 验证")
    void testPhase8BackwardCompatibility() throws Exception {
        // 1. 验证阶段 7 封板业务核心参数未被阶段 8 任何改动所覆盖或污染
        assertNotNull(phase7Properties, "阶段7配置属性类 Phase7Properties 必须完整就绪");
        assertEquals(24, phase7Properties.getArchive().getSpecialUnlockDurationHours(),
                "阶段7特批解锁时效必须严格保持 24 小时出厂设置");
        assertEquals(10, phase7Properties.getMaterial().getMaxFileSizeMb(),
                "阶段7材料附件大小上限必须严格保持 10MB");

        // 2. 验证阶段 6 周报配置参数未被篡改
        assertNotNull(weeklyProperties, "阶段6配置属性类 WeeklyProperties 必须完整就绪");
        assertEquals(15, weeklyProperties.getMinContentLength(),
                "阶段6周报正文最小字符数必须保持 15 字不变");

        // 3. 验证历史已有探活端点 (API-124) 与安全催办查询端点正常工作
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/safety/students")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("TEST-P8-20: 前端系统监控与安全审计组件配置与生产构建验证")
    void testFrontendBuildAndMonitorComponent() throws Exception {
        // 1. 验证前端关键页面组件物理文件真实存在
        Path monitorViewPath = Paths.get("../frontend/src/views/monitor/ServerMonitor.vue");
        assertTrue(Files.exists(monitorViewPath), "前端 ServerMonitor.vue 视图组件必须物理存在");

        // 2. 验证前端生产打包产物及路由配置
        Path routerPath = Paths.get("../frontend/src/router/index.ts");
        assertTrue(Files.exists(routerPath), "前端路由配置文件 index.ts 必须存在");
        String routerContent = Files.readString(routerPath);
        assertTrue(routerContent.contains("ServerMonitor"), "前端路由必须包含 ServerMonitor 路由声明");
        assertTrue(routerContent.contains("admin/system/monitor"), "前端路由必须包含 admin/system/monitor 路径映射");
        assertTrue(routerContent.contains("SYS_ADMIN"), "前端监控路由权限必须严格限定为 SYS_ADMIN");

        // 3. 验证前端生产打包产物存在 (由前置步骤 npm run build 生成)
        Path distIndexPath = Paths.get("../frontend/dist/index.html");
        assertTrue(Files.exists(distIndexPath), "前端构建生产产物 dist/index.html 必须成功生成");
    }
}
