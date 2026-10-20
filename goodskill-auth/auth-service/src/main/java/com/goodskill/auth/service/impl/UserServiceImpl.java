package com.goodskill.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodskill.auth.entity.*;
import com.goodskill.auth.mapper.*;
import com.goodskill.auth.pojo.dto.UserDTO;
import com.goodskill.auth.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author heng
 * @since 2019-09-07
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private RolePermissionMapper rolePermissionMapper;
    @Resource
    private PermissionMapper permissionMapper;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private UserMapper baseMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private Environment environment;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void register(UserDTO userDTO) {
        validateProductionPassword(userDTO.getPassword());
        if (findByUserAccount(userDTO.getUsername()) != null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        User user = new User();
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setUsername(userDTO.getUsername());
        user.setAccount(userDTO.getUsername());
        baseMapper.insert(user);

        Role buyerRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "buyer"));
        if (buyerRole == null) {
            if (environment.acceptsProfiles(Profiles.of("production"))) {
                throw new IllegalStateException("Required buyer role is not configured");
            }
            return;
        }
        UserRole assignment = new UserRole();
        assignment.setUserId(user.getId());
        assignment.setRoleId(buyerRole.getRoleId());
        userRoleMapper.insert(assignment);
    }

    @Override
    public Set<Role> findRoles(String account) {
        User entity = new User();
        entity.setAccount(account);
        List<User> users = baseMapper.selectList(new QueryWrapper(entity));
        Set<Role> roles = new HashSet<>();
        if (CollectionUtils.hasUniqueObject(users)) {
            User user = users.getFirst();

            UserRole role = new UserRole();
            role.setUserId(user.getId());
            List<UserRole> userRoles = userRoleMapper.selectList(new QueryWrapper(role));
            for (UserRole userRole : userRoles) {
                roles.add(roleMapper.selectById(userRole.getRoleId()));
            }
        }
        return roles;
    }

    @Override
    public Set<Permission> findPermissions(String account) {
        Permission permission;
        Set<Role> roles = findRoles(account);
        Set<Permission> permissions = new HashSet<>();
        for (Role role : roles) {
            RolePermission rolePermission1 = new RolePermission();
            rolePermission1.setRoleId(role.getRoleId());
            List<RolePermission> rolePermissions = rolePermissionMapper.selectList(new QueryWrapper<>(rolePermission1));
            for (RolePermission rolePermission : rolePermissions) {
                permission = permissionMapper.selectById(rolePermission.getPermissionId());
                permissions.add(permission);
            }
        }
        return permissions;
    }

    @Override
    public User findByUserAccount(String userAccount) {
        User user = new User();
        user.setAccount(userAccount);
        List<User> userList = baseMapper.selectList(new LambdaQueryWrapper<>(user));
        if (CollectionUtils.hasUniqueObject(userList)) {
            return userList.getFirst();
        } else {
            return null;
        }
    }

    @Override
    public User getUserInfoById(String userId) {
        User user = baseMapper.selectById(userId);
        user.setPassword(null);
        return user;
    }

    @Override
    public IPage<User> page(Page<User> page) {
        return super.page(page);
    }

    @Override
    public boolean removeById(int userId) {
        return super.removeById(userId);
    }

    @Override
    public boolean updateLastLoginTime(Integer id) {
        User user = new User();
        user.setId(id);
        user.setLastLoginTime(LocalDateTime.now());
        return this.updateById(user);
    }

    @Override
    public boolean checkPassword(String password, String passwordInput) {
        return passwordEncoder.matches(passwordInput, password);
    }

    @Override
    public boolean addRole(int userId, int roleId) {
        // 校验角色id是否存在
        if (roleMapper.selectById(roleId) != null) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            return userRoleMapper.insert(userRole) > 0;
        } else {
            throw new RuntimeException("角色id不存在");
        }
    }

    @Override
    public IPage<User> page(Page<User> page, String keyword) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.like(User::getUsername, keyword)
                    .or()
                    .like(User::getAccount, keyword)
                    .or()
                    .like(User::getEmailAddr, keyword);
        }
        queryWrapper.orderByDesc(User::getCreateTime);
        return super.page(page, queryWrapper);
    }

    @Override
    public User getUserById(Integer id) {
        User user = baseMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public boolean createUser(User user) {
        // 检查用户名是否已存在
        User existUser = findByUserAccount(user.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 加密密码
        validateProductionPassword(user.getPassword());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setAccount(user.getUsername());
        return baseMapper.insert(user) > 0;
    }

    @Override
    public boolean updateUser(User user) {
        User existUser = baseMapper.selectById(user.getId());
        if (existUser == null) {
            throw new RuntimeException("用户不存在");
        }
        // 如果修改了用户名，检查是否重复
        if (!existUser.getUsername().equals(user.getUsername())) {
            User checkUser = findByUserAccount(user.getUsername());
            if (checkUser != null) {
                throw new RuntimeException("用户名已存在");
            }
        }
        // 不更新密码字段
        user.setPassword(null);
        user.setAccount(user.getUsername());
        return baseMapper.updateById(user) > 0;
    }

    @Override
    @Transactional
    public boolean resetPassword(Integer userId, String rawPassword, String actorId, String requestId) {
        validateProductionPassword(rawPassword);
        if (environment.acceptsProfiles(Profiles.of("production"))
                && (!StringUtils.hasText(actorId) || !StringUtils.hasText(requestId))) {
            throw new IllegalArgumentException("Administrator identity and request id are required for password reset");
        }
        User existing = baseMapper.selectById(userId);
        if (existing == null) {
            return false;
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(rawPassword));
        if (baseMapper.updateById(update) != 1) {
            throw new IllegalStateException("Password update did not affect exactly one user");
        }
        if (environment.acceptsProfiles(Profiles.of("production"))) {
            jdbcTemplate.update("INSERT INTO auth_admin_audit_event "
                            + "(actor_id, action, target_user_id, request_id, result) VALUES (?, ?, ?, ?, ?)",
                    actorId, "USER_PASSWORD_RESET", userId, requestId, "SUCCESS");
        }
        return true;
    }

    @Override
    public boolean deleteUser(Integer id) {
        User existUser = baseMapper.selectById(id);
        if (existUser == null) {
            throw new RuntimeException("用户不存在");
        }
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    public boolean batchDeleteUsers(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return baseMapper.deleteBatchIds(ids) == ids.size();
    }

    private void validateProductionPassword(String password) {
        if (!environment.acceptsProfiles(Profiles.of("production"))) {
            return;
        }
        if (password == null || password.length() < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "密码至少需要 15 个字符，且 UTF-8 编码不能超过 72 字节");
        }
    }
}
