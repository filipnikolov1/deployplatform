package com.filipnikolov.launchpad.github.service;

import com.filipnikolov.launchpad.github.dto.CommitsAhead;

public interface GitHubService {

    /**
     * Returns commits in (base..head) for the given repo. Returns a fallback record
     * with null count and an empty list when no token is configured or the call fails.
     */
    CommitsAhead compare(String repoUrl, String base, String head);
}
