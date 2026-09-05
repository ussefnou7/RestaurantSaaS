package com.smart.restaurant_saas.auth.security;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.common.ApiErrorResponse;
import com.smart.restaurant_saas.user.repository.AuthenticatedAccount;
import com.smart.restaurant_saas.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
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
            reject(request, response, AuthErrorCode.TOKEN_INVALID, "Authorization header is not a Bearer token");
            return;
        }

        CurrentUserPrincipal tokenPrincipal;
        try {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            tokenPrincipal = jwtService.parseToken(token);
        } catch (ExpiredJwtException ex) {
            reject(request, response, AuthErrorCode.TOKEN_EXPIRED, "Token expired");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            // Malformed, bad signature, or an unparseable claim. One code: see TOKEN_INVALID.
            reject(request, response, AuthErrorCode.TOKEN_INVALID, "Token rejected: " + ex.getClass().getSimpleName());
            return;
        }

        Optional<AuthenticatedAccount> found = Optional.ofNullable(tokenPrincipal.userId())
                .flatMap(userRepository::findAccountForAuthentication);

        if (found.isEmpty()) {
            reject(request, response, AuthErrorCode.USER_INACTIVE,
                    "No account for user id " + tokenPrincipal.userId());
            return;
        }

        AuthenticatedAccount account = found.get();

        if (!account.isUserActive()) {
            // Which status it was is logged, never returned — see AuthErrorCode.USER_INACTIVE.
            reject(request, response, AuthErrorCode.USER_INACTIVE,
                    "User " + account.userId() + " status is " + account.status());
            return;
        }

        if (!account.isRoleActive()) {
            reject(request, response, AuthErrorCode.ROLE_INACTIVE,
                    "User " + account.userId() + " holds deactivated role " + account.roleCode());
            return;
        }

        authenticate(tokenPrincipal, account);
        filterChain.doFilter(request, response);
    }

    /**
     * Builds the principal with the <strong>database-resolved</strong> role code, not the token's
     * {@code roleCode} claim.
     *
     * <p>This is the whole of the live-role change in one line. Every role-level helper —
     * {@code SecurityService.isSysAdmin/isOwner/isOwnerOrBranchManager},
     * {@code CurrentTenantProvider.isSysAdmin}, {@code CurrentUserScopeProvider},
     * {@code CurrentUserService.getCurrentRoleCode} — reads
     * {@code CurrentUserPrincipal.roleCode()}. Stamping the live value here makes all of them live
     * at once, including the 216 {@code @PreAuthorize} SpEL gates that call them, without editing
     * a single one. A revoked role previously survived until the token expired, and
     * {@code isSysAdmin()} was the one path in the system that returned true with no repository
     * call at all — bypassing every gate, from the one snapshot nobody was re-reading.
     *
     * <p>Not a cache. The role is read from the database on every request; within a single request
     * its value cannot change. The rejected design was caching <em>across</em> requests.
     */
    private void authenticate(CurrentUserPrincipal tokenPrincipal, AuthenticatedAccount account) {
        String liveRoleCode = account.roleCode().name();

        if (!liveRoleCode.equals(tokenPrincipal.roleCode())) {
            log.info("Role changed since token was issued for user {}: token={} live={}",
                    account.userId(), tokenPrincipal.roleCode(), liveRoleCode);
        }

        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                tokenPrincipal.userId(),
                tokenPrincipal.tenantId(),
                tokenPrincipal.username(),
                liveRoleCode);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(liveRoleCode))));
    }

    /**
     * The single exit for every rejection this filter makes.
     *
     * <p>Filter-thrown failures never reach {@code GlobalExceptionHandler} — it is a
     * {@code @RestControllerAdvice} and only wraps handler invocation — so the structured body is
     * written here to keep the error contract identical to every other endpoint. Before this,
     * only {@code USER_INACTIVE} carried an {@code errorCode} because it alone had been
     * hand-written; expiry, which every user hits daily under a 24h lifetime, emitted a bare
     * container error page the frontend could not distinguish from any other failure, so it could
     * not sign the user out and showed an unexplained error instead.
     *
     * <p>Routing every branch through one method is deliberate: three hand-written bodies is how
     * the fourth branch ships without one.
     *
     * @param detail English, logs only — never surfaced. The response carries the code alone.
     */
    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthErrorCode errorCode,
            String detail
    ) throws IOException {
        SecurityContextHolder.clearContext();
        log.warn("Rejected request to {}: {} ({})", request.getRequestURI(), errorCode.getCode(), detail);

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
