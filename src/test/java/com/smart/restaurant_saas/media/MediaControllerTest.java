package com.smart.restaurant_saas.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.CommonErrorCode;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.media.dto.MediaResponse;
import com.smart.restaurant_saas.media.dto.MediaVariantResponse;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of {@link MediaController}: caching headers, conditional requests, and the
 * shape of a refusal.
 *
 * <p>Permission <em>mapping</em> is asserted in {@code MediaAttachmentIntegrationTest}, where the
 * purposes and their codes are real. What matters here is that a refusal raised inside the service
 * still reaches the client as a translatable {@code errorCode} — a media endpoint carries no
 * permission string in its annotation, so nothing else proves the gate is wired.
 */
@WebMvcTest(
        controllers = MediaController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({MediaControllerTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class MediaControllerTest {

    private static final String CHECKSUM =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        reset(mediaService);
    }

    @Test
    @WithMockUser
    void uploadReturnsCreatedAndForwardsThePurposeAndOwner() throws Exception {
        when(mediaService.upload(eq(SliceTenantConfig.TENANT_ID), eq(9L),
                eq(MediaPurpose.PRODUCT_IMAGE), eq(42L), any()))
                .thenReturn(response());

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("purpose", "PRODUCT_IMAGE")
                        .param("ownerId", "42")
                        .header("X-User-Id", "9"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purpose").value("PRODUCT_IMAGE"))
                .andExpect(jsonPath("$.variants[0].url").value("/api/media/77/thumb"));
    }

    @Test
    @WithMockUser
    void aRefusalFromThePurposesPermissionReachesTheClientAsAnErrorCode() throws Exception {
        when(mediaService.upload(any(), any(), any(), any(), any()))
                .thenThrow(new AuthorizationException(CommonErrorCode.ACCESS_DENIED,
                        "Caller lacks PRODUCTS_UPDATE",
                        ErrorParams.of("requiredPermission", "PRODUCTS_UPDATE")));

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("purpose", "PRODUCT_IMAGE")
                        .param("ownerId", "42"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser
    void deleteForwardsTheLinkIdAndAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/media/links/5"))
                .andExpect(status().isNoContent());

        verify(mediaService).delete(SliceTenantConfig.TENANT_ID, 5L);
    }

    @Test
    @WithMockUser
    void deleteOnAnImmutableOwnerAnswersConflictWithItsCode() throws Exception {
        doThrow(new com.smart.restaurant_saas.common.ValidationException(
                MediaErrorCode.MEDIA_OWNER_IMMUTABLE, "Owner is final"))
                .when(mediaService).delete(SliceTenantConfig.TENANT_ID, 5L);

        mockMvc.perform(delete("/api/media/links/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_OWNER_IMMUTABLE"));
    }

    @Test
    @WithMockUser
    void streamIsCacheableForAYearAndCarriesTheChecksumEtag() throws Exception {
        when(mediaService.openVariant(SliceTenantConfig.TENANT_ID, 77L, MediaVariantType.THUMB))
                .thenReturn(streamed());

        mockMvc.perform(get("/api/media/77/thumb"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "-THUMB\""))
                .andExpect(header().string("Cache-Control", "max-age=31536000, private, immutable"))
                // The stored content type is sniffed from the bytes; a browser that sniffs for
                // itself would undo that.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @WithMockUser
    void aMatchingIfNoneMatchAnswers304WithNoBody() throws Exception {
        when(mediaService.openVariant(SliceTenantConfig.TENANT_ID, 77L, MediaVariantType.THUMB))
                .thenReturn(streamed());

        mockMvc.perform(get("/api/media/77/thumb")
                        .header("If-None-Match", "\"" + CHECKSUM + "-THUMB\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    @WithMockUser
    void renditionsOfOneFileDoNotShareAnEtag() throws Exception {
        when(mediaService.openVariant(SliceTenantConfig.TENANT_ID, 77L, MediaVariantType.MEDIUM))
                .thenReturn(streamed());

        // Same file, same checksum, different rendition. Were the ETag the checksum alone, this
        // would answer 304 and serve a thumbnail where a medium was asked for.
        mockMvc.perform(get("/api/media/77/medium")
                        .header("If-None-Match", "\"" + CHECKSUM + "-THUMB\""))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void anUnknownVariantIsANotFoundWithAMediaErrorCode() throws Exception {
        mockMvc.perform(get("/api/media/77/gigantic"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_VARIANT_NOT_FOUND"));
    }

    private MediaResponse response() {
        return new MediaResponse(1L, 77L, MediaOwnerType.PRODUCT, 42L, MediaPurpose.PRODUCT_IMAGE,
                0, "a.jpg", "image/jpeg", 12L, 100, 100,
                List.of(new MediaVariantResponse(MediaVariantType.THUMB, 100, 100, 12L,
                        "/api/media/77/thumb", null)));
    }

    private MediaService.StreamedVariant streamed() {
        byte[] bytes = "stub-image-bytes".getBytes(StandardCharsets.UTF_8);
        return new MediaService.StreamedVariant(new ByteArrayResource(bytes), "image/jpeg",
                (long) bytes.length, CHECKSUM);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        MediaService mediaService() {
            return mock(MediaService.class);
        }
    }
}
