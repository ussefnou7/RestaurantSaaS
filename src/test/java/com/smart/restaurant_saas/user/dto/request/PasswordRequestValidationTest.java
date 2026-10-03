package com.smart.restaurant_saas.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequestsRejectPasswordsThatDoNotMeetThePolicy() {
        List<String> invalidPasswords = List.of(
                "Abc123",
                "abcdefgh",
                "12345678",
                "Abcd123\n"
        );

        for (String password : invalidPasswords) {
            assertThat(passwordErrors(createOwner(password))).isNotEmpty();
            assertThat(passwordErrors(createAdminManagedUser(password))).isNotEmpty();
            assertThat(passwordErrors(createTenantManagedUser(password))).isNotEmpty();
        }
    }

    @Test
    void createRequestsAcceptUnicodeLettersWithNumbers() {
        String password = "مطعم1234";

        assertThat(passwordErrors(createOwner(password))).isEmpty();
        assertThat(passwordErrors(createAdminManagedUser(password))).isEmpty();
        assertThat(passwordErrors(createTenantManagedUser(password))).isEmpty();
    }

    @Test
    void updateRequestsAllowOmittedPasswordAndValidateProvidedPassword() {
        assertThat(passwordErrors(updateAdminManagedUser(null))).isEmpty();
        assertThat(passwordErrors(updateTenantManagedUser(null))).isEmpty();

        assertThat(passwordErrors(updateAdminManagedUser("password")))
                .containsExactly("PASSWORD_PATTERN");
        assertThat(passwordErrors(updateTenantManagedUser("password")))
                .containsExactly("PASSWORD_PATTERN");

        assertThat(passwordErrors(updateAdminManagedUser("Newpass1"))).isEmpty();
        assertThat(passwordErrors(updateTenantManagedUser("Newpass1"))).isEmpty();
    }

    @Test
    void loginRequestDoesNotApplyTheCreationPasswordPolicy() {
        LoginRequest request = new LoginRequest("tenant", "owner", "1", null);

        assertThat(passwordErrors(request)).isEmpty();
    }

    private CreateTenantOwnerRequest createOwner(String password) {
        return new CreateTenantOwnerRequest("Owner", "owner", password, null, null);
    }

    private CreateTenantUserRequest createAdminManagedUser(String password) {
        return new CreateTenantUserRequest(
                "Cashier", "cashier", password, null, null, "CASHIER", null);
    }

    private CreateUserRequest createTenantManagedUser(String password) {
        return new CreateUserRequest(
                "cashier", "Cashier", null, password, "CASHIER", null, true);
    }

    private UpdateTenantUserRequest updateAdminManagedUser(String password) {
        return new UpdateTenantUserRequest("Cashier", "cashier", null, null, password);
    }

    private UpdateUserRequest updateTenantManagedUser(String password) {
        return new UpdateUserRequest("Cashier", null, "CASHIER", null, true, password);
    }

    private List<String> passwordErrors(Object request) {
        return validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }
}
