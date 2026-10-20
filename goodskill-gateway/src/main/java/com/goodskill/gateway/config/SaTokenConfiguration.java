package com.goodskill.gateway.config;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.util.SaResult;
import com.goodskill.gateway.properties.IgnoreProperties;
import com.goodskill.gateway.security.ProductionRouteAccessPolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * [Sa-Token 权限认证] 全局配置类
 */
@Configuration
@Slf4j
public class SaTokenConfiguration {
    @Resource
    private IgnoreProperties ignoreProperties;
    @Resource
    private Environment environment;

    private final ProductionRouteAccessPolicy productionRouteAccessPolicy = new ProductionRouteAccessPolicy();

    /**
     * 注册 [Sa-Token全局过滤器]，优先级高于GlobalFilter，即在GlobalFilter之前执行
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 指定 [拦截路由]
                .addInclude("/**")    /* 拦截所有path */
                // 指定 [放行路由]
                .addExclude("/favicon.ico", "/actuator/health")
                // 指定[认证函数]: 每次请求执行
                .setAuth(obj -> {
                    ServerHttpRequest request = SaReactorSyncHolder.getContext().getRequest();
                    RequestPath path = request.getPath();
                    if (environment.acceptsProfiles(Profiles.of("production"))) {
                        authorizeProductionRoute(request, path.value());
                    } else {
                        SaRouter.notMatch(ignoreProperties.getWhiteUrl())
                                .check(r -> StpUtil.checkLogin());
                    }
                })
                // 指定[异常处理函数]：每次[认证函数]发生异常时执行此函数
                .setError(e -> {
                    if (environment.acceptsProfiles(Profiles.of("production"))) {
                        log.warn("Gateway authorization rejected request: {}", e.getClass().getSimpleName());
                        return SaResult.error("Forbidden");
                    }
                    log.error("Gateway authentication failed", e);
                    return SaResult.error(e.getMessage());
                });
    }

    private void authorizeProductionRoute(ServerHttpRequest request, String path) {
        HttpMethod method = request.getMethod();
        if (method == null) {
            throw new SaTokenException("HTTP method is required");
        }
        if (productionRouteAccessPolicy.isDisabled(path)) {
            throw new SaTokenException("Route is disabled in production");
        }
        if (productionRouteAccessPolicy.isAnonymousAllowed(method, path)) {
            return;
        }
        StpUtil.checkLogin();
        var requiredRoles = productionRouteAccessPolicy.requiredAnyRole(method, path)
                .orElseThrow(() -> new SaTokenException("Route is not enabled in production"));
        if (!requiredRoles.isEmpty()) {
            StpUtil.checkRoleOr(requiredRoles.toArray(String[]::new));
        }
    }
}
