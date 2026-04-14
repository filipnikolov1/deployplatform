package com.filipnikolov.launchpad.github.dto;

import java.util.List;

public record CommitsAhead(
        Integer count,
        List<CommitInfo> commits,
        String compareUrl
) {
    public record CommitInfo(String sha, String message, String author, String date, String url) {}
}
