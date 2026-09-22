package com.college.internship.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security 用户认证详情加载实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, 0));

        if (user == null) {
            log.warn("登录失败，用户不存在: {}", username);
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            log.warn("登录失败，账号被禁用: {}", username);
            throw new UsernameNotFoundException("该账号已被停用，请联系管理员");
        }

        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());

        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .realName(user.getRealName())
                .userType(user.getUserType())
                .deptId(user.getDeptId())
                .tokenVersion(user.getTokenVersion())
                .permissions(roles)
                .build();
    }
}
