package com.sedna.crawler.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class HttpUtilsTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 301, 302, 307, 308, 404, 410})
    void shouldIgnoreSilently_ReturnsTrue_ForIgnoredStatusCodes(int statusCode) {
        assertTrue(HttpUtils.shouldIgnoreSilently(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 500, 503, 429})
    void shouldIgnoreSilently_ReturnsFalse_ForNonIgnoredStatusCodes(int statusCode) {
        assertFalse(HttpUtils.shouldIgnoreSilently(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201})
    void isSuccess_ReturnsTrue_ForSuccessStatusCodes(int statusCode) {
        assertTrue(HttpUtils.isSuccess(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {300, 400, 404, 500, 503})
    void isSuccess_ReturnsFalse_ForNonSuccessStatusCodes(int statusCode) {
        assertFalse(HttpUtils.isSuccess(statusCode));
    }

    @Test
    void shouldIgnoreSilently_HandlesEdgeCases() {
        // Test boundary values
        assertFalse(HttpUtils.shouldIgnoreSilently(0));
        assertFalse(HttpUtils.shouldIgnoreSilently(-1));
        assertFalse(HttpUtils.shouldIgnoreSilently(999));
    }

    @Test
    void isSuccess_HandlesEdgeCases() {
        // Test boundary values
        assertFalse(HttpUtils.isSuccess(0));
        assertFalse(HttpUtils.isSuccess(-1));
        assertFalse(HttpUtils.isSuccess(199));
        assertFalse(HttpUtils.isSuccess(202));
    }
}
