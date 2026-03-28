package com.school.supervision.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@Component
public class TenantJwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public TenantJwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Claims claims = jwtService.parse(token);
                    String username = claims.getSubject();
                    String orgId = claims.get("organization_id", String.class);
                    setTenantOrganizationId(UUID.fromString(orgId));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (JwtException | IllegalArgumentException ignored) {
                    // Expired, malformed, or wrong signature — continue unauthenticated; secured routes return 401/403.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            clearTenantContext();
        }
    }

    private void setTenantOrganizationId(UUID organizationId) {
        invokeTenantContextMethod("setOrganizationId", new Class<?>[]{UUID.class}, new Object[]{organizationId});
    }

    private void clearTenantContext() {
        invokeTenantContextMethod("clear", new Class<?>[]{}, new Object[]{});
    }

    private void invokeTenantContextMethod(String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class<?> tenantContextClass = Class.forName("com.school.supervision.common.tenant.TenantContext");
            Method method = tenantContextClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (Exception ignored) {
            // Avoid failing auth flow if classloading issues occur during dev runtime.
        }
    }
}
