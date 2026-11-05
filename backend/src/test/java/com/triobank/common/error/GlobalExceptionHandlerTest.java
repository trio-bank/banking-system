package com.triobank.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.common.config.SecurityConfig;
import com.triobank.common.web.TraceIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = ErrorTestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.FixedClock.class})
@ActiveProfiles("test")
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String NOW = "2026-11-05T10:15:30Z";
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private static final String TRACE_ID_PATTERN = "[0-9a-f]{16}";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            // milliseconds here, to check they are cut off in the response
            return Clock.fixed(Instant.parse(NOW).plusMillis(123), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void apiExceptionIsRenderedAsProblemDetailWithExtensionFields() throws Exception {
        MvcResult result = mvc.perform(get("/test/api-error"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://triobank.example/errors/user-locked"))
                .andExpect(jsonPath("$.title").value("User locked"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Too many failed logins."))
                .andExpect(jsonPath("$.instance").value("/test/api-error"))
                .andExpect(jsonPath("$.code").value("USER_LOCKED"))
                .andExpect(jsonPath("$.timestamp").value(NOW))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.lockedUntil").value("2026-11-05T10:30:30Z"))
                .andReturn();

        assertTraceIdMatchesHeader(result);
    }

    @Test
    void apiExceptionWithoutDetailUsesTitle() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Resource not found"));
    }

    @Test
    void invalidJsonIsMalformedRequest() throws Exception {
        MvcResult result = mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Jan\", "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.type").value("https://triobank.example/errors/malformed-request"))
                .andExpect(jsonPath("$.instance").value("/test/validated"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andReturn();

        assertTraceIdMatchesHeader(result);
    }

    @Test
    void wrongJsonTypeIsMalformedRequest() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Jan\", \"amount\": \"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void beanValidationFailuresAreMappedToFieldCodes() throws Exception {
        String body = """
                {"name": " ", "nickname": "ab", "amount": "10.001", "address": {"postalCode": "00100"}}
                """;

        mvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid."))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')].code").value("TOO_SHORT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')].code").value("TOO_MANY_DECIMALS"))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.postalCode')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.postalCode')].message")
                        .value("Postal code must match NN-NNN."));
    }

    @Test
    void tooLongValueIsTooLong() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Jan\", \"nickname\": \"abcdef\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void requestParameterValidationIsValidationError() throws Exception {
        mvc.perform(get("/test/params").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void invalidPathVariableIsMalformedRequest() throws Exception {
        mvc.perform(get("/test/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    void unknownPathIsResourceNotFound() throws Exception {
        mvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/test/does-not-exist"));
    }

    @Test
    void unsupportedMethodKeepsStatusAndGetsCode() throws Exception {
        mvc.perform(post("/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void optimisticLockFailureIsConcurrentModification() throws Exception {
        mvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unmappedDataIntegrityViolationIsInternalError() throws Exception {
        mvc.perform(get("/test/integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void accessDeniedIsForbidden() throws Exception {
        mvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutInternalsAndIsLoggedWithTraceId(CapturedOutput output)
            throws Exception {
        MvcResult result = mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(ErrorTestController.INTERNAL_SECRET, "IllegalStateException", "at com.");

        String traceId = assertTraceIdMatchesHeader(result);
        assertThat(output.getOut())
                .contains("[" + traceId + "]")
                .contains("Unexpected error on GET /test/boom")
                .contains(ErrorTestController.INTERNAL_SECRET);
    }

    @Test
    void successfulResponseHasTraceIdHeader() throws Exception {
        mvc.perform(get("/test/money"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.HEADER, matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    void unauthenticatedResponseHasTraceIdHeader() throws Exception {
        mvc.perform(get("/test/money").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.HEADER, matchesPattern(TRACE_ID_PATTERN)));
    }

    @Test
    void moneyIsSerializedAsStringWithTwoDecimalsAndOtherDecimalsKeepTheirScale() throws Exception {
        mvc.perform(get("/test/money"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"amount": "10.00", "missing": null, "interestRate": "4.0000"}
                        """, JsonCompareMode.STRICT));
    }

    private String assertTraceIdMatchesHeader(MvcResult result) throws Exception {
        String header = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(header).matches(TRACE_ID_PATTERN);
        String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(traceId).isEqualTo(header);
        return traceId;
    }

}
