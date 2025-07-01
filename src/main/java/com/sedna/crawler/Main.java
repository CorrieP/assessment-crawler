package com.sedna.crawler;

import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Web Crawler");
        System.out.println("====================");

        while (true) {
            System.out.print("Enter a URL (or 'exit' to quit): ");
            String inputUrl = scanner.nextLine().trim();

            if (inputUrl.equals("exit")) {
                System.out.println("Exiting program...");
                break;
            }

            if (inputUrl.isEmpty()) {
                System.out.println("Please enter a valid URL.");
                continue;
            }

            try (WebCrawler crawler = new WebCrawler()) {
                System.out.println("\nCrawling " + inputUrl + " with parallelism ...\n");

                long startTime = System.currentTimeMillis();
                List<String> foundUrls = crawler.crawl(inputUrl);
                long endTime = System.currentTimeMillis();

                System.out.println("\n--- CRAWL RESULTS ---");
                System.out.println("Found " + foundUrls.size() + " pages:");
                System.out.println("Time taken: " + (endTime - startTime) + "ms");

                for (String url : foundUrls) {
                    System.out.println(url);
                }
            }

            System.out.println("\n" + "=".repeat(50) + "\n");
        }

        scanner.close();
    }
}