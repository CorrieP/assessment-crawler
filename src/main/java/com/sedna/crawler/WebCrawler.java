package com.sedna.crawler;

import com.sedna.crawler.utils.HttpUtils;
import com.sedna.crawler.utils.UrlUtils;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.time.Duration;

import static com.sedna.crawler.utils.UrlUtils.*;

public class WebCrawler implements AutoCloseable{
    private final Set<String> visitedUrls;
    private final HttpClient httpClient;
    private final Pattern linkPattern;
    private final Semaphore pageLimitSemaphore;
    private final int maxPages;
    private final ExecutorService virtualThreadExecutor;
    private final ConcurrentHashMap<String, Semaphore> domainSemaphores = new ConcurrentHashMap<>();
    // avoid too many concurrent streams
    private static final int MAX_CONCURRENT_REQUESTS_PER_DOMAIN = 2;

    public WebCrawler(int maxPages) {
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.pageLimitSemaphore = new Semaphore(maxPages);
        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofSeconds(15))
                                    .build();
        this.linkPattern = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=([\"'])(.*?)\\1",
                Pattern.CASE_INSENSITIVE);
        this.maxPages = maxPages;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public WebCrawler() {
        this(200);
    }

    public List<String> crawl(String startUrl) {
        if (!isValidUrl(startUrl)) {
            throw new IllegalArgumentException("Invalid URL: " + startUrl);
        }
        try {
            String normalizedStartUrl = normalizeUrl(startUrl);
            String domain = extractDomain(normalizedStartUrl);

            // Start crawling and collect all futures
            Set<CompletableFuture<Void>> activeTasks = ConcurrentHashMap.newKeySet();

            CompletableFuture<Void> initialTask = crawlPageAsync(normalizedStartUrl, domain, activeTasks);
            activeTasks.add(initialTask);

            // Wait for all tasks to complete
            waitForCompletion(activeTasks);

            return new ArrayList<>(visitedUrls);

        } catch (Exception e) {
            System.err.println("Error starting crawl: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            virtualThreadExecutor.shutdown();
        }
    }

    private void waitForCompletion(Set<CompletableFuture<Void>> activeTasks) {
        while (!activeTasks.isEmpty() && visitedUrls.size() < maxPages) {
            // Remove completed tasks
            activeTasks.removeIf(CompletableFuture::isDone);

            if (!activeTasks.isEmpty()) {
                System.out.println("Progress: " + visitedUrls.size() + " pages crawled, "
                        + activeTasks.size() + " active tasks");

                // Wait for at least one task to complete
                CompletableFuture<Void> anyCompleted = CompletableFuture.anyOf(
                        activeTasks.toArray(new CompletableFuture[0])
                ).thenApply(v -> null);

                try {
                    anyCompleted.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // Continue checking - timeout is expected
                } catch (Exception e) {
                    System.err.println("Error waiting for tasks: " + e.getMessage());
                }
            }
        }
    }

    private CompletableFuture<Void> crawlPageAsync(String url, String domain,
                                                   Set<CompletableFuture<Void>> activeTasks) {
        return CompletableFuture.runAsync(() -> {
            if (visitedUrls.contains(url) ||
                    !UrlUtils.isSameDomain(url, domain)) {
                return;
            }

            // Try to acquire a permit - if none available, we've hit the limit
            if (!pageLimitSemaphore.tryAcquire()) {
                return; // Max pages reached
            }

            try {
                System.out.println("[" + Thread.currentThread() + "] Crawling: " + url);

                String htmlContent = fetchPage(url);

                if (htmlContent != null) {
                    visitedUrls.add(url);

                    Set<String> links = extractLinks(htmlContent, url);

                    for (String link : links) {
                        if (pageLimitSemaphore.availablePermits() > 0 &&
                                !visitedUrls.contains(link) &&
                                UrlUtils.isSameDomain(link, domain)) {

                            CompletableFuture<Void> newTask = crawlPageAsync(link, domain, activeTasks);
                            activeTasks.add(newTask);
                        }
                    }
                } else {
                    // Release permit if we didn't successfully process the page
                    pageLimitSemaphore.release();
                }

                Thread.sleep(100);

            } catch (Exception e) {
                pageLimitSemaphore.release();
                System.err.println("Error crawling " + url + ": " + e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    private Semaphore getSemaphoreForDomain(String domain) {
        return domainSemaphores.computeIfAbsent(domain,
                k -> new Semaphore(MAX_CONCURRENT_REQUESTS_PER_DOMAIN));
    }

    private String fetchPage(String url) throws Exception {
        String domain = UrlUtils.extractDomain(url);
        Semaphore semaphore = getSemaphoreForDomain(domain);

        if (!semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
            System.err.println("Rate limit timeout for domain: " + domain);
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(new URI(url))
                                             .header("User-Agent", "Mozilla/5.0 (compatible; SednaWebCrawler/1.0)")
                                             .timeout(Duration.ofSeconds(30)) // Reduced from 45
                                             .GET()
                                             .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (HttpUtils.shouldIgnoreSilently(statusCode)) {
                return null;
            }

            if (HttpUtils.isSuccess(statusCode)) {
                return response.body();
            }

            System.err.println("HTTP " + statusCode + " for " + url);
            return null;

        } finally {
            semaphore.release();
            Thread.sleep(500); // Increased delay to be more respectful
        }
    }

    private Set<String> extractLinks(String htmlContent, String baseUrl) {
        Set<String> links = new HashSet<>();
        Matcher matcher = linkPattern.matcher(htmlContent);

        while (matcher.find()) {
            String link = matcher.group(2);
            try {
                String absoluteUrl = UrlUtils.resolveUrl(link, baseUrl);
                if (absoluteUrl != null && !absoluteUrl.isEmpty()) {
                    links.add(normalizeUrl(absoluteUrl));
                }
            } catch (Exception e) {
                // Skip invalid URLs
            }
        }

        return links;
    }

    @Override
    public void close() {
        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}