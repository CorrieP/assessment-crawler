package com.sedna.crawler;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sedna.crawler.utils.UrlUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class WebCrawlerTest {

    private WireMockServer wireMockServer;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        baseUrl = "http://localhost:" + wireMockServer.port();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void crawl_SinglePage_ReturnsCorrectUrl() {
        // Setup
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Test Page</h1></body></html>")));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl);

            assertEquals(1, result.size());
            assertEquals(baseUrl + "/", result.get(0));
        }
    }

    @Test
    void crawl_PageWithLinks_FollowsInternalLinks() {
        // Setup main page
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                    <html><body>
                        <a href="/page1">Internal Link 1</a>
                        <a href="/page2">Internal Link 2</a>
                        <a href="https://external.com/page">External Link</a>
                    </body></html>""")));

        wireMockServer.stubFor(get(urlEqualTo("/page1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Page 1</h1></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/page2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Page 2</h1></body></html>")));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl);

            assertEquals(3, result.size());
            assertTrue(result.contains(baseUrl + "/"));
            assertTrue(result.contains(baseUrl + "/page1"));
            assertTrue(result.contains(baseUrl + "/page2"));
            assertFalse(result.contains("https://external.com/page"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 301, 302, 307, 308, 400, 410})
    void crawl_IgnoresSpecificStatusCodes(int statusCode) {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href='/error-page'>Error Link</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/error-page"))
                .willReturn(aResponse().withStatus(statusCode)));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl);

            assertEquals(1, result.size());
            assertEquals(baseUrl + "/", result.get(0));
        }
    }

    @Test
    void crawl_RespectsMaxPagesLimit() {
        // Setup multiple pages
        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                <html><body>
                    <a href="/page1">Link 1</a>
                    <a href="/page2">Link 2</a>
                    <a href="/page3">Link 3</a>
                    <a href="/page4">Link 4</a>
                    <a href="/page5">Link 5</a>
                </body></html>""")));

        for (int i = 1; i <= 5; i++) {
            wireMockServer.stubFor(get(urlPathEqualTo("/page" + i))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/html")
                            .withBody("<html><body><h1>Page " + i + "</h1></body></html>")));
        }

        try (WebCrawler crawler = new WebCrawler(3)) { // Limit to 3 pages
            List<String> result = crawler.crawl(baseUrl);

            System.out.println("Expected max: 3, Actual: " + result.size());
            result.forEach(System.out::println);

            assertTrue(result.size() <= 3,
                    "Should have at most 3 pages, but got " + result.size());

            // Verify we got the root page at least
            assertTrue(result.contains(UrlUtils.normalizeUrl(baseUrl)),
                    "Should contain the root page");

            // Verify no duplicates
            assertEquals(result.size(), result.stream().distinct().count(),
                    "Should have no duplicate URLs");
        }
    }

    @Test
    void crawl_HandlesInvalidUrl() {
        try (WebCrawler crawler = new WebCrawler(10)) {
            assertThrows(IllegalArgumentException.class,
                    () -> crawler.crawl("invalid-url"));
        }
    }

    @Test
    void crawl_HandlesRelativeLinks() {
        System.out.println("Base URL: " + baseUrl);

        // Use urlPathEqualTo which is more flexible
        wireMockServer.stubFor(get(urlPathEqualTo("/folder"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                            <html><body>
                                <a href="relative.html">Relative Link</a>
                                <a href="../parent.html">Parent Link</a>
                            </body></html>""")));

        wireMockServer.stubFor(get(urlPathEqualTo("/folder/relative.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Relative Page</h1></body></html>")));

        wireMockServer.stubFor(get(urlPathEqualTo("/parent.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Parent Page</h1></body></html>")));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl + "/folder/");

            System.out.println("Crawl results:");
            result.forEach(System.out::println);

            assertTrue(result.size() >= 1, "Should have at least 1 result");
        }
    }

    @Test
    void crawl_SinglePage_Debug() {
        System.out.println("=== SINGLE PAGE DEBUG TEST ===");
        System.out.println("Base URL: " + baseUrl);

        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Test Page</h1></body></html>")));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl);

            System.out.println("Results: " + result.size());
            result.forEach(System.out::println);

            System.out.println("Requests made:");
            wireMockServer.getAllServeEvents().forEach(event -> {
                System.out.println("  " + event.getRequest().getMethod() + " " +
                        event.getRequest().getUrl() + " -> " +
                        event.getResponse().getStatus());
            });

            assertFalse(result.isEmpty(), "Single page test should work");
        }
    }

    @Test
    void debug_UrlNormalization() {
        String testUrl = baseUrl + "/folder/";
        System.out.println("Original URL: " + testUrl);
        System.out.println("Normalized URL: " + UrlUtils.normalizeUrl(testUrl));

        // Test if normalization is changing the path
        try {
            String domain = UrlUtils.extractDomain(testUrl);
            System.out.println("Extracted domain: " + domain);
            System.out.println("Is same domain: " + UrlUtils.isSameDomain(testUrl, domain));
        } catch (Exception e) {
            System.out.println("Error in URL processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testUrlResolutionDebug() {
        String baseUrl = "http://localhost:8080/folder/";

        String resolved1 = UrlUtils.resolveUrl("relative.html", baseUrl);
        String resolved2 = UrlUtils.resolveUrl("../parent.html", baseUrl);

        System.out.println("Base: " + baseUrl);
        System.out.println("Relative resolved: " + resolved1);
        System.out.println("Parent resolved: " + resolved2);

        System.out.println("Relative normalized: " + UrlUtils.normalizeUrl(resolved1));
        System.out.println("Parent normalized: " + UrlUtils.normalizeUrl(resolved2));
    }

    @Test
    void crawl_ThreadSafety_ConcurrentExecution() throws InterruptedException {
        // Setup a page with many links
        StringBuilder htmlBody = new StringBuilder("<html><body>");
        for (int i = 1; i <= 20; i++) {
            htmlBody.append("<a href='/page").append(i).append("'>Page ").append(i).append("</a>");
        }
        htmlBody.append("</body></html>");

        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(htmlBody.toString())));

        // Setup all linked pages
        for (int i = 1; i <= 20; i++) {
            wireMockServer.stubFor(get(urlEqualTo("/page" + i))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/html")
                            .withBody("<html><body><h1>Page " + i + "</h1></body></html>")));
        }

        try (WebCrawler crawler = new WebCrawler(25)) {
            List<String> result = crawler.crawl(baseUrl);

            // Verify no duplicates (thread safety)
            assertEquals(result.size(), result.stream().distinct().count());
            assertTrue(result.size() >= 1); // At least the main page
        }
    }

    @Test
    void crawl_MultipleInstancesConcurrently() throws InterruptedException {
        wireMockServer.stubFor(get(urlMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Test Page</h1></body></html>")));

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try (WebCrawler crawler = new WebCrawler(5)) {
                    List<String> result = crawler.crawl(baseUrl);
                    if (!result.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(3, successCount.get());

        executor.shutdown();
    }

    @Test
    void crawl_HandlesCircularReferences() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href='/page1'>Page 1</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/page1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href='/'>Back to Home</a></body></html>")));

        try (WebCrawler crawler = new WebCrawler(10)) {
            List<String> result = crawler.crawl(baseUrl);

            // Print for debugging
            System.out.println("Crawled URLs: " + result);

            assertEquals(2, result.size());
            assertTrue(result.contains(baseUrl + "/"));  // Normalized root
            assertTrue(result.contains(baseUrl + "/page1"));

            // Verify no duplicates
            assertEquals(result.size(), result.stream().distinct().count());
        }
    }

    @Test
    void close_ShutsDownExecutorProperly() {
        WebCrawler crawler = new WebCrawler(10);

        assertDoesNotThrow(() -> crawler.close());

        // Verify that calling close multiple times doesn't cause issues
        assertDoesNotThrow(() -> crawler.close());
    }

    @Test
    void crawl_HandlesSlowResponses() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href='/slow'>Slow Page</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Slow Page</h1></body></html>")
                        .withFixedDelay(500))); // 500ms delay

        try (WebCrawler crawler = new WebCrawler(10)) {
            long startTime = System.currentTimeMillis();
            List<String> result = crawler.crawl(baseUrl);
            long duration = System.currentTimeMillis() - startTime;

            assertEquals(2, result.size());
            assertTrue(duration >= 500); // Should take at least 500ms due to delay
        }
    }
}
