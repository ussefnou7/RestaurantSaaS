package com.smart.restaurant_saas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * L006: execute Spring's method-security advice against a fixed contract for all 238 audited gates.
 * Controller bodies are mocked so validation, tenant lookup, or service failures cannot mask a
 * missing guard. Existing MVC tests cover HTTP wiring. The contract must not be derived from the
 * live annotations: deleting a guard must leave its test intact.
 */
class ControllerAuthorizationContractTest {

    private static final List<Gate> GATES = readContract();
    private static final Set<String> PERMISSIONS = GATES.stream()
            .flatMap(gate -> gate.permissions().stream()).collect(Collectors.toSet());

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0}: deny unauthorized callers")
    @MethodSource("gates")
    void rejectsUnauthorizedCallers(Gate gate) throws Throwable {
        Fixture fixture = fixture(gate, Class.forName(gate.controller()));
        authenticate(!gate.authenticatedOnly());
        assertDenied(fixture);

        if (!gate.authenticatedOnly()) {
            // A different real permission must not accidentally grant this operation.
            for (String permission : PERMISSIONS) {
                if (!gate.permissions().contains(permission)) {
                    when(fixture.security().hasPermission(permission)).thenReturn(true);
                    assertDenied(fixture);
                    when(fixture.security().hasPermission(permission)).thenReturn(false);
                }
            }
        }
        assertThat(mockingDetails(fixture.target()).getInvocations()).isEmpty();
    }

    @ParameterizedTest(name = "{0}: allow intended callers")
    @MethodSource("gates")
    void allowsIntendedCallers(Gate gate) throws Throwable {
        Fixture fixture = fixture(gate, Class.forName(gate.controller()));
        authenticate(true);
        int expectedInvocations = 0;
        for (String permission : gate.permissions()) {
            when(fixture.security().hasPermission(permission)).thenReturn(true);
            fixture.invoke();
            expectedInvocations++;
            when(fixture.security().hasPermission(permission)).thenReturn(false);
        }
        if (gate.authenticatedOnly()) {
            fixture.invoke();
            expectedInvocations++;
        }
        when(fixture.security().isSysAdmin()).thenReturn(true);
        if (gate.sysadmin() || gate.authenticatedOnly()) {
            fixture.invoke();
            expectedInvocations++;
        } else {
            assertDenied(fixture);
        }
        assertThat(expectedInvocations).isPositive();
        assertThat(mockingDetails(fixture.target()).getInvocations()).hasSize(expectedInvocations);
    }

    @ParameterizedTest(name = "{0}: removing this gate admits a forbidden caller")
    @MethodSource("gates")
    void independentlyRemovingEachGuardBreaksItsDenialContract(Gate gate) throws Throwable {
        Class<?> mutated = withoutGuard(gate);
        Fixture fixture = fixture(gate, mutated);
        authenticate(!gate.authenticatedOnly());
        // Same caller and invocation as the denial test, with only this annotation removed.
        fixture.invoke();
        assertThat(mockingDetails(fixture.target()).getInvocations()).hasSize(1);
    }

    private static void assertDenied(Fixture fixture) {
        assertThatThrownBy(fixture::invoke).isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(boolean authenticated) {
        var authorities = AuthorityUtils.createAuthorityList("ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(authenticated
                ? new UsernamePasswordAuthenticationToken("audit-user", "unused", authorities)
                : new AnonymousAuthenticationToken("audit-key", "anonymousUser", authorities));
    }

    private static Fixture fixture(Gate gate, Class<?> type) {
        SecurityService security = mock(SecurityService.class);
        var beans = new StaticApplicationContext();
        beans.getBeanFactory().registerSingleton("securityService", security);
        var expressions = new DefaultMethodSecurityExpressionHandler();
        expressions.setApplicationContext(beans);
        var manager = new PreAuthorizeAuthorizationManager();
        manager.setExpressionHandler(expressions);
        Object target = mock(type);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize(manager));
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(gate.method()))
                .reduce((first, second) -> {
                    throw new IllegalStateException("Ambiguous contract method: " + gate);
                }).orElseThrow();
        Object[] arguments = Arrays.stream(method.getParameterTypes())
                .map(parameter -> parameter.isPrimitive() ? Array.get(Array.newInstance(parameter, 1), 0) : null)
                .toArray();
        return new Fixture(target, factory.getProxy(type.getClassLoader()), method, arguments, security);
    }

    private static Stream<Gate> gates() {
        return GATES.stream().flatMap(gate -> {
            if (!gate.method().equals("*")) {
                return Stream.of(gate);
            }
            try {
                return Arrays.stream(Class.forName(gate.controller()).getDeclaredMethods())
                        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                        .map(method -> new Gate(gate.id(), gate.controller(), method.getName(),
                                gate.sysadmin(), gate.authenticatedOnly(), gate.permissions(), true));
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static List<Gate> readContract() {
        try (var input = ControllerAuthorizationContractTest.class.getResourceAsStream("/authz-gates.txt")) {
            if (input == null) {
                throw new IllegalStateException("Missing authorization contract");
            }
            List<Gate> gates = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines().filter(line -> !line.startsWith("#") && !line.isBlank()).map(line -> {
                        String[] fields = line.split("\\|", -1);
                        return new Gate(fields[0], fields[1], fields[2], Boolean.parseBoolean(fields[3]),
                                Boolean.parseBoolean(fields[4]), fields[5].isEmpty()
                                        ? List.of() : List.of(fields[5].split(",")), fields[2].equals("*"));
                    }).toList();
            assertThat(gates).hasSize(238);
            assertThat(gates.stream().map(Gate::id)).doesNotHaveDuplicates();
            return gates;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Class<?> withoutGuard(Gate gate) throws Exception {
        String resource = "/" + gate.controller().replace('.', '/') + ".class";
        try (var input = ControllerAuthorizationContractTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing controller bytecode: " + resource);
            }
            var reader = new ClassReader(input);
            var writer = new ClassWriter(0);
            int[] removed = {0};
            String annotation = "Lorg/springframework/security/access/prepost/PreAuthorize;";
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (gate.classLevel() && descriptor.equals(annotation)) {
                        removed[0]++;
                        return null;
                    }
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (gate.classLevel() || !name.equals(gate.method())) {
                        return delegate;
                    }
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (descriptor.equals(annotation)) {
                                removed[0]++;
                                return null;
                            }
                            return super.visitAnnotation(descriptor, visible);
                        }
                    };
                }
            }, 0);
            assertThat(removed[0]).as("Exactly one guard removed for %s", gate).isEqualTo(1);
            byte[] bytes = writer.toByteArray();
            return new ClassLoader(ControllerAuthorizationContractTest.class.getClassLoader()) {
                Class<?> defineMutant() {
                    return defineClass(gate.controller(), bytes, 0, bytes.length);
                }
            }.defineMutant();
        }
    }

    private record Gate(String id, String controller, String method, boolean sysadmin,
            boolean authenticatedOnly, List<String> permissions, boolean classLevel) {
        @Override
        public String toString() {
            return id + " " + controller.substring(controller.lastIndexOf('.') + 1) + "." + method;
        }
    }

    private record Fixture(Object target, Object proxy, Method method, Object[] arguments,
            SecurityService security) {
        void invoke() throws Throwable {
            try {
                method.invoke(proxy, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
