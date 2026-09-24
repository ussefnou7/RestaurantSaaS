package com.smart.restaurant_saas.rbac.contract;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads every controller's {@code @PreAuthorize} by reflection, with no Spring context and no
 * database, and reports what each endpoint requires.
 *
 * <p>Reflection rather than grepping source: a merged annotation lookup resolves
 * {@code @GetMapping} and friends through their {@code @RequestMapping} meta-annotation and applies
 * the class-level path prefix, so the path and HTTP method are the real ones. Grep cannot do either
 * and would silently miss a gate written in an unexpected shape.
 *
 * <p>An expression this scanner cannot parse becomes an {@link Gate#unparseable} gate rather than
 * being dropped. The caller is expected to fail on those — that is the only thing keeping the
 * generated manifest honest as controllers are added.
 */
final class PermissionGateScanner {

    private static final String BASE_PACKAGE = "com.smart.restaurant_saas";

    private static final Pattern HAS_PERMISSION =
            Pattern.compile("^@securityService\\.hasPermission\\('([A-Z0-9_]+)'\\)$");
    private static final Pattern ROLE_RULE =
            Pattern.compile("^@securityService\\.(isSysAdmin|isOwner|isOwnerOrBranchManager)\\(\\)$");
    private static final String AUTHENTICATED = "isAuthenticated()";

    private PermissionGateScanner() {
    }

    /**
     * One endpoint and what it requires. Exactly one of {@code permissionCodes} / {@code roleRules}
     * being non-empty is the normal case; both empty means the endpoint is login-only.
     */
    record Gate(
            String httpMethod,
            String path,
            String handler,
            String expression,
            Set<String> permissionCodes,
            Set<String> roleRules,
            boolean unparseable
    ) {
    }

    static List<Gate> scan() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Gate> gates = new ArrayList<>();
        for (BeanDefinition candidate : provider.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = loadOrFail(candidate.getBeanClassName());
            String classPath = firstPathOf(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                gates.add(toGate(controller, method, classPath, mapping));
            }
        }
        gates.sort(Comparator.comparing(Gate::path).thenComparing(Gate::httpMethod));
        return gates;
    }

    private static Gate toGate(Class<?> controller, Method method, String classPath, RequestMapping mapping) {
        String path = join(classPath, firstPathOf(mapping));
        String httpMethod = mapping.method().length == 0 ? "ANY" : mapping.method()[0].name();
        String handler = controller.getSimpleName() + "." + method.getName();

        // Method-level wins; a controller may still carry a class-level gate as the default.
        PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (preAuthorize == null) {
            preAuthorize = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        }
        if (preAuthorize == null) {
            // SecurityConfig ends in anyRequest().authenticated(), so this is login-only, not open.
            return new Gate(httpMethod, path, handler, null, Set.of(), Set.of(), false);
        }
        return parse(httpMethod, path, handler, preAuthorize.value().trim());
    }

    private static Gate parse(String httpMethod, String path, String handler, String expression) {
        Set<String> permissionCodes = new LinkedHashSet<>();
        Set<String> roleRules = new LinkedHashSet<>();

        // " and " would narrow rather than widen access; refuse it instead of mis-reading it as " or ".
        if (expression.contains(" and ")) {
            return new Gate(httpMethod, path, handler, expression, Set.of(), Set.of(), true);
        }

        for (String term : expression.split("\\s+or\\s+")) {
            String trimmed = term.trim();
            Matcher permission = HAS_PERMISSION.matcher(trimmed);
            if (permission.matches()) {
                permissionCodes.add(permission.group(1));
                continue;
            }
            Matcher role = ROLE_RULE.matcher(trimmed);
            if (role.matches()) {
                roleRules.add(role.group(1));
                continue;
            }
            if (AUTHENTICATED.equals(trimmed)) {
                continue;
            }
            return new Gate(httpMethod, path, handler, expression, Set.of(), Set.of(), true);
        }

        // isSysAdmin() alongside a permission is the universal bypass, not a role requirement.
        if (!permissionCodes.isEmpty()) {
            roleRules.remove("isSysAdmin");
        }
        return new Gate(httpMethod, path, handler, expression, permissionCodes, roleRules, false);
    }

    private static String firstPathOf(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        return mapping.path()[0];
    }

    private static String join(String classPath, String methodPath) {
        String combined = classPath + (methodPath.isEmpty() || methodPath.startsWith("/") ? methodPath : "/" + methodPath);
        return combined.isEmpty() ? "/" : combined;
    }

    private static Class<?> loadOrFail(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Scanned controller is not loadable: " + className, e);
        }
    }
}
