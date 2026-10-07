package com.smart.restaurant_saas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * L043: exercise every affected controller body with the real authenticated actor provider.
 * The fixed endpoint list survives removal of a protection from any individual controller.
 * HTTP binding and persisted document/ledger attribution are covered by the integration test.
 */
class ControllerActorAttributionTest {
    private static final Long ACTOR = 918_101L;
    private static final Long UNTRUSTED_ID = 918_999L;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0}: service receives authenticated actor")
    @MethodSource("endpoints")
    void forwardsAuthenticatedActor(String endpoint) throws Exception {
        var principal = new CurrentUserPrincipal(ACTOR, 7L, "actor", "OWNER", null, false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        String[] parts = endpoint.split("#");
        Class<?> type = Class.forName(parts[0]);
        List<Object> services = new ArrayList<>();
        var constructor = type.getConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(dependency -> {
            if (dependency == CurrentTenantProvider.class) {
                return new CurrentTenantProvider(null, null);
            }
            Object service = mock(dependency);
            services.add(service);
            return service;
        }).toArray();
        Object controller = constructor.newInstance(dependencies);
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(parts[1])).findFirst().orElseThrow();
        // Any id supplied as an argument is deliberately different from the authenticated actor.
        Object[] arguments = Arrays.stream(method.getParameterTypes())
                .map(parameter -> parameter == Long.class ? UNTRUSTED_ID
                        : parameter.isEnum() ? parameter.getEnumConstants()[0] : mock(parameter)).toArray();
        method.invoke(controller, arguments);

        var calls = services.stream().flatMap(service -> mockingDetails(service).getInvocations().stream())
                .toList();
        assertThat(calls).as(endpoint + " must invoke its operation service").hasSize(1);
        Object[] forwarded = calls.getFirst().getArguments();
        int actorIndex = type.getSimpleName().equals("MediaController") ? 1 : forwarded.length - 1;
        assertThat(forwarded[actorIndex]).as(endpoint + " actor").isEqualTo(ACTOR);
        assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null)
                .anyMatch(annotation -> "X-User-Id".equalsIgnoreCase(annotation.value())
                        || "X-User-Id".equalsIgnoreCase(annotation.name())))
                .as(endpoint + " must not bind a client-supplied actor").isFalse();

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> method.invoke(controller, arguments)).hasRootCauseInstanceOf(ApiException.class);
        assertThat(services.stream().flatMap(service -> mockingDetails(service).getInvocations().stream()))
                .as("Unauthenticated invocation must not reach the service").hasSize(1);
    }

    @Test
    void noControllerAcceptsAnActorHeaderIncludingNewEndpoints() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var controllers = scanner.findCandidateComponents("com.smart.restaurant_saas");
        assertThat(controllers).isNotEmpty();
        for (var controller : controllers) {
            for (Method method : Class.forName(controller.getBeanClassName()).getDeclaredMethods()) {
                for (var parameter : method.getParameters()) {
                    var header = parameter.getAnnotation(RequestHeader.class);
                    if (header != null) {
                        assertThat(List.of(header.value().toLowerCase(java.util.Locale.ROOT),
                                header.name().toLowerCase(java.util.Locale.ROOT)))
                                .as(method.toString()).doesNotContain("x-user-id");
                    }
                }
            }
        }
    }

    private static Stream<String> endpoints() throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(
                ControllerActorAttributionTest.class.getResourceAsStream("/actor-attribution-endpoints.txt"),
                StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList().stream();
        }
    }
}
