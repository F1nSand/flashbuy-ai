package com.goodskill.auth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.goodskill.auth.entity.User;
import com.goodskill.auth.pojo.dto.UserDTO;
import com.goodskill.auth.service.UserService;
import com.goodskill.auth.service.LoginAttemptLimiter;
import com.goodskill.core.info.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 登录测试
 */
@RestController
@Tag(name = "登录测试")
public class LoginController {
    @Resource
    private UserService userService;
    @Resource
    private LoginAttemptLimiter loginAttemptLimiter;

    @Value("${goodskill.auth.browser-cookie-session:false}")
    private boolean browserCookieSession;

    @Value("${sa-token.token-name:access_token}")
    private String tokenName;

    /**
     * 测试登录
     *
     * @return
     */
    @RequestMapping("/isLogin")
    @Operation(summary = "测试登录")
    public Result<String> isLogin(HttpServletRequest request) {
        return Result.ok("是否登录：" + StpUtil.isLogin());
    }

    @RequestMapping("/tokenInfo")
    @Operation(summary = "获取token信息")
    public Result<SaTokenInfo> tokenInfo() {
        return Result.ok(StpUtil.getTokenInfo());
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<String> logout(HttpServletResponse response) {
        StpUtil.logout();
        if (browserCookieSession) {
            response.addHeader("Set-Cookie", ResponseCookie.from(tokenName, "")
                    .httpOnly(true).secure(true).sameSite("Lax").path("/")
                    .maxAge(Duration.ZERO).build().toString());
        }
        return Result.ok();
    }

    @PostMapping("/register")
    @Operation(summary = "注册")
    public Result<String> register(@Valid @RequestBody UserDTO userDTO) {
        userService.register(userDTO);
        return Result.ok("注册成功");
    }

    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<String> login(@Valid @RequestBody UserDTO userDTO, HttpServletResponse response) {
        if (loginAttemptLimiter.isBlocked(userDTO.getUsername())) {
            return Result.fail("用户名或密码错误");
        }
        User userAccount = userService.findByUserAccount(userDTO.getUsername());
        if (userAccount != null && !isDisabled(userAccount)) {
            // 校验账户与用户输入的密码是否一致
            boolean b = userService.checkPassword(userAccount.getPassword(), userDTO.getPassword());
            if (!b) {
                loginAttemptLimiter.recordFailure(userDTO.getUsername());
                return Result.fail("用户名或密码错误");
            }
            StpUtil.login(userAccount.getId());
            loginAttemptLimiter.clear(userDTO.getUsername());
            String tokenValue = StpUtil.getTokenInfo().getTokenValue();
            if (browserCookieSession) {
                response.addHeader("Set-Cookie", ResponseCookie.from(tokenName, tokenValue)
                        .httpOnly(true).secure(true).sameSite("Lax").path("/")
                        .maxAge(Duration.ofDays(7)).build().toString());
                return Result.ok("登录成功");
            }
            return Result.ok(tokenValue);
        }
        loginAttemptLimiter.recordFailure(userDTO.getUsername());
        return Result.fail("用户名或密码错误");
    }

    private boolean isDisabled(User user) {
        return (user.getLocked() != null && user.getLocked() != 0)
                || (user.getDeleteFlag() != null && user.getDeleteFlag() != 0);
    }

}
