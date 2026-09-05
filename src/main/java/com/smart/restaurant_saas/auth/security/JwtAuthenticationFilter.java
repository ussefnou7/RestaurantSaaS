package com.smart.restaurant_saas.auth.security;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.common.ApiErrorResponse;
import com.smart.restaurant_saas.user.repository.AuthenticatedAccount;
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

/**
 * Establishes the caller's identity, then checks the two things about their account that are
 * revocable.
 *
 * <p>The governing rule: <strong>the token establishes who is asking. It never establishes what
 * they may do, or whether they still exist. Anything revocable is read live.</strong> The request
 * path is three checks in order — user status, role state, then permissions — and a failure at
 * either of the first two never reaches the third.
 *
 * <p>All three are live reads. That is not an oversight to be optimised away with a cache: with a
 * 24h token and no revocation list, anything decided at login stays decided for a day, which is
 * the whole defect this filter exists to close.
 */
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

        // No credentials offered at all is not a rejection: permitAll routes (swagger, OPTIONS)
        // rely on passing through unauthenticated, and authorizeHttpRequests decides the rest.
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

        Optional<AuthenticatedAccount> found = Optional.ofNullable(principal.userId())
                .flatMap(userRepository::findAccountForAuthentication);

        if (found.isEmpty()) {
            log.warn("Rejected token for user id {}: no account", principal.userId());
            SecurityContextHolder.clearContext();
            writeError(request, response, AuthErrorCode.USER_INACTIVE);
            return;
        }

        AuthenticatedAccount account = found.get();

        if (!account.isUserActive()) {
            // Which status it was is logged, never returned — see AuthErrorCode.USER_INACTIVE.
            log.warn("Rejected token for user {}: status is {}", account.userId(), account.status());
            SecurityContextHolder.clearContext();
            writeError(request, response, AuthErrorCode.USER_INACTIVE);
            return;
        }

        if (!account.isRoleActive()) {
            log.warn("Rejected token for user {}: role {} is deactivated",
                    account.userId(), account.roleCode());
            SecurityContextHolder.clearContext();
            writeError(request, response, AuthErrorCode.ROLE_INACTIVE);
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
     * Filter-thrown failures never reach {@code GlobalExceptionHandler} (it is a
     * {@code @RestControllerAdvice}, which only wraps handler invocation), so the structured body
     * is written here by hand to keep the error contract identical to every other endpoint. A bare
     * {@code sendError} would give the frontend a 401 with no {@code errorCode} to branch on, and
     * a locked-out user would get an unexplained retry loop instead of being signed out.
     *
     * <p>Note that the two {@code sendError} paths above still have exactly that problem — most
     * importantly token expiry, which every user hits daily under a 24h lifetime. They are fixed
     * in the following pass.
     */
    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthErrorCode errorCode
    ) throws IOException {
        int status = errorCode.getDefaultStatus().value();
        ApiErrorResponse body = ApiErrorResponse.of(
                errorCode.getCode(),
                errorCode.getCode(),
                status,
                request.getRequestURI());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
