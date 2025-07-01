package com.sedna.crawler.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UrlUtilsTest {

    @Test
    void normalizeUrl_HandlesBasicUrl() {
        String url = "https://example.com/path";
        String expected = "https://example.com/path";
        assertEquals(expected, UrlUtils.normalizeUrl(url));
    }

    @Test
    void normalizeUrl_RemovesDefaultPorts() {
        assertEquals("https://example.com/path",
                UrlUtils.normalizeUrl("https://example.com:443/path"));
        assertEquals("http://example.com/path",
                UrlUtils.normalizeUrl("http://example.com:80/path"));
    }

    @Test
    void normalizeUrl_KeepsNonDefaultPorts() {
        assertEquals("https://example.com:8080/path",
                UrlUtils.normalizeUrl("https://example.com:8080/path"));
    }

    @Test
    void normalizeUrl_IncludesQueryParameters() {
        String url = "https://example.com/path?param=value&other=123";
        assertEquals(url, UrlUtils.normalizeUrl(url));
    }

    @Test
    void normalizeUrl_HandlesInvalidUrl() {
        String invalidUrl = "not-a-url";
        assertEquals(invalidUrl, UrlUtils.normalizeUrl(invalidUrl));
    }

    @Test
    void extractDomain_ValidUrls() throws URISyntaxException {
        assertEquals("example.com", UrlUtils.extractDomain("https://example.com/path"));
        assertEquals("subdomain.example.com",
                UrlUtils.extractDomain("https://subdomain.example.com/path"));
        assertEquals("example.com", UrlUtils.extractDomain("http://example.com:8080/path"));
    }

    @Test
    void extractDomain_ThrowsException_ForNullOrEmptyUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlUtils.extractDomain(null));
        assertThrows(IllegalArgumentException.class,
                () -> UrlUtils.extractDomain(""));
        assertThrows(IllegalArgumentException.class,
                () -> UrlUtils.extractDomain("   "));
    }

    @Test
    void extractDomain_ThrowsException_ForUrlWithoutHost() {
        assertThrows(URISyntaxException.class,
                () -> UrlUtils.extractDomain("file:///path/to/file"));
    }

    @Test
    void normalizeUrl_RemovesTrailingSlash() {
        // Should remove trailing slash from paths
        assertEquals("https://example.com/path",
                UrlUtils.normalizeUrl("https://example.com/path/"));
        assertEquals("https://example.com/path/subpath",
                UrlUtils.normalizeUrl("https://example.com/path/subpath/"));

        // Should NOT remove trailing slash from root
        assertEquals("https://example.com/",
                UrlUtils.normalizeUrl("https://example.com/"));
        assertEquals("https://example.com/",
                UrlUtils.normalizeUrl("https://example.com"));

        // Should handle query parameters
        assertEquals("https://example.com/path?param=value",
                UrlUtils.normalizeUrl("https://example.com/path/?param=value"));
    }

    @Test
    void normalizeUrl_HandlesRootPathConsistently() {
        // These should all normalize to the same thing
        String expected = "https://example.com/";
        assertEquals(expected, UrlUtils.normalizeUrl("https://example.com"));
        assertEquals(expected, UrlUtils.normalizeUrl("https://example.com/"));
        assertEquals(expected, UrlUtils.normalizeUrl("https://EXAMPLE.COM/"));
    }

    @ParameterizedTest
    @MethodSource("provideSameDomainTestCases")
    void isSameDomain_TestCases(String url, String targetDomain, boolean expected) {
        assertEquals(expected, UrlUtils.isSameDomain(url, targetDomain));
    }

    private static Stream<Arguments> provideSameDomainTestCases() {
        return Stream.of(
                Arguments.of("https://example.com/path", "example.com", true),
                Arguments.of("https://sub.example.com/path", "example.com", true),
                Arguments.of("https://other.com/path", "example.com", false),
                Arguments.of("https://example.com.evil.com/path", "example.com", false),
                Arguments.of("invalid-url", "example.com", false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideResolveUrlTestCases")
    void resolveUrl_TestCases(String link, String baseUrl, String expected) {
        assertEquals(expected, UrlUtils.resolveUrl(link, baseUrl));
    }

    private static Stream<Arguments> provideResolveUrlTestCases() {
        return Stream.of(
                // Absolute URLs
                Arguments.of("https://other.com/page", "https://example.com/base", "https://other.com/page"),

                // Protocol-relative URLs
                Arguments.of("//other.com/page", "https://example.com/base", "https://other.com/page"),

                // Root-relative URLs
                Arguments.of("/absolute/path", "https://example.com/base", "https://example.com/absolute/path"),
                Arguments.of("/absolute/path", "https://example.com:8080/base", "https://example.com:8080/absolute/path"),

                // Relative URLs
                Arguments.of("relative/path", "https://example.com/base/", "https://example.com/base/relative/path"),

                // Ignored links
                Arguments.of("#fragment", "https://example.com/base", null),
                Arguments.of("mailto:test@example.com", "https://example.com/base", null),
                Arguments.of("javascript:void(0)", "https://example.com/base", null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path",
            "https://sub.example.com:8080/path?param=value"
    })
    void isValidUrl_ReturnsTrue_ForValidUrls(String url) {
        assertTrue(UrlUtils.isValidUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-a-url",
            "ftp://example.com",
            "file:///path/to/file",
            "example.com"
    })
    void isValidUrl_ReturnsFalse_ForInvalidUrls(String url) {
        assertFalse(UrlUtils.isValidUrl(url));
    }

    @Test
    void isValidUrl_ReturnsFalse_ForNull() {
        assertFalse(UrlUtils.isValidUrl(null));
    }
}
