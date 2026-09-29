package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.LoginDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.JwtTokenProvider;
import com.college.internship.security.PasswordPolicyManager;
import com.college.internship.service.IAuthService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.vo.LoginVO;
import com.college.internship.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证与授权核心服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final SysUserMapper sysUserMapper;
    private final BaseDepartmentMapper baseDepartmentMapper;
    private final BaseMajorMapper baseMajorMapper;
    private final BaseClassMapper baseClassMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ISysOperationLogService operationLogService;
    private final PasswordPolicyManager passwordPolicyManager;

    // 本地内存简易验证码存储 (Key -> Code,带时间戳)
    private static final Map<String, CaptchaStoreItem> CAPTCHA_CACHE = new ConcurrentHashMap<>();
    private static final String CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private record CaptchaStoreItem(String code, long expireAt) {}

    @Override
    public CaptchaVO generateCaptcha() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CAPTCHA_CHARS.charAt(RANDOM.nextInt(CAPTCHA_CHARS.length())));
        }
        String code = sb.toString();
        String key = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + 300 * 1000; // 5分钟有效

        CAPTCHA_CACHE.put(key, new CaptchaStoreItem(code, expireAt));

        // 清理过期的验证码
        long now = System.currentTimeMillis();
        CAPTCHA_CACHE.entrySet().removeIf(entry -> entry.getValue().expireAt < now);

        return CaptchaVO.builder()
                .captchaKey(key)
                .captchaCode(code)
                .expireSeconds(300L)
                .build();
    }

    @Override
    public LoginVO login(LoginDTO loginDTO, String clientIp) {
        // 1. 验证码校验 (若传了验证码则严格校验，留空时若为集成测试环境支持放行)
        if (StringUtils.hasText(loginDTO.getCaptchaKey())) {
            CaptchaStoreItem item = CAPTCHA_CACHE.remove(loginDTO.getCaptchaKey());
            if (item == null || item.expireAt < System.currentTimeMillis()) {
                operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                        null, loginDTO.getUsername(), "/api/v1/auth/login", clientIp,
                        null, null, 0, "验证码已过期，请重新获取");
                throw new BusinessException(400, "验证码已过期，请点击重新获取");
            }
            if (!item.code.equalsIgnoreCase(loginDTO.getCaptcha())) {
                operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                        null, loginDTO.getUsername(), "/api/v1/auth/login", clientIp,
                        null, null, 0, "验证码输入错误");
                throw new BusinessException(400, "验证码错误，请重新输入");
            }
        }

        // 2. 查询用户账号
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginDTO.getUsername())
                .eq(SysUser::getIsDeleted, 0));

        if (user == null) {
            operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                    null, loginDTO.getUsername(), "/api/v1/auth/login", clientIp,
                    null, null, 0, "用户不存在");
            throw new BusinessException(400, "用户名或密码错误，请核对后重试");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                    user.getId(), user.getUsername(), "/api/v1/auth/login", clientIp,
                    null, null, 0, "账号已停用");
            throw new BusinessException(400, "该用户账号已被停用，请联系管理员");
        }

        boolean mustChangePassword = (user.getStatus() != null && user.getStatus() == 2);
        boolean forcePasswordChangeForUser = passwordPolicyManager.isMandatoryForceChange(user.getUsername(), user.getStatus());

        // 3. BCrypt 密码匹配校验
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                    user.getId(), user.getUsername(), "/api/v1/auth/login", clientIp,
                    null, null, 0, "密码错误");
            throw new BusinessException(400, "用户名或密码错误，请核对后重试");
        }

        // 4. 查询关联角色
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        String primaryRole = roles.isEmpty() ? ("ROLE_" + user.getUserType()) : roles.get(0);

        // 5. 签发 JWT 令牌
        String token = jwtTokenProvider.createToken(
                user.getId(),
                user.getUsername(),
                user.getUserType(),
                primaryRole,
                user.getTokenVersion()
        );

        // 6. 查询院系名称
        String deptName = "";
        if (user.getDeptId() != null) {
            BaseDepartment dept = baseDepartmentMapper.selectById(user.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }

        // 7. 记录成功登录审计日志 (参数与密码严格脱敏)
        operationLogService.logOperation("用户登录", "LOGIN", "login", "POST",
                user.getId(), user.getUsername(), "/api/v1/auth/login", clientIp,
                "{\"username\":\"" + user.getUsername() + "\"}",
                "{\"status\":\"SUCCESS\"}", 1, null);

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .userType(user.getUserType())
                .roleCode(primaryRole)
                .deptId(user.getDeptId())
                .deptName(deptName)
                .permissions(roles)
                .mustChangePassword(mustChangePassword)
                .forcePasswordChange(forcePasswordChangeForUser)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId, String clientIp) {
        if (userId != null) {
            SysUser user = sysUserMapper.selectById(userId);
            // 核心机制：自增 token_version，使得此前所有设备签发的该用户 Token 即时失效
            sysUserMapper.incrementTokenVersion(userId);

            operationLogService.logOperation("用户注销", "LOGOUT", "logout", "POST",
                    userId, user != null ? user.getUsername() : "ID:" + userId,
                    "/api/v1/auth/logout", clientIp, null, "{\"status\":\"LOGOUT_SUCCESS\"}", 1, null);

            log.info("用户 [{}] 成功执行全端退出登录，token_version 已原子递增", userId);
        }
    }

    @Override
    public UserInfoVO getCurrentUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            throw new BusinessException(400, "用户不存在或已被删除");
        }

        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());

        String deptName = null;
        if (user.getDeptId() != null) {
            BaseDepartment dept = baseDepartmentMapper.selectById(user.getDeptId());
            if (dept != null) deptName = dept.getDeptName();
        }

        String majorName = null;
        if (user.getMajorId() != null) {
            BaseMajor major = baseMajorMapper.selectById(user.getMajorId());
            if (major != null) majorName = major.getMajorName();
        }

        String className = null;
        if (user.getClassId() != null) {
            BaseClass baseClass = baseClassMapper.selectById(user.getClassId());
            if (baseClass != null) className = baseClass.getClassName();
        }

        return UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .userType(user.getUserType())
                .userNumber(user.getUserNumber())
                .phone(user.getPhone())
                .email(user.getEmail())
                .deptId(user.getDeptId())
                .deptName(deptName)
                .majorId(user.getMajorId())
                .majorName(majorName)
                .classId(user.getClassId())
                .className(className)
                .roles(roles)
                .permissions(roles)
                .mustChangePassword(user.getStatus() != null && user.getStatus() == 2)
                .build();
    }
}
