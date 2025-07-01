package com.sedna.crawler.utils;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlUtils {

    public static String normalizeUrl(String url) {
        try {
            if (!isValidUrl(url)) throw new URISyntaxException(url, "Invalid URL");

            URI parsedUri = new URI(url);
            String normalized = parsedUri.getScheme() + "://" +
                    parsedUri.getHost().toLowerCase();

            if (parsedUri.getPort() != -1 &&
                    !((parsedUri.getPort() == 80 && "http".equals(parsedUri.getScheme())) ||
                            (parsedUri.getPort() == 443 && "https".equals(parsedUri.getScheme())))) {
                normalized += ":" + parsedUri.getPort();
            }

            String path = parsedUri.getPath();

            // Normalize path handling
            if (path == null || path.isEmpty() || path.equals("/")) {
                normalized += "/";  // Always use "/" for root
            } else {
                // Remove trailing slash for non-root paths
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                normalized += path;
            }

            if (parsedUri.getQuery() != null) {
                normalized += "?" + parsedUri.getQuery();
            }

            return normalized;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    public static String extractDomain(String url) throws URISyntaxException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        URI parsedUri = new URI(url);
        String host = parsedUri.getHost();
        if (host == null) {
            throw new URISyntaxException(url, "No host found in URL");
        }
        return host.toLowerCase();
    }

    public static boolean isSameDomain(String url, String targetDomain) {
        try {
            String domain = extractDomain(url);
            return domain.equals(targetDomain) || domain.endsWith("." + targetDomain);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String resolveUrl(String link, String baseUrl) {
        try {
            if (link.startsWith("http://") || link.startsWith("https://")) {
                return link;
            } else if (link.startsWith("//")) {
                URI base = new URI(baseUrl);
                return base.getScheme() + ":" + link;
            } else if (link.startsWith("/")) {
                URI base = new URI(baseUrl);
                return base.getScheme() + "://" + base.getHost() +
                        (base.getPort() != -1 ? ":" + base.getPort() : "") + link;
            } else if (!link.startsWith("#") && !link.startsWith("mailto:") &&
                    !link.startsWith("javascript:")) {
                URI base = new URI(baseUrl);
                URI resolved = base.resolve(link);
                return resolved.toString();
            }
        } catch (URISyntaxException e) {
            // Invalid URI
        }
        return null;
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme)) &&
                    uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
