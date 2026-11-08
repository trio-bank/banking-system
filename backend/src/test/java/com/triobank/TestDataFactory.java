package com.triobank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Test data for integration tests (docs/09-testing.md §3). Goes through the public API, like a real client. */
public final class TestDataFactory {

    public static final String PASSWORD = "Str0ng!Passw0rd";
    public static final String WRONG_PASSWORD = "Wr0ng!Passw0rd";
    public static final String SORT_CODE = "99900012";
    public static final String FIRST_NAME = "Anna";
    public static final String LAST_NAME = "Nowak";
    public static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(1990, 5, 14);

    private static final AtomicInteger PESEL_SERIAL = new AtomicInteger(1000);

    public record RegisteredCustomer(UUID userId, String customerNumber, String email, String password,
                                     String pesel, String iban) {
    }

    private final MockMvc mockMvc;

    public TestDataFactory(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ------------------------------------------------------------ API calls

    /** Registers a new adult customer with a unique email and PESEL. */
    public RegisteredCustomer registerCustomer() throws Exception {
        String email = uniqueEmail();
        String pesel = uniquePesel();
        MvcResult result = mockMvc.perform(registerRequest(registerJson(email, PASSWORD, pesel, "")))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredCustomer(
                UUID.fromString(JsonPath.read(body, "$.userId")),
                JsonPath.read(body, "$.customerNumber"),
                email, PASSWORD, pesel,
                JsonPath.read(body, "$.account.iban"));
    }

    public ResultActions login(String email, String password) throws Exception {
        String json = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public String loginAndGetToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------- requests

    public static MockHttpServletRequestBuilder registerRequest(String json) {
        return post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** @param extraFields raw JSON fields added to the request, e.g. {@code "role": "ADMIN"}; may be empty */
    public static String registerJson(String email, String password, String pesel, String extraFields) {
        String extra = extraFields.isBlank() ? "" : extraFields + ",";
        return """
                {
                  %s
                  "email": "%s",
                  "password": "%s",
                  "firstName": "%s",
                  "lastName": "%s",
                  "pesel": "%s",
                  "phoneNumber": "+48501234567",
                  "address": {
                    "street": "Krakowska",
                    "buildingNumber": "12A",
                    "apartmentNumber": "4",
                    "postalCode": "45-018",
                    "city": "Opole",
                    "countryCode": null
                  }
                }
                """.formatted(extra, email, password, FIRST_NAME, LAST_NAME, pesel);
    }

    // ----------------------------------------------------------- unique data

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** A valid PESEL of an adult born on {@link #ADULT_BIRTH_DATE}; every call gives a new number. */
    public static String uniquePesel() {
        return pesel(ADULT_BIRTH_DATE, PESEL_SERIAL.incrementAndGet());
    }

    /** A valid PESEL of a person who is 17 today. */
    public static String underagePesel() {
        return pesel(LocalDate.now().minusYears(17), PESEL_SERIAL.incrementAndGet());
    }

    public static String withBrokenCheckDigit(String pesel) {
        int wrong = (pesel.charAt(10) - '0' + 1) % 10;
        return pesel.substring(0, 10) + wrong;
    }

    /** PESEL = YYMMDD + 4-digit serial + check digit (weights 1,3,7,9,1,3,7,9,1,3). Years 1900-2099 only. */
    static String pesel(LocalDate birth, int serial) {
        int monthCode = birth.getMonthValue() + (birth.getYear() >= 2000 ? 20 : 0);
        String base = "%02d%02d%02d%04d".formatted(birth.getYear() % 100, monthCode, birth.getDayOfMonth(), serial);
        int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }
        return base + (10 - sum % 10) % 10;
    }

    // --------------------------------------------------------------- checks

    /** IBAN mod-97 check for Polish IBANs (PL + 26 digits). */
    public static boolean isValidPolishIban(String iban) {
        if (iban == null || !iban.matches("PL\\d{26}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.digit(c, 36)); // digits stay digits, P = 25, L = 21
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /** Codes of the field errors of one field in a 400 response. */
    public static List<String> fieldCodes(MvcResult result, String field) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(),
                "$.errors[?(@.field=='" + field + "')].code");
    }

}
