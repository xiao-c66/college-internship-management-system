package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.delivery.DeliveryResult;
import com.college.internship.delivery.IVerificationCodeSender;
import com.college.internship.dto.ChangePasswordDTO;
import com.college.internship.dto.ForgotPasswordResetDTO;
import com.college.internship.dto.ForgotPasswordSendCodeDTO;
import com.college.internship.dto.UserImportExecuteDTO;
import com.college.internship.dto.UserImportRowDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.SysRole;
import com.college.internship.entity.SysUser;
import com.college.internship.entity.SysUserRole;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.SysRoleMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.mapper.SysUserRoleMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.impl.UserServiceImpl;
import com.college.internship.vo.ResetPasswordResultVO;
import com.college.internship.vo.UserImportResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 账号密码管理与批量导入纯内存隔离单元测试 (不连接共享数据库)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceIsolatedUnitTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private BaseDepartmentMapper baseDepartmentMapper;
    @Mock
    private BaseMajorMapper baseMajorMapper;
    @Mock
    private BaseClassMapper baseClassMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ISysOperationLogService operationLogService;
    @Mock
    private IVerificationCodeSender verificationCodeSender;

    @InjectMocks
    private UserServiceImpl userService;

    private BaseDepartment csDept;
    private BaseDepartment mathDept;
    private BaseMajor seMajor;
    private BaseClass seClass;

    @BeforeEach
    void setUp() {
        csDept = BaseDepartment.builder().id(10L).deptCode("CS").deptName("计算机与信息工程学院").build();
        mathDept = BaseDepartment.builder().id(20L).deptCode("MATH").deptName("理学院数学系").build();
        seMajor = BaseMajor.builder().id(101L).deptId(10L).majorName("软件工程").build();
        seClass = BaseClass.builder().id(1001L).deptId(10L).majorId(101L).className("软件工程2101班").build();

        when(baseDepartmentMapper.selectList(null)).thenReturn(List.of(csDept, mathDept));
        when(baseMajorMapper.selectList(null)).thenReturn(List.of(seMajor));
        when(baseClassMapper.selectList(null)).thenReturn(List.of(seClass));
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "BCRYPT_" + invocation.getArgument(0));
        when(passwordEncoder.matches(any(), any())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            String encoded = inv.getArgument(1);
            return ("BCRYPT_" + raw).equals(encoded) || raw.equals(encoded);
        });
        when(verificationCodeSender.sendCode(any(), any(), any()))
                .thenReturn(DeliveryResult.success("MOCK_PROVIDER", "mock-uuid-1234"));
    }

    // ==========================================
    // 测试 1: 批量导入与独立高熵临时密码签发
    // ==========================================
    @Test
    @DisplayName("批量导入: 为学生生成唯一高熵独立临时密码，状态置为2(待首次改密)，数据库只存哈希")
    void testBatchImport_GeneratesIndependentTempPassword_ForcesFirstChange() {
        LoginUser deptAdmin = LoginUser.builder()
                .userId(1L)
                .username("dept_admin")
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        SysRole studentRole = SysRole.builder().id(5L).roleCode("ROLE_STUDENT").build();
        when(sysRoleMapper.selectOne(any())).thenReturn(studentRole);

        UserImportRowDTO row1 = UserImportRowDTO.builder()
                .rowNum(2)
                .userNumber("2021001001")
                .username("stu_import_01")
                .realName("李测试")
                .phone("13811112222")
                .email("li@test.com")
                .deptName("计算机与信息工程学院")
                .majorName("软件工程")
                .className("软件工程2101班")
                .build();

        UserImportRowDTO row2 = UserImportRowDTO.builder()
                .rowNum(3)
                .userNumber("2021001002")
                .username("stu_import_02")
                .realName("王测试")
                .phone("13833334444")
                .email("wang@test.com")
                .deptName("计算机与信息工程学院")
                .majorName("软件工程")
                .className("软件工程2101班")
                .build();

        UserImportExecuteDTO executeDTO = UserImportExecuteDTO.builder()
                .userType("STUDENT")
                .rows(List.of(row1, row2))
                .build();

        UserImportResultVO result = userService.executeImport(executeDTO, deptAdmin);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(2, result.getCredentials().size());

        // 验证每个账号的临时密码是独立的，且非空
        String tempPwd1 = result.getCredentials().get(0).getTemporaryPassword();
        String tempPwd2 = result.getCredentials().get(1).getTemporaryPassword();
        assertNotNull(tempPwd1);
        assertNotNull(tempPwd2);
        assertNotEquals(tempPwd1, tempPwd2, "各账号必须生成独立高熵临时密码，禁止共用默认密码");
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength(tempPwd1), "批量导入生成的临时密码必须符合复杂度规则");
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength(tempPwd2), "批量导入生成的临时密码必须符合复杂度规则");

        // 验证持久化 SysUser
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper, times(2)).insert(userCaptor.capture());
        List<SysUser> savedUsers = userCaptor.getAllValues();

        for (SysUser user : savedUsers) {
            assertEquals(2, user.getStatus(), "导入初始状态必须置为2 (待首次改密)");
            assertTrue(user.getPassword().startsWith("BCRYPT_"), "数据库中必须只存储哈希值");
            assertFalse(user.getPassword().contains(tempPwd1) && user.getPassword().contains(tempPwd2), "数据库绝不能存明文密码");
        }
    }

    @Test
    @DisplayName("权限隔离: 院系管理员跨院系导入账号被拒绝(403)")
    void testBatchImport_DeptAdminCrossDept_Forbidden() {
        LoginUser deptAdmin = LoginUser.builder()
                .userId(1L)
                .username("dept_admin")
                .userType("DEPT_ADMIN")
                .deptId(10L) // 计算机学院
                .build();

        UserImportRowDTO crossRow = UserImportRowDTO.builder()
                .rowNum(2)
                .userNumber("2021999999")
                .username("math_stu")
                .realName("数学系学生")
                .deptName("理学院数学系") // 其他院系
                .build();

        UserImportExecuteDTO executeDTO = UserImportExecuteDTO.builder()
                .userType("STUDENT")
                .rows(List.of(crossRow))
                .build();

        UserImportResultVO result = userService.executeImport(executeDTO, deptAdmin);
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
    }

    // ==========================================
    // 测试 2: 管理员重置密码
    // ==========================================
    @Test
    @DisplayName("管理员重置密码: 生成独立临时密码，状态置为2(强制首次改密)，并使在线Token失效")
    void testAdminResetPassword_GeneratesTempPassword_KicksTokens() {
        Long targetUserId = 888L;
        SysUser existingUser = SysUser.builder()
                .id(targetUserId)
                .username("reset_user")
                .realName("重置测试用户")
                .userType("STUDENT")
                .deptId(10L)
                .password("BCRYPT_old_password")
                .status(1)
                .tokenVersion(3L)
                .build();

        when(sysUserMapper.selectById(targetUserId)).thenReturn(existingUser);

        LoginUser deptAdmin = LoginUser.builder()
                .userId(1L)
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        ResetPasswordResultVO result = userService.resetUserPassword(targetUserId, deptAdmin);

        assertNotNull(result.getTemporaryPassword());
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength(result.getTemporaryPassword()), "管理员重置生成的临时密码必须符合复杂度规则");
        assertTrue(existingUser.getPassword().startsWith("BCRYPT_"));
        assertEquals(2, existingUser.getStatus(), "重置后状态必须置为2 (待首次改密)");
        verify(sysUserMapper, times(1)).updateById(existingUser);
        verify(sysUserMapper, times(1)).incrementTokenVersion(targetUserId); // 踢下线
    }

    // ==========================================
    // 测试 3: 用户修改密码与首次改密完成
    // ==========================================
    @Test
    @DisplayName("修改密码: 校验复杂度、原密码与新密码不一致，更新后状态恢复为1(正常活跃)")
    void testChangePassword_ValidatesComplexity_RestoresStatus1() {
        Long userId = 777L;
        SysUser user = SysUser.builder()
                .id(userId)
                .username("change_pwd_user")
                .password("BCRYPT_Temp@123456")
                .status(2) // 待改密状态
                .build();

        when(sysUserMapper.selectById(userId)).thenReturn(user);

        LoginUser loginUser = LoginUser.builder().userId(userId).username("change_pwd_user").build();

        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .oldPassword("Temp@123456")
                .newPassword("NewSecureP@ss2026")
                .confirmPassword("NewSecureP@ss2026")
                .build();

        userService.changePassword(dto, loginUser);

        assertEquals("BCRYPT_NewSecureP@ss2026", user.getPassword());
        assertEquals(1, user.getStatus(), "修改密码后状态恢复为 1 (正常活跃)");
        verify(sysUserMapper, times(1)).updateById(user);
        verify(sysUserMapper, times(1)).incrementTokenVersion(userId);
    }

    @Test
    @DisplayName("修改密码边界校验: 两次密码不一致或原密码错误均应拦截(400)")
    void testChangePassword_ValidationFailures() {
        LoginUser loginUser = LoginUser.builder().userId(777L).username("test_user").build();

        // 两次密码不一致
        ChangePasswordDTO dtoMismatch = ChangePasswordDTO.builder()
                .oldPassword("Old123456@")
                .newPassword("Pass123456@")
                .confirmPassword("Different123456@")
                .build();

        assertThrows(BusinessException.class, () -> userService.changePassword(dtoMismatch, loginUser));

        // 密码过短
        ChangePasswordDTO dtoShort = ChangePasswordDTO.builder()
                .oldPassword("Old123456@")
                .newPassword("Short1!")
                .confirmPassword("Short1!")
                .build();

        BusinessException exShort = assertThrows(BusinessException.class, () -> userService.changePassword(dtoShort, loginUser));
        assertTrue(exShort.getMessage().contains("密码长度不能少于8位"));
    }

    @Test
    @DisplayName("密码复杂度全覆盖校验: 必须包含大写、小写、数字和特殊字符，缺失任一类均拦截(400)")
    void testPasswordComplexity_MissingCategories_Rejected() {
        // 缺少大写字母
        BusinessException exNoUpper = assertThrows(BusinessException.class, () ->
                UserServiceImpl.validatePasswordStrength("password123!"));
        assertTrue(exNoUpper.getMessage().contains("大写英文字母"));

        // 缺少小写字母
        BusinessException exNoLower = assertThrows(BusinessException.class, () ->
                UserServiceImpl.validatePasswordStrength("PASSWORD123!"));
        assertTrue(exNoLower.getMessage().contains("小写英文字母"));

        // 缺少数字
        BusinessException exNoDigit = assertThrows(BusinessException.class, () ->
                UserServiceImpl.validatePasswordStrength("Password!@#$"));
        assertTrue(exNoDigit.getMessage().contains("数字"));

        // 缺少特殊字符
        BusinessException exNoSpecial = assertThrows(BusinessException.class, () ->
                UserServiceImpl.validatePasswordStrength("Password123"));
        assertTrue(exNoSpecial.getMessage().contains("特殊字符"));

        // 弱密码 123456 仅能作为测试沙箱夹具初始化密码，作为新设密码时必须被严格拦截 (API-125)
        BusinessException ex123456 = assertThrows(BusinessException.class, () ->
                UserServiceImpl.validatePasswordStrength("123456"));
        assertTrue(ex123456.getMessage().contains("密码长度不能少于8位"));

        // 合法复杂度测试
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength("Admin@2026"));
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength("Secure#Pass99"));
        assertDoesNotThrow(() -> UserServiceImpl.validatePasswordStrength("P@ssw0rd!#$"));
    }

    @Test
    @DisplayName("修改密码防护: 新密码不能与原密码相同(400)")
    void testChangePassword_SameAsOldPassword_Rejected() {
        Long userId = 778L;
        SysUser user = SysUser.builder()
                .id(userId)
                .username("same_pwd_user")
                .password("BCRYPT_CurrentStrongP@ss1")
                .status(1)
                .build();

        when(sysUserMapper.selectById(userId)).thenReturn(user);

        LoginUser loginUser = LoginUser.builder().userId(userId).username("same_pwd_user").build();

        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .oldPassword("CurrentStrongP@ss1")
                .newPassword("CurrentStrongP@ss1")
                .confirmPassword("CurrentStrongP@ss1")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.changePassword(dto, loginUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("新密码不能与原密码相同"));
    }

    @Test
    @DisplayName("忘记密码重置防护: 新密码不能与当前旧密码相同(400)")
    void testForgotPassword_SameAsOldPassword_Rejected() {
        String username = "student_same_pwd";
        String boundPhone = "13800112233";
        SysUser user = SysUser.builder()
                .id(671L)
                .username(username)
                .phone(boundPhone)
                .password("BCRYPT_CurrentStrongP@ss1")
                .status(1)
                .build();

        when(sysUserMapper.selectOne(any())).thenReturn(user);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        when(verificationCodeSender.sendCode(any(), any(), codeCaptor.capture()))
                .thenReturn(DeliveryResult.success("MOCK_PROVIDER", "mock-uuid-same"));

        ForgotPasswordSendCodeDTO sendDTO = ForgotPasswordSendCodeDTO.builder()
                .username(username)
                .target(boundPhone)
                .build();
        userService.sendForgotPasswordCode(sendDTO);

        String code = codeCaptor.getValue();

        ForgotPasswordResetDTO resetDTO = ForgotPasswordResetDTO.builder()
                .username(username)
                .target(boundPhone)
                .code(code)
                .newPassword("CurrentStrongP@ss1")
                .confirmPassword("CurrentStrongP@ss1")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetForgotPassword(resetDTO));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("新密码不能与当前旧密码相同"));
    }

    // ==========================================
    // 测试 4: 忘记密码安全找回（手机/邮箱校验，防枚举与暴力破解防范）
    // ==========================================
    @Test
    @DisplayName("忘记密码安全规范: 目标手机/邮箱不符或账号不存在时对外统一模糊提示(防枚举探测)")
    void testForgotPassword_TargetMismatchOrUserNotFound_UnifiedError() {
        SysUser user = SysUser.builder()
                .id(666L)
                .username("student_safe")
                .phone("13812345678")
                .email("bound@school.edu.cn")
                .build();

        when(sysUserMapper.selectOne(any())).thenReturn(user);

        // 1. 账号存在但手机号不匹配
        ForgotPasswordSendCodeDTO dto = ForgotPasswordSendCodeDTO.builder()
                .username("student_safe")
                .target("13999999999") // 不匹配
                .build();

        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                userService.sendForgotPasswordCode(dto));
        assertEquals(400, ex1.getCode());
        assertEquals("账号或绑定的安全手机/邮箱不匹配。如未绑定或遗失，请联系管理员线下重置密码", ex1.getMessage());

        // 2. 账号根本不存在 (模拟探测枚举)
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        ForgotPasswordSendCodeDTO dtoUnknown = ForgotPasswordSendCodeDTO.builder()
                .username("non_existent_user")
                .target("13812345678")
                .build();

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                userService.sendForgotPasswordCode(dtoUnknown));
        assertEquals(400, ex2.getCode());
        assertEquals("账号或绑定的安全手机/邮箱不匹配。如未绑定或遗失，请联系管理员线下重置密码", ex2.getMessage(), "账号不存在时必须返回完全相同的信息，防止枚举");
    }

    @Test
    @DisplayName("忘记密码暴力破解防护: 验证码错误累计超过5次直接作废锁定")
    void testForgotPassword_BruteForceProtection_MaxAttemptsInvalidated() {
        String username = "student_brute_test";
        String boundPhone = "13911112222";
        SysUser user = SysUser.builder()
                .id(668L)
                .username(username)
                .phone(boundPhone)
                .password("BCRYPT_OldPass")
                .status(1)
                .build();

        when(sysUserMapper.selectOne(any())).thenReturn(user);

        // 1. 发送验证码
        ForgotPasswordSendCodeDTO sendDTO = ForgotPasswordSendCodeDTO.builder()
                .username(username)
                .target(boundPhone)
                .build();
        userService.sendForgotPasswordCode(sendDTO);

        // 2. 模拟连续 4 次输入错误验证码
        ForgotPasswordResetDTO wrongDTO = ForgotPasswordResetDTO.builder()
                .username(username)
                .target(boundPhone)
                .code("999999")
                .newPassword("NewSecureP@ss123")
                .confirmPassword("NewSecureP@ss123")
                .build();

        for (int i = 1; i <= 4; i++) {
            BusinessException ex = assertThrows(BusinessException.class, () -> userService.resetForgotPassword(wrongDTO));
            assertTrue(ex.getMessage().contains("还剩 " + (5 - i) + " 次尝试机会"));
        }

        // 3. 第 5 次错误尝试 -> 触发超限作废
        BusinessException ex5 = assertThrows(BusinessException.class, () -> userService.resetForgotPassword(wrongDTO));
        assertTrue(ex5.getMessage().contains("验证码错误尝试次数已超限，该验证码已作废"));

        // 4. 第 6 次再尝试 -> 已从缓存移除
        BusinessException ex6 = assertThrows(BusinessException.class, () -> userService.resetForgotPassword(wrongDTO));
        assertTrue(ex6.getMessage().contains("验证码已过期或不存在"));
    }

    @Test
    @DisplayName("外发通道未配置时安全失败: sendForgotPasswordCode 抛出503且绝不写入验证码缓存")
    void testForgotPassword_UnconfiguredGateway_SafeFailure_DoesNotCacheCode() {
        String username = "student_unconfigured";
        String boundPhone = "13800009999";
        SysUser user = SysUser.builder()
                .id(669L)
                .username(username)
                .phone(boundPhone)
                .build();
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        // 模拟外发服务商未配置
        when(verificationCodeSender.sendCode(any(), any(), any()))
                .thenThrow(new BusinessException(503, "系统短信外发通道未配置，请联系系统管理员配置相关服务商凭证"));

        ForgotPasswordSendCodeDTO sendDTO = ForgotPasswordSendCodeDTO.builder()
                .username(username)
                .target(boundPhone)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.sendForgotPasswordCode(sendDTO));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("系统短信外发通道未配置"));

        // 验证由于外发失败，验证码未存入缓存，客户端无法凭此重置
        ForgotPasswordResetDTO resetDTO = ForgotPasswordResetDTO.builder()
                .username(username)
                .target(boundPhone)
                .code("123456")
                .newPassword("NewPass123456!")
                .confirmPassword("NewPass123456!")
                .build();

        BusinessException resetEx = assertThrows(BusinessException.class, () ->
                userService.resetForgotPassword(resetDTO));
        assertTrue(resetEx.getMessage().contains("验证码已过期或不存在"), "外发失败后严禁在系统内留存有效验证码");
    }

    @Test
    @DisplayName("忘记密码防重放验证: 成功重置密码后验证码立失效，二次提交被拒绝")
    void testForgotPassword_AntiReplay_DestroyedAfterSuccessfulReset() {
        String username = "student_replay_test";
        String boundPhone = "13877778888";
        SysUser user = SysUser.builder()
                .id(670L)
                .username(username)
                .phone(boundPhone)
                .password("BCRYPT_OldSecret")
                .status(1)
                .build();
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        // 捕获生成的验证码以完成成功流程
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        when(verificationCodeSender.sendCode(any(), any(), codeCaptor.capture()))
                .thenReturn(DeliveryResult.success("MOCK_PROVIDER", "mock-uuid-replay"));

        ForgotPasswordSendCodeDTO sendDTO = ForgotPasswordSendCodeDTO.builder()
                .username(username)
                .target(boundPhone)
                .build();
        userService.sendForgotPasswordCode(sendDTO);

        String generatedCode = codeCaptor.getValue();
        assertNotNull(generatedCode);

        // 第一次成功重置密码
        ForgotPasswordResetDTO resetDTO = ForgotPasswordResetDTO.builder()
                .username(username)
                .target(boundPhone)
                .code(generatedCode)
                .newPassword("NewStrongP@ss2026")
                .confirmPassword("NewStrongP@ss2026")
                .build();

        assertDoesNotThrow(() -> userService.resetForgotPassword(resetDTO));

        // 第二次使用相同验证码尝试重置 (重放攻击)
        BusinessException replayEx = assertThrows(BusinessException.class, () ->
                userService.resetForgotPassword(resetDTO));
        assertTrue(replayEx.getMessage().contains("验证码已过期或不存在"), "成功重置后验证码必须立即销毁，彻底杜绝重放");
    }

    // ==========================================
    // 测试 5: Excel/CSV 公式注入 (CSV Injection / DDE) 防护
    // ==========================================
    @Test
    @DisplayName("批量导入安全规范: 拦截 = + - @ 等公式注入字符")
    void testBatchImport_FormulaInjection_Rejected() {
        LoginUser deptAdmin = LoginUser.builder()
                .userId(1L)
                .username("dept_admin")
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        SysRole studentRole = SysRole.builder().id(5L).roleCode("ROLE_STUDENT").build();
        when(sysRoleMapper.selectOne(any())).thenReturn(studentRole);

        // 包含公式字符的待导入行
        UserImportRowDTO formulaRow1 = UserImportRowDTO.builder()
                .rowNum(2)
                .userNumber("=cmd|'/C calc'!A0")
                .username("stu_inject_1")
                .realName("测试")
                .deptName("计算机与信息工程学院")
                .majorName("软件工程")
                .className("软件工程2101班")
                .build();

        UserImportRowDTO formulaRow2 = UserImportRowDTO.builder()
                .rowNum(3)
                .userNumber("2021009999")
                .username("+cmd|calc")
                .realName("测试")
                .deptName("计算机与信息工程学院")
                .majorName("软件工程")
                .className("软件工程2101班")
                .build();

        UserImportExecuteDTO executeDTO = UserImportExecuteDTO.builder()
                .userType("STUDENT")
                .rows(List.of(formulaRow1, formulaRow2))
                .build();

        UserImportResultVO result = userService.executeImport(executeDTO, deptAdmin);
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFailureCount(), "所有包含公式注入的行必须被严格拦截入库");
    }

    // ==========================================
    // 测试 6: 导入凭证 previewToken 绑定与防重放/防篡改
    // ==========================================
    @Test
    @DisplayName("批量导入绑定校验: 传入无效或篡改的 previewToken 时拒绝执行(400)")
    void testBatchImport_InvalidPreviewToken_Rejected() {
        LoginUser deptAdmin = LoginUser.builder()
                .userId(1L)
                .username("dept_admin")
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        UserImportExecuteDTO tamperedDTO = UserImportExecuteDTO.builder()
                .userType("STUDENT")
                .previewToken("non-existent-or-tampered-token-uuid")
                .rows(List.of())
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.executeImport(tamperedDTO, deptAdmin));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("导入预览凭证已过期或无效"));
    }
}
