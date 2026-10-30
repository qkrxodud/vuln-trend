package com.vulntrend.batch.application.probe;

public class C11BraceInStringComment {

    private static final String URL = "https://example.com/{id}"; // comment {
    private static final String GLOB = "/api/**";

    /**
     * javadoc
     */
    static class After {
        After() {
        }
    }
}
