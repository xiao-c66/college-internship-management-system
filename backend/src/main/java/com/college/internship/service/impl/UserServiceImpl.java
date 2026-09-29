package com.college.internship.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.ChangePasswordDTO;
import com.college.internship.dto.ForgotPasswordResetDTO;
import com.college.internship.dto.ForgotPasswordSendCodeDTO;
import com.college.internship.dto.UserImportExecuteDTO;
import com.college.internship.dto.UserImportRowDTO;
import com.college.internship.dto.UserQueryDTO;
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
import com.college.internship.service.IUserService;
import com.college.internship.vo.ResetPasswordResultVO;
import com.college.internship.vo.StudentImportTemplateVO;
import com.college.internship.vo.TeacherImportTemplateVO;
import com.college.internship.vo.UserImportCredentialVO;
import com.college.internship.vo.UserImportPreviewVO;
import com.college.internship.vo.UserImportResultVO;
import com.college.internship.vo.UserImportRowVO;
import com.college.internship.vo.UserManageVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.college.internship.delivery.DeliveryChannel;
import com.college.internship.delivery.DeliveryResult;
import com.college.internship.delivery.IVerificationCodeSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 教师/学生批量导入与系统用户账号密码管理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final BaseDepartmentMapper baseDepartmentMapper;
    private final BaseMajorMapper baseMajorMapper;
    private final BaseClassMapper baseClassMapper;
    private final PasswordEncoder passwordEncoder;
    private final ISysOperationLogService operationLogService;
    private final IVerificationCodeSender verificationCodeSender;

    // 导入预览结果防篡改绑定缓存 (previewToken -> List<UserImportRowDTO>, 15分钟有效) (重点核查 6)
    private final Cache<String, List<UserImportRowDTO>> previewCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    // 忘记密码安全验证码内存缓存 (username -> CodeStoreItem) (重点核查 3: 限流、防重放、暴力破解防范)
    private static final Map<String, ForgotCodeStoreItem> FORGOT_CODE_CACHE = new ConcurrentHashMap<>();
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final Pattern USER_NUMBER_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");
    private static final Pattern FORMULA_PREFIX_PATTERN = Pattern.compile("^[=+\\-@\\t\\r].*");
    private static final String TEMP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private record ForgotCodeStoreItem(String code, String target, long expireAt, long lastSentAt, int failedAttempts) {}

    @Override
    public IPage<UserManageVO> getUserPage(UserQueryDTO queryDTO, LoginUser loginUser, long current, long size) {
        checkAdminPermission(loginUser);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0)
                .orderByDesc(SysUser::getCreateTime);

        // 院系数据范围隔离：院系管理员仅能查看本院系用户
        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            Long deptId = loginUser.getDeptId();
            if (deptId == null) {
                return new Page<>(current, size, 0);
            }
            wrapper.eq(SysUser::getDeptId, deptId);
        } else if (queryDTO != null && queryDTO.getDeptId() != null) {
            wrapper.eq(SysUser::getDeptId, queryDTO.getDeptId());
        }

        if (queryDTO != null) {
            if (StringUtils.hasText(queryDTO.getUserType())) {
                wrapper.eq(SysUser::getUserType, queryDTO.getUserType());
            }
            if (queryDTO.getMajorId() != null) {
                wrapper.eq(SysUser::getMajorId, queryDTO.getMajorId());
            }
            if (queryDTO.getClassId() != null) {
                wrapper.eq(SysUser::getClassId, queryDTO.getClassId());
            }
            if (queryDTO.getStatus() != null) {
                wrapper.eq(SysUser::getStatus, queryDTO.getStatus());
            }
            if (StringUtils.hasText(queryDTO.getKeyword())) {
                String kw = queryDTO.getKeyword().trim();
                wrapper.and(w -> w.like(SysUser::getUsername, kw)
                        .or().like(SysUser::getRealName, kw)
                        .or().like(SysUser::getUserNumber, kw));
            }
        }

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(current, size), wrapper);

        // 批量关联映射学院、专业、班级名称
        Map<Long, String> deptMap = baseDepartmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseDepartment::getId, BaseDepartment::getDeptName, (k1, k2) -> k1));
        Map<Long, String> majorMap = baseMajorMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseMajor::getId, BaseMajor::getMajorName, (k1, k2) -> k1));
        Map<Long, String> classMap = baseClassMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseClass::getId, BaseClass::getClassName, (k1, k2) -> k1));

        List<UserManageVO> voList = page.getRecords().stream().map(u -> UserManageVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .realName(u.getRealName())
                .userType(u.getUserType())
                .userNumber(u.getUserNumber())
                .phone(u.getPhone())
                .email(u.getEmail())
                .deptId(u.getDeptId())
                .deptName(u.getDeptId() != null ? deptMap.get(u.getDeptId()) : null)
                .majorId(u.getMajorId())
                .majorName(u.getMajorId() != null ? majorMap.get(u.getMajorId()) : null)
                .classId(u.getClassId())
                .className(u.getClassId() != null ? classMap.get(u.getClassId()) : null)
                .status(u.getStatus())
                .createTime(u.getCreateTime())
                .build()).collect(Collectors.toList());

        Page<UserManageVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public void downloadImportTemplate(String userType, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName;

            if ("TEACHER".equalsIgnoreCase(userType)) {
                fileName = URLEncoder.encode("教师账号批量导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

                List<TeacherImportTemplateVO> sample = List.of(
                        TeacherImportTemplateVO.builder()
                                .userNumber("T2026001")
                                .username("teacher_wang")
                                .realName("王教师")
                                .phone("13800138001")
                                .email("wang@college.edu.cn")
                                .deptName("计算机与信息工程学院")
                                .build()
                );
                EasyExcel.write(response.getOutputStream(), TeacherImportTemplateVO.class)
                        .sheet("教师导入模板").doWrite(sample);
            } else {
                fileName = URLEncoder.encode("学生账号批量导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

                List<StudentImportTemplateVO> sample = List.of(
                        StudentImportTemplateVO.builder()
                                .userNumber("2021001001")
                                .username("stu2021001001")
                                .realName("张同学")
                                .phone("13900139001")
                                .email("zhang@student.edu.cn")
                                .deptName("计算机与信息工程学院")
                                .majorName("软件工程")
                                .className("软件工程2101班")
                                .build()
                );
                EasyExcel.write(response.getOutputStream(), StudentImportTemplateVO.class)
                        .sheet("学生导入模板").doWrite(sample);
            }
        } catch (IOException e) {
            log.error("下载用户导入模板失败", e);
            throw new BusinessException(500, "下载导入模板失败，请稍后重试");
        }
    }

    @Override
    public UserImportPreviewVO previewImport(MultipartFile file, String userType, LoginUser loginUser) {
        checkAdminPermission(loginUser);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传的导入文件不能为空");
        }

        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new BusinessException(400, "上传导入文件大小不能超过 10MB");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls") && !fileName.endsWith(".csv"))) {
            throw new BusinessException(400, "仅支持上传 Excel (.xlsx, .xls) 或 CSV 文件");
        }

        boolean isStudent = !"TEACHER".equalsIgnoreCase(userType);

        List<Map<Integer, String>> rawDataList;
        try {
            rawDataList = EasyExcel.read(file.getInputStream()).sheet().headRowNumber(1).doReadSync();
        } catch (Exception e) {
            log.error("解析 Excel 文件失败", e);
            throw new BusinessException(400, "Excel 解析失败，请确认文件格式合规并使用标准模板");
        }

        if (rawDataList.isEmpty()) {
            throw new BusinessException(400, "上传文件中未解析到任何有效数据行");
        }

        // 预加载组织架构字典
        Map<String, BaseDepartment> deptNameMap = baseDepartmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseDepartment::getDeptName, d -> d, (k1, k2) -> k1));
        Map<String, BaseMajor> majorNameMap = baseMajorMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseMajor::getMajorName, m -> m, (k1, k2) -> k1));
        Map<String, BaseClass> classNameMap = baseClassMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseClass::getClassName, c -> c, (k1, k2) -> k1));

        // 预加载已存在账号与学工号
        Set<String> existingUsernames = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUsername).eq(SysUser::getIsDeleted, 0)).stream()
                .map(SysUser::getUsername).collect(Collectors.toSet());
        Set<String> existingNumbers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUserNumber).eq(SysUser::getIsDeleted, 0)).stream()
                .map(SysUser::getUserNumber).collect(Collectors.toSet());

        Set<String> seenUsernamesInFile = new HashSet<>();
        Set<String> seenNumbersInFile = new HashSet<>();

        List<UserImportRowVO> previewRows = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;

        for (int i = 0; i < rawDataList.size(); i++) {
            Map<Integer, String> row = rawDataList.get(i);
            int rowNum = i + 2; // Excel 数据行号 (第1行为表头)

            String userNumber = cleanCell(row.get(0));
            String username = cleanCell(row.get(1));
            String realName = cleanCell(row.get(2));
            String phone = cleanCell(row.get(3));
            String email = cleanCell(row.get(4));
            String deptName = cleanCell(row.get(5));
            String majorName = isStudent ? cleanCell(row.get(6)) : null;
            String className = isStudent ? cleanCell(row.get(7)) : null;

            List<String> errors = new ArrayList<>();

            // 1. 必填字段校验与防公式注入 (重点核查 5)
            if (!StringUtils.hasText(userNumber)) {
                errors.add("学号/工号不能为空");
            } else if (FORMULA_PREFIX_PATTERN.matcher(userNumber).matches() || !USER_NUMBER_PATTERN.matcher(userNumber).matches()) {
                errors.add("学号/工号格式不合规或包含非法公式字符(仅限字母数字及-_)");
            }

            if (!StringUtils.hasText(username)) {
                errors.add("登录用户名不能为空");
            } else if (FORMULA_PREFIX_PATTERN.matcher(username).matches() || !USERNAME_PATTERN.matcher(username).matches()) {
                errors.add("登录用户名格式不合规或包含非法公式字符(仅限3-32位字母数字及-_)");
            }

            if (!StringUtils.hasText(realName)) {
                errors.add("姓名不能为空");
            } else if (FORMULA_PREFIX_PATTERN.matcher(realName).matches()) {
                errors.add("姓名包含非法字符，禁止以=、+、-、@等公式字符开头");
            }

            if (!StringUtils.hasText(deptName)) {
                errors.add("所属院系不能为空");
            } else if (FORMULA_PREFIX_PATTERN.matcher(deptName).matches()) {
                errors.add("院系名称包含非法字符");
            }

            if (isStudent) {
                if (!StringUtils.hasText(majorName)) errors.add("学生专业不能为空");
                if (!StringUtils.hasText(className)) errors.add("学生班级不能为空");
            }

            // 2. 格式校验
            if (StringUtils.hasText(phone) && !PHONE_PATTERN.matcher(phone).matches()) {
                errors.add("手机号格式不正确");
            }
            if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
                errors.add("邮箱格式不正确");
            }

            // 3. 重复性校验 (文件内排重)
            if (StringUtils.hasText(username)) {
                if (!seenUsernamesInFile.add(username.toLowerCase())) {
                    errors.add("文件中存在重复用户名: " + username);
                } else if (existingUsernames.contains(username)) {
                    errors.add("系统中已存在同名账号: " + username);
                }
            }
            if (StringUtils.hasText(userNumber)) {
                if (!seenNumbersInFile.add(userNumber.toLowerCase())) {
                    errors.add("文件中存在重复工号/学号: " + userNumber);
                } else if (existingNumbers.contains(userNumber)) {
                    errors.add("系统中已存在相同工号/学号: " + userNumber);
                }
            }

            // 4. 学院/专业/班级关联校验
            BaseDepartment dept = null;
            if (StringUtils.hasText(deptName)) {
                dept = deptNameMap.get(deptName);
                if (dept == null) {
                    errors.add("院系不存在: " + deptName);
                } else if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !dept.getId().equals(loginUser.getDeptId())) {
                    // 权限边界：院系管理员严禁导入其他院系账号
                    errors.add("院系管理员无权跨院系导入账号 (当前限制: " + loginUser.getDeptId() + ")");
                }
            }

            if (isStudent && dept != null) {
                BaseMajor major = null;
                if (StringUtils.hasText(majorName)) {
                    major = majorNameMap.get(majorName);
                    if (major == null) {
                        errors.add("专业不存在: " + majorName);
                    } else if (!dept.getId().equals(major.getDeptId())) {
                        errors.add("专业 [" + majorName + "] 不属于学院 [" + deptName + "]");
                    }
                }

                if (StringUtils.hasText(className)) {
                    BaseClass clazz = classNameMap.get(className);
                    if (clazz == null) {
                        errors.add("班级不存在: " + className);
                    } else if (major != null && !major.getId().equals(clazz.getMajorId())) {
                        errors.add("班级 [" + className + "] 不属于专业 [" + majorName + "]");
                    }
                }
            }

            boolean isValid = errors.isEmpty();
            if (isValid) {
                validCount++;
            } else {
                invalidCount++;
            }

            previewRows.add(UserImportRowVO.builder()
                    .rowNum(rowNum)
                    .userNumber(userNumber)
                    .username(username)
                    .realName(realName)
                    .phone(phone)
                    .email(email)
                    .deptName(deptName)
                    .majorName(majorName)
                    .className(className)
                    .isValid(isValid)
                    .errorMessage(isValid ? null : String.join("; ", errors))
                    .build());
        }

        // 绑定机制 (重点核查 6: 服务端缓存预览通过的行清单，生成 previewToken 供执行时校验防篡改)
        List<UserImportRowDTO> validRows = previewRows.stream()
                .filter(UserImportRowVO::getIsValid)
                .map(r -> UserImportRowDTO.builder()
                        .rowNum(r.getRowNum())
                        .userNumber(r.getUserNumber())
                        .username(r.getUsername())
                        .realName(r.getRealName())
                        .phone(r.getPhone())
                        .email(r.getEmail())
                        .deptName(r.getDeptName())
                        .majorName(r.getMajorName())
                        .className(r.getClassName())
                        .build())
                .collect(Collectors.toList());

        String previewToken = UUID.randomUUID().toString();
        previewCache.put(previewToken, validRows);

        return UserImportPreviewVO.builder()
                .totalCount(rawDataList.size())
                .validCount(validCount)
                .invalidCount(invalidCount)
                .previewToken(previewToken)
                .previewRows(previewRows)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResultVO executeImport(UserImportExecuteDTO executeDTO, LoginUser loginUser) {
        checkAdminPermission(loginUser);

        String userType = "TEACHER".equalsIgnoreCase(executeDTO.getUserType()) ? "TEACHER" : "STUDENT";
        
        // 绑定机制 (重点核查 6: 优先读取服务端缓存的 previewToken 绑定数据，防止客户端篡改行数据)
        List<UserImportRowDTO> rows;
        if (StringUtils.hasText(executeDTO.getPreviewToken())) {
            rows = previewCache.getIfPresent(executeDTO.getPreviewToken());
            if (rows == null) {
                throw new BusinessException(400, "导入预览凭证已过期或无效，请重新上传预览");
            }
            previewCache.invalidate(executeDTO.getPreviewToken()); // 单次消费防重放
        } else {
            rows = executeDTO.getRows();
        }

        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(400, "提交的待导入数据列表不能为空");
        }

        // 查找目标系统角色
        String targetRoleCode = "ROLE_" + userType;
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, targetRoleCode)
                .eq(SysRole::getIsDeleted, 0));
        Long roleId = role != null ? role.getId() : (userType.equals("STUDENT") ? 1L : 2L);

        Map<String, BaseDepartment> deptNameMap = baseDepartmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseDepartment::getDeptName, d -> d, (k1, k2) -> k1));
        Map<String, BaseMajor> majorNameMap = baseMajorMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseMajor::getMajorName, m -> m, (k1, k2) -> k1));
        Map<String, BaseClass> classNameMap = baseClassMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseClass::getClassName, c -> c, (k1, k2) -> k1));

        List<UserImportCredentialVO> credentials = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        Set<String> processedUsernamesInBatch = new HashSet<>();
        Set<String> processedNumbersInBatch = new HashSet<>();

        for (UserImportRowDTO row : rows) {
            try {
                String uName = row.getUsername() != null ? row.getUsername().trim() : "";
                String uNum = row.getUserNumber() != null ? row.getUserNumber().trim() : "";

                // 服务端二次强校验格式与公式注入 (重点核查 5)
                if (!USERNAME_PATTERN.matcher(uName).matches() || FORMULA_PREFIX_PATTERN.matcher(uName).matches()) {
                    throw new BusinessException(400, "用户名格式非法或含公式符号");
                }
                if (!USER_NUMBER_PATTERN.matcher(uNum).matches() || FORMULA_PREFIX_PATTERN.matcher(uNum).matches()) {
                    throw new BusinessException(400, "学工号格式非法或含公式符号");
                }
                if (row.getRealName() != null && FORMULA_PREFIX_PATTERN.matcher(row.getRealName().trim()).matches()) {
                    throw new BusinessException(400, "姓名含公式注入符号");
                }

                // 批次内排重与并发排重 (重点核查 5)
                if (!processedUsernamesInBatch.add(uName.toLowerCase()) || !processedNumbersInBatch.add(uNum.toLowerCase())) {
                    throw new BusinessException(400, "批次内包含重复账号或工号");
                }
                Long existingCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, uName)
                        .or().eq(SysUser::getUserNumber, uNum)
                        .eq(SysUser::getIsDeleted, 0));
                if (existingCount > 0) {
                    throw new BusinessException(400, "系统内已存在同名账号或工号");
                }

                // 服务端二次强校验院系权限与组织架构层级 (重点核查 5)
                BaseDepartment dept = deptNameMap.get(row.getDeptName());
                if (dept == null) {
                    throw new BusinessException(400, "院系不存在");
                }
                if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !dept.getId().equals(loginUser.getDeptId())) {
                    throw new BusinessException(403, "院系管理员无权跨院系导入");
                }

                BaseMajor major = row.getMajorName() != null ? majorNameMap.get(row.getMajorName()) : null;
                BaseClass clazz = row.getClassName() != null ? classNameMap.get(row.getClassName()) : null;
                if ("STUDENT".equals(userType)) {
                    if (major == null || !dept.getId().equals(major.getDeptId())) {
                        throw new BusinessException(400, "专业不存在或与院系不匹配");
                    }
                    if (clazz == null || !major.getId().equals(clazz.getMajorId())) {
                        throw new BusinessException(400, "班级不存在或与专业不匹配");
                    }
                }

                // 生成高熵独立临时密码 (每个账号完全独立，禁止通用默认密码) (重点核查 2)
                String tempPassword = generateSecureTempPassword();
                String encodedPassword = passwordEncoder.encode(tempPassword);

                SysUser newUser = SysUser.builder()
                        .username(uName)
                        .password(encodedPassword)
                        .realName(row.getRealName().trim())
                        .userType(userType)
                        .userNumber(uNum)
                        .phone(StringUtils.hasText(row.getPhone()) ? row.getPhone().trim() : null)
                        .email(StringUtils.hasText(row.getEmail()) ? row.getEmail().trim() : null)
                        .deptId(dept.getId())
                        .majorId(major != null ? major.getId() : null)
                        .classId(clazz != null ? clazz.getId() : null)
                        .status(2) // 状态2: 初始创建，首次登录强制改密
                        .tokenVersion(1L)
                        .build();

                sysUserMapper.insert(newUser);

                // 关联角色表
                SysUserRole userRole = SysUserRole.builder()
                        .userId(newUser.getId())
                        .roleId(roleId)
                        .createTime(LocalDateTime.now())
                        .build();
                sysUserRoleMapper.insert(userRole);

                // 记录凭据用于本次单次下发（数据库仅存散列，日志与报告严禁输出明文）
                credentials.add(UserImportCredentialVO.builder()
                        .rowNum(row.getRowNum())
                        .userNumber(newUser.getUserNumber())
                        .username(newUser.getUsername())
                        .realName(newUser.getRealName())
                        .temporaryPassword(tempPassword)
                        .status("SUCCESS")
                        .build());

                successCount++;
            } catch (Exception e) {
                log.warn("批量导入单行入库失败: username={}, error={}", row.getUsername(), e.getMessage());
                failureCount++;
            }
        }

        // 记录审计日志 (仅记录操作统计，严禁记录任何明文临时密码)
        operationLogService.logOperation("批量用户导入", "IMPORT", "executeImport", "POST",
                loginUser.getUserId(), loginUser.getUsername(), "/api/v1/users/import-execute", "127.0.0.1",
                "{\"userType\":\"" + userType + "\",\"totalRows\":" + rows.size() + "}",
                "{\"successCount\":" + successCount + ",\"failureCount\":" + failureCount + "}",
                1, null);

        return UserImportResultVO.builder()
                .totalCount(rows.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .credentials(credentials)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResultVO resetUserPassword(Long userId, LoginUser loginUser) {
        checkAdminPermission(loginUser);

        SysUser targetUser = sysUserMapper.selectById(userId);
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getIsDeleted())) {
            throw new BusinessException(400, "待重置用户不存在");
        }

        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (targetUser.getDeptId() == null || !targetUser.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系管理员仅允许重置本院系用户密码");
            }
            if ("SYS_ADMIN".equals(targetUser.getUserType())) {
                throw new BusinessException(403, "院系管理员无权重置超级管理员密码");
            }
        }

        String tempPassword = generateSecureTempPassword();
        targetUser.setPassword(passwordEncoder.encode(tempPassword));
        targetUser.setStatus(2); // 重置后强制首次登录改密

        sysUserMapper.updateById(targetUser);
        sysUserMapper.incrementTokenVersion(userId); // 强制使其所有在线会话失效

        // 审计留痕 (参数脱敏)
        operationLogService.logOperation("管理员重置用户密码", "RESET_PWD", "resetUserPassword", "POST",
                loginUser.getUserId(), loginUser.getUsername(), "/api/v1/users/" + userId + "/reset-password", "127.0.0.1",
                "{\"targetUserId\":" + userId + ",\"targetUsername\":\"" + targetUser.getUsername() + "\"}",
                "{\"status\":\"SUCCESS\"}", 1, null);

        return ResetPasswordResultVO.builder()
                .userId(targetUser.getId())
                .username(targetUser.getUsername())
                .realName(targetUser.getRealName())
                .temporaryPassword(tempPassword)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto, LoginUser loginUser) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }

        validatePasswordStrength(dto.getNewPassword());

        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null || Integer.valueOf(1).equals(user.getIsDeleted())) {
            throw new BusinessException(400, "用户不存在");
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码或初始临时密码错误，请核实后重试");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setStatus(1); // 解除待改密状态，恢复为正常活跃状态

        sysUserMapper.updateById(user);
        sysUserMapper.incrementTokenVersion(user.getId());

        operationLogService.logOperation("修改密码", "CHANGE_PWD", "changePassword", "POST",
                user.getId(), user.getUsername(), "/api/v1/users/change-password", "127.0.0.1",
                null, "{\"status\":\"PASSWORD_CHANGED\"}", 1, null);
    }

    @Override
    public void sendForgotPasswordCode(ForgotPasswordSendCodeDTO dto) {
        String username = dto.getUsername() != null ? dto.getUsername().trim() : "";
        String target = dto.getTarget() != null ? dto.getTarget().trim() : "";

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, 0));

        // 防枚举防护：无论账号是否存在、还是手机/邮箱不一致，对外统一返回相同提示，避免探测账号是否存在 (重点核查 3)
        boolean phoneMatched = user != null && StringUtils.hasText(user.getPhone()) && target.equalsIgnoreCase(user.getPhone());
        boolean emailMatched = user != null && StringUtils.hasText(user.getEmail()) && target.equalsIgnoreCase(user.getEmail());

        if (user == null || (!phoneMatched && !emailMatched)) {
            throw new BusinessException(400, "账号或绑定的安全手机/邮箱不匹配。如未绑定或遗失，请联系管理员线下重置密码");
        }

        long now = System.currentTimeMillis();
        ForgotCodeStoreItem item = FORGOT_CODE_CACHE.get(username);
        if (item != null && (now - item.lastSentAt()) < 60 * 1000L) {
            long remaining = 60 - (now - item.lastSentAt()) / 1000L;
            throw new BusinessException(429, "验证码发送过于频繁，请等待 " + remaining + " 秒后重试");
        }

        // 判定外发渠道：包含@为邮箱，否则为手机号
        DeliveryChannel channel = target.contains("@") ? DeliveryChannel.EMAIL : DeliveryChannel.SMS;

        // 生成6位数字高熵验证码，有效期5分钟
        String code = String.format("%06d", RANDOM.nextInt(1000000));

        // 统一调用外发适配层：生产环境未配置真实 provider 时必须安全失败并提示管理员配置，不能对用户假报发送成功
        DeliveryResult deliveryResult = verificationCodeSender.sendCode(channel, target, code);
        if (deliveryResult == null || !deliveryResult.isSuccess()) {
            throw new BusinessException(503, "验证码外发失败: " + (deliveryResult != null ? deliveryResult.getMessage() : "外发异常"));
        }

        // 外发成功确认后方可写入缓存
        FORGOT_CODE_CACHE.put(username, new ForgotCodeStoreItem(code, target, now + 300 * 1000L, now, 0));

        // 审计留痕：仅记录渠道与脱敏目标，严禁将验证码输出至日志或返回给客户端 (重点核查 3)
        log.info("安全验证码交付完成: username={}, target={}, channel={}, provider={}",
                username, maskTarget(target), channel, deliveryResult.getProviderName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetForgotPassword(ForgotPasswordResetDTO dto) {
        String username = dto.getUsername() != null ? dto.getUsername().trim() : "";
        String target = dto.getTarget() != null ? dto.getTarget().trim() : "";
        String code = dto.getCode() != null ? dto.getCode().trim() : "";

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }

        validatePasswordStrength(dto.getNewPassword());

        ForgotCodeStoreItem item = FORGOT_CODE_CACHE.get(username);
        if (item == null || item.expireAt() < System.currentTimeMillis()) {
            throw new BusinessException(400, "验证码已过期或不存在，请重新获取");
        }

        // 防暴力破解：超过5次错误尝试直接作废 (重点核查 3)
        if (item.failedAttempts() >= 5) {
            FORGOT_CODE_CACHE.remove(username);
            throw new BusinessException(400, "验证码错误尝试次数已超限，该验证码已作废，请重新获取");
        }

        if (!item.target().equalsIgnoreCase(target)) {
            FORGOT_CODE_CACHE.remove(username);
            throw new BusinessException(400, "验证安全接收目标不匹配");
        }

        if (!item.code().equals(code)) {
            int newAttempts = item.failedAttempts() + 1;
            if (newAttempts >= 5) {
                FORGOT_CODE_CACHE.remove(username);
                throw new BusinessException(400, "验证码错误尝试次数已超限，该验证码已作废，请重新获取");
            } else {
                FORGOT_CODE_CACHE.put(username, new ForgotCodeStoreItem(item.code(), item.target(), item.expireAt(), item.lastSentAt(), newAttempts));
                throw new BusinessException(400, "验证码错误，还剩 " + (5 - newAttempts) + " 次尝试机会");
            }
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, 0));
        if (user == null) {
            throw new BusinessException(400, "账号或绑定的安全手机/邮箱不匹配。如未绑定或遗失，请联系管理员线下重置密码");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(400, "新密码不能与当前旧密码相同");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setStatus(1); // 成功重置为正常状态
        sysUserMapper.updateById(user);
        sysUserMapper.incrementTokenVersion(user.getId()); // 密码重置强制吊销历史token (重点核查 4)

        FORGOT_CODE_CACHE.remove(username); // 单次验证成功立即销毁 (重点核查 3)

        operationLogService.logOperation("安全验证码重置密码", "FORGOT_PWD_RESET", "resetForgotPassword", "POST",
                user.getId(), user.getUsername(), "/api/v1/users/forgot-password/verify-and-reset", "127.0.0.1",
                null, "{\"status\":\"RESET_SUCCESS\"}", 1, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleUserStatus(Long userId, LoginUser loginUser) {
        checkAdminPermission(loginUser);

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getIsDeleted())) {
            throw new BusinessException(400, "用户不存在");
        }

        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (user.getDeptId() == null || !user.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系管理员仅能管理本院系用户状态");
            }
            if ("SYS_ADMIN".equals(user.getUserType())) {
                throw new BusinessException(403, "院系管理员无权停用超级管理员");
            }
        }

        int newStatus = (user.getStatus() != null && user.getStatus() == 1) ? 0 : 1;
        user.setStatus(newStatus);
        sysUserMapper.updateById(user);

        if (newStatus == 0) {
            sysUserMapper.incrementTokenVersion(userId); // 停用即时踢下线
        }
    }

    private void checkAdminPermission(LoginUser loginUser) {
        if (loginUser == null || (!"SYS_ADMIN".equals(loginUser.getUserType()) && !"DEPT_ADMIN".equals(loginUser.getUserType()))) {
            throw new BusinessException(403, "仅超级管理员或院系管理员有权执行账号管理与批量导入操作");
        }
    }

    private String cleanCell(String val) {
        return val != null ? val.trim() : "";
    }

    public static void validatePasswordStrength(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new BusinessException(400, "密码长度不能少于8位");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        if (!hasUpper) {
            throw new BusinessException(400, "密码必须包含至少一个大写英文字母");
        }
        if (!hasLower) {
            throw new BusinessException(400, "密码必须包含至少一个小写英文字母");
        }
        if (!hasDigit) {
            throw new BusinessException(400, "密码必须包含至少一个数字");
        }
        if (!hasSpecial) {
            throw new BusinessException(400, "密码必须包含至少一个特殊字符(如 !@#$%^&* 等)");
        }
    }

    private String generateSecureTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        sb.append("ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(RANDOM.nextInt(24)));
        sb.append("abcdefghijkmnpqrstuvwxyz".charAt(randomInt(24)));
        sb.append("23456789".charAt(randomInt(8)));
        sb.append("!@#$%".charAt(randomInt(5)));
        for (int i = 4; i < 10; i++) {
            sb.append(TEMP_CHARS.charAt(randomInt(TEMP_CHARS.length())));
        }
        List<Character> list = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) list.add(c);
        Collections.shuffle(list, RANDOM);
        StringBuilder res = new StringBuilder();
        for (char c : list) res.append(c);
        return res.toString();
    }

    private int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    private String maskTarget(String target) {
        if (target.contains("@")) {
            int atIndex = target.indexOf("@");
            return (atIndex > 2 ? target.substring(0, 2) + "***" : "***") + target.substring(atIndex);
        } else if (target.length() >= 7) {
            return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
        }
        return "***";
    }
}
