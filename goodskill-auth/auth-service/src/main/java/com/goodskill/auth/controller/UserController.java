package com.goodskill.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.goodskill.auth.entity.User;
import com.goodskill.auth.pojo.vo.UserInfoVO;
import com.goodskill.auth.service.UserService;
import com.goodskill.core.info.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户管理控制器
 */
@RestController
@Tag(name = "用户管理")
@RequestMapping("/user")
@Validated
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Resource
    private UserService userService;
    @Resource
    private StpInterface stpInterface;
    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * 分页获取用户列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页获取用户列表")
    @SaCheckRole("system-manager")
    public Result<IPage<UserResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<User> pageParam = new Page<>(safePage, safeSize);
        IPage<User> userPage = userService.page(pageParam, keyword);
        Page<UserResponse> responsePage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        responsePage.setRecords(userPage.getRecords().stream().map(UserResponse::from).toList());
        return Result.ok(responsePage);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    @SaCheckRole("system-manager")
    public Result<UserResponse> getUserById(@PathVariable Integer id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(UserResponse.from(user));
    }

    /**
     * 创建新用户
     */
    @PostMapping
    @Operation(summary = "创建新用户")
    @SaCheckRole("system-manager")
    public Result<String> createUser(@RequestBody User user) {
        try {
            userService.createUser(user);
            return Result.ok("创建成功");
        } catch (Exception e) {
            logger.warn("User creation failed ({})", e.getClass().getSimpleName());
            return Result.fail("用户创建失败");
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息")
    @SaCheckRole("system-manager")
    public Result<String> updateUser(@PathVariable Integer id, @RequestBody User user) {
        try {
            StpUtil.logout(id);
            user.setId(id);
            userService.updateUser(user);
            return Result.ok("更新成功");
        } catch (Exception e) {
            logger.warn("User update failed for target {} ({})", id, e.getClass().getSimpleName());
            return Result.fail("用户更新失败");
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @SaCheckRole("system-manager")
    public Result<String> deleteUser(@PathVariable Integer id) {
        try {
            StpUtil.logout(id);
            userService.deleteUser(id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            logger.warn("User deletion failed for target {} ({})", id, e.getClass().getSimpleName());
            return Result.fail("用户删除失败");
        }
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除用户")
    @SaCheckRole("system-manager")
    public Result<String> batchDeleteUsers(@RequestBody @Size(min = 1, max = 100) List<Integer> ids) {
        try {
            ids.forEach(StpUtil::logout);
            userService.batchDeleteUsers(ids);
            return Result.ok("批量删除成功");
        } catch (Exception e) {
            logger.warn("Batch user deletion failed for {} ids ({})", ids.size(), e.getClass().getSimpleName());
            return Result.fail("批量删除用户失败");
        }
    }

    /**
     * 为用户增加角色
     */
    @PostMapping("/role/add")
    @Operation(summary = "为用户增加角色")
    @SaCheckRole("system-manager")
    public Result<String> addRole(@RequestParam("userId") int userId, @RequestParam("roleId") int roleId) {
        try {
            StpUtil.logout(userId);
            userService.addRole(userId, roleId);
            return Result.ok();
        } catch (RuntimeException e) {
            logger.warn("Role assignment failed for target {} ({})", userId, e.getClass().getSimpleName());
            return Result.fail("角色分配失败");
        }
    }

    @GetMapping("/info")
    @Operation(summary = "获取用户信息")
    public Result<?> getUser() {
        Object loginId = StpUtil.getLoginId();
        StpUtil.hasPermission("/test");
        StpUtil.hasRole("test");
        User info = userService.getUserInfoById(loginId.toString());
        List<String> permissionList = StpUtil.getPermissionList();
        List<String> roleList = StpUtil.getRoleList();

        UserInfoVO userInfoVO =  new UserInfoVO();
        userInfoVO.setUser(info);
        userInfoVO.setPermissions(permissionList);
        userInfoVO.setRoles(roleList);
        return Result.ok(userInfoVO);
    }

    @GetMapping("/listUserPermission")
    @Operation(summary = "获取用户权限")
    public List<String> listUserPermission(String loginId, String loginType) {
        return stpInterface.getPermissionList(loginId, loginType);
    }

    @GetMapping("/listUserRole")
    @Operation(summary = "获取用户角色")
    public List<String> listUserRole(String loginId, String loginType) {
        return stpInterface.getRoleList(loginId, loginType);
    }

    @PostMapping("/{id}/password/reset")
    @Operation(summary = "管理员重置用户密码")
    @SaCheckRole("system-manager")
    public Result<String> resetPassword(@PathVariable Integer id,
                                        @Valid @RequestBody PasswordResetRequest request) {
        String actorId = String.valueOf(StpUtil.getLoginId());
        try {
            // Revoke existing sessions first. If the database update fails, forced re-login is safe.
            StpUtil.logout(id);
            if (!userService.resetPassword(id, request.newPassword(), actorId, UUID.randomUUID().toString())) {
                return Result.fail("密码重置失败");
            }
            return Result.ok("密码已重置");
        } catch (RuntimeException e) {
            logger.warn("Password reset failed for target user {} ({})", id, e.getClass().getSimpleName());
            return Result.fail("密码重置失败");
        }
    }

    /**
     * Public representation of a user. Credentials are intentionally excluded.
     */
    public record UserResponse(
            Integer id,
            String account,
            String username,
            Integer locked,
            String avatar,
            LocalDateTime lastLoginTime,
            String mobile,
            String emailAddr,
            Integer deleteFlag) {
        private static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getAccount(), user.getUsername(), user.getLocked(),
                    user.getAvatar(), user.getLastLoginTime(), user.getMobile(), user.getEmailAddr(),
                    user.getDeleteFlag());
        }
    }

    public record PasswordResetRequest(@NotBlank String newPassword) {
    }

}
