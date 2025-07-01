package com.sedna.crawler.utils;

import java.util.Set;

public class HttpUtils {
    //Getting a lot of 400 or 3* errors on Sedna
    private static final Set<Integer> SILENTLY_IGNORED_STATUS_CODES = Set.of(
            400, 301, 302, 307, 308, 404, 410
    );

    private static final Set<Integer> SUCCESS_STATUS_CODES = Set.of(
            200, 201
    );

    public static boolean shouldIgnoreSilently(int statusCode) {
        return SILENTLY_IGNORED_STATUS_CODES.contains(statusCode);
    }

    public static boolean isSuccess(int statusCode) {
        return SUCCESS_STATUS_CODES.contains(statusCode);
    }
}
