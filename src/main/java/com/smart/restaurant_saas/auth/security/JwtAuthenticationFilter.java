package com.smart.restaurant_saas.auth.security;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.common.ApiErrorResponse;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * These are the same routes SecurityConfig marks permitAll(). Without this, a
     * present-but-stale/invalid token (leftover from a prior session) makes this
     * filter reject the request with 401 before authorizeHttpRequests ever gets a
     * chance to apply permitAll — the two endpoints that establish identity can't
     * be allowed to depend on already holding a valid one.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());
        return isPost && ("/api/auth/login".equals(path) || "/api/devices/login".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Authorization header");
            return;
        }

        CurrentUserPrincipal principal;
        try {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            principal = jwtService.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        if (!isAccountStillUsable(principal)) {
            SecurityContextHolder.clearContext();
            writeUserInactive(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.roleCode()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * The token says who is asking; it does not say whether they still exist. Account state is
     * revocable, so it is read live on every request rather than trusted from the claims — with a
     * 24h token, a snapshot taken at login means a dismissed employee keeps access for up to a day.
     *
     * <p>This belongs here rather than in the permission query because not every endpoint carries
     * a {@code @PreAuthorize} — anything gated by authentication alone would otherwise stay open.
     * One indexed lookup by id is the same cost class as the permission query already accepted as
     * live.
     *
     * <p>Looked up by id alone, not by (id, tenantId): the tenant is itself derived from the
     * principal, and resolving it here would make account validity depend on tenant resolution
     * which in turn depends on the role claim. Identity is the more primitive question and is
     * answered first.
     */
    private boolean isAccountStillUsable(CurrentUserPrincipal principal) {
        Optional<User> user = Optional.ofNullable(principal.userId()).flatMap(userRepository::findById);

        if (user.isEmpty()) {
            log.warn("Rejected token for user id {}: no such user", principal.userId());
            return false;
        }

        UserStatus status = user.get().getStatus();
        if (status != UserStatus.ACTIVE) {
            // Status is logged, never returned — see AuthErrorCode.USER_INACTIVE.
            log.warn("Rejected token for user id {}: status is {}", principal.userId(), status);
            return false;
        }

        return true;
    }

    /**
     * Filter-thrown failures never reach {@code GlobalExceptionHandler} (it is a
     * {@code @RestControllerAdvice}, which only wraps handler invocation), so the structured body
     * is written here by hand to keep the error contract identical to every other endpoint. A bare
     * {@code sendError} would give the frontend a 401 with no {@code errorCode} to branch on, and
     * a disabled user would get an unexplained retry loop instead of being signed out.
     */
    private void writeUserInactive(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(
                AuthErrorCode.USER_INACTIVE.getCode(),
                "Account is not active",
                HttpServletResponse.SC_UNAUTHORIZED,
                request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
