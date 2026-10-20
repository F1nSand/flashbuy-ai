package com.goodskill.auth.controller;

import com.goodskill.auth.entity.User;
import com.goodskill.auth.pojo.dto.UserDTO;
import com.goodskill.auth.service.UserService;
import com.goodskill.auth.service.LoginAttemptLimiter;
import com.goodskill.core.info.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {
    private static final String INVALID_CREDENTIALS = "用户名或密码错误";

    @InjectMocks
    private LoginController controller;

    @Mock
    private UserService userService;

    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    @Mock
    private HttpServletResponse servletResponse;

    @Test
    void lockedAccountIsRejectedBeforePasswordCheck() {
        User account = account(1, 0);
        account.setLocked(1);
        when(userService.findByUserAccount("demo")).thenReturn(account);

        Result<String> result = controller.login(loginRequest(), servletResponse);

        assertEquals(Result.FAIL, result.getCode());
        assertEquals(INVALID_CREDENTIALS, result.getMsg());
        verify(userService, never()).checkPassword(account.getPassword(), "secret");
    }

    @Test
    void deletedAccountIsRejectedBeforePasswordCheck() {
        User account = account(0, 1);
        when(userService.findByUserAccount("demo")).thenReturn(account);

        Result<String> result = controller.login(loginRequest(), servletResponse);

        assertEquals(Result.FAIL, result.getCode());
        assertEquals(INVALID_CREDENTIALS, result.getMsg());
        verify(userService, never()).checkPassword(account.getPassword(), "secret");
    }

    @Test
    void blockedAccountIsRejectedBeforeCredentialLookup() {
        org.mockito.Mockito.when(loginAttemptLimiter.isBlocked("demo")).thenReturn(true);

        Result<String> result = controller.login(loginRequest(), servletResponse);

        assertEquals(Result.FAIL, result.getCode());
        assertEquals(INVALID_CREDENTIALS, result.getMsg());
        verifyNoInteractions(userService);
    }

    @Test
    void failedPasswordAddsAnAccountThrottleAttempt() {
        User account = account(0, 0);
        org.mockito.Mockito.when(userService.findByUserAccount("demo")).thenReturn(account);
        org.mockito.Mockito.when(userService.checkPassword(account.getPassword(), "secret")).thenReturn(false);

        Result<String> result = controller.login(loginRequest(), servletResponse);

        assertEquals(Result.FAIL, result.getCode());
        verify(loginAttemptLimiter).recordFailure("demo");
    }

    private static User account(int locked, int deleteFlag) {
        User account = new User();
        account.setId(21);
        account.setAccount("demo");
        account.setPassword("stored-bcrypt-hash");
        account.setLocked(locked);
        account.setDeleteFlag(deleteFlag);
        return account;
    }

    private static UserDTO loginRequest() {
        UserDTO request = new UserDTO();
        request.setUsername("demo");
        request.setPassword("secret");
        return request;
    }
}
