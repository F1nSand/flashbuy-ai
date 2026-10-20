package com.goodskill.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodskill.auth.entity.User;
import com.goodskill.auth.service.UserService;
import com.goodskill.core.info.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    @InjectMocks
    private UserController controller;

    @Mock
    private UserService userService;

    @Test
    void listClampsPageBoundsAndDoesNotSerializePassword() throws Exception {
        User user = userWithPassword();
        Page<User> servicePage = new Page<>(1, 100, 1);
        servicePage.setRecords(List.of(user));
        when(userService.page(ArgumentMatchers.<Page<User>>any(), eq("demo"))).thenReturn(servicePage);

        Result<IPage<UserController.UserResponse>> result = controller.list(-4, 500, "demo");

        ArgumentCaptor<Page<User>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(userService).page(pageCaptor.capture(), eq("demo"));
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(100, pageCaptor.getValue().getSize());
        String json = new ObjectMapper().writeValueAsString(result.getData());
        assertFalse(json.contains("password"));
        assertFalse(json.contains("sensitive-password-hash"));
        assertTrue(json.contains("demo-account"));
    }

    @Test
    void listClampsNonPositivePageAndSizeToOne() {
        Page<User> servicePage = new Page<>(1, 1, 0);
        servicePage.setRecords(List.of());
        when(userService.page(ArgumentMatchers.<Page<User>>any(), ArgumentMatchers.<String>eq(null)))
                .thenReturn(servicePage);

        controller.list(0, -2, null);

        ArgumentCaptor<Page<User>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(userService).page(pageCaptor.capture(), ArgumentMatchers.<String>eq(null));
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(1, pageCaptor.getValue().getSize());
    }

    @Test
    void detailReturnsSafeDtoWithoutPassword() throws Exception {
        when(userService.getUserById(7)).thenReturn(userWithPassword());

        Result<UserController.UserResponse> result = controller.getUserById(7);

        String json = new ObjectMapper().writeValueAsString(result.getData());
        assertFalse(json.contains("password"));
        assertFalse(json.contains("sensitive-password-hash"));
        assertEquals(7, result.getData().id());
    }

    @Test
    void userEntityNeverSerializesPasswordEvenOnOtherResponses() throws Exception {
        String json = new ObjectMapper().writeValueAsString(userWithPassword());

        assertFalse(json.contains("password"));
        assertFalse(json.contains("sensitive-password-hash"));
    }

    @Test
    void userAdminMethodsRequireSystemManagerButSelfAndGatewayLookupsDoNot() throws Exception {
        for (String methodName : List.of("list", "getUserById", "createUser", "updateUser", "deleteUser",
                "batchDeleteUsers", "addRole", "resetPassword")) {
            SaCheckRole role = UserController.class.getMethod(methodName, parameterTypes(methodName)).getAnnotation(SaCheckRole.class);
            assertNotNull(role, methodName);
            assertTrue(List.of(role.value()).contains("system-manager"), methodName);
        }

        assertNull(UserController.class.getMethod("getUser").getAnnotation(SaCheckRole.class));
        assertNull(UserController.class.getMethod("listUserPermission", String.class, String.class).getAnnotation(SaCheckRole.class));
        assertNull(UserController.class.getMethod("listUserRole", String.class, String.class).getAnnotation(SaCheckRole.class));
    }

    private static Class<?>[] parameterTypes(String methodName) {
        return switch (methodName) {
            case "list" -> new Class<?>[]{int.class, int.class, String.class};
            case "getUserById", "deleteUser" -> new Class<?>[]{Integer.class};
            case "createUser" -> new Class<?>[]{User.class};
            case "updateUser" -> new Class<?>[]{Integer.class, User.class};
            case "batchDeleteUsers" -> new Class<?>[]{List.class};
            case "addRole" -> new Class<?>[]{int.class, int.class};
            case "resetPassword" -> new Class<?>[]{Integer.class, UserController.PasswordResetRequest.class};
            default -> throw new IllegalArgumentException(methodName);
        };
    }

    private static User userWithPassword() {
        User user = new User();
        user.setId(7);
        user.setAccount("demo-account");
        user.setUsername("Demo User");
        user.setPassword("sensitive-password-hash");
        user.setLocked(0);
        user.setDeleteFlag(0);
        return user;
    }
}
