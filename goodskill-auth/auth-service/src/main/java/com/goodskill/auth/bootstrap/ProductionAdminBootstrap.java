package com.goodskill.auth.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.goodskill.auth.entity.Role;
import com.goodskill.auth.entity.User;
import com.goodskill.auth.entity.UserRole;
import com.goodskill.auth.mapper.RoleMapper;
import com.goodskill.auth.mapper.UserMapper;
import com.goodskill.auth.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Creates the first production administrator only from a one-time deployment secret.
 * The secret must be removed after the first successful start.
 */
@Component
@Profile("production")
public class ProductionAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionAdminBootstrap.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String username;
    private final String password;

    public ProductionAdminBootstrap(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            @Value("${goodskill.auth.bootstrap-admin.username:}") String username,
            @Value("${goodskill.auth.bootstrap-admin.password:}") String password) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> bootstrap());
    }

    private void bootstrap() {
        boolean usernameProvided = StringUtils.hasText(username);
        boolean passwordProvided = StringUtils.hasText(password);
        if (usernameProvided != passwordProvided) {
            throw new IllegalStateException("Both one-time administrator bootstrap values must be provided together");
        }

        Role systemManager = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "system-manager")
                .eq(Role::getStatus, 1)
                .eq(Role::getDeleteFlag, 0)
                .last("FOR UPDATE"));
        if (systemManager == null) {
            throw new IllegalStateException("Production system-manager role is missing or disabled");
        }

        List<UserRole> assignments = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, systemManager.getRoleId()));
        if (!assignments.isEmpty()) {
            boolean activeAdministratorExists = assignments.stream()
                    .map(UserRole::getUserId)
                    .map(userMapper::selectById)
                    .anyMatch(ProductionAdminBootstrap::isActive);
            if (!activeAdministratorExists) {
                throw new IllegalStateException(
                        "System-manager assignments exist but none are active; recover access manually before startup");
            }
            if (usernameProvided) {
                throw new IllegalStateException(
                        "A system manager already exists; remove the one-time administrator bootstrap values");
            }
            return;
        }

        if (userMapper.selectCount(null) != 0) {
            throw new IllegalStateException(
                    "Production identity database is not empty and has no system manager; refusing automatic promotion");
        }
        validateCredentials(username, password);

        User administrator = new User();
        administrator.setAccount(username);
        administrator.setUsername(username);
        administrator.setPassword(passwordEncoder.encode(password));
        administrator.setLocked(0);
        administrator.setDeleteFlag(0);
        if (userMapper.insert(administrator) != 1 || administrator.getId() == null) {
            throw new IllegalStateException("Failed to create the initial production administrator");
        }

        UserRole assignment = new UserRole();
        assignment.setUserId(administrator.getId());
        assignment.setRoleId(systemManager.getRoleId());
        if (userRoleMapper.insert(assignment) != 1) {
            throw new IllegalStateException("Failed to assign the initial production administrator role");
        }
        log.info("Created the initial production system manager with user id {}", administrator.getId());
    }

    static void validateCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || username.length() > 120) {
            throw new IllegalStateException("Bootstrap administrator username must contain 1 to 120 characters");
        }
        if (password == null || password.length() < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Bootstrap administrator password must be at least 15 characters and at most 72 UTF-8 bytes");
        }
    }

    private static boolean isActive(User user) {
        return user != null && (user.getLocked() == null || user.getLocked() == 0)
                && (user.getDeleteFlag() == null || user.getDeleteFlag() == 0);
    }
}
