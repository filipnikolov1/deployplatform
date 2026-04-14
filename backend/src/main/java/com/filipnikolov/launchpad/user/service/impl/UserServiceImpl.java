package com.filipnikolov.launchpad.user.service.impl;

import com.filipnikolov.launchpad.exception.ResourceNotFoundException;
import com.filipnikolov.launchpad.user.model.UserAccount;
import com.filipnikolov.launchpad.user.repository.UserAccountRepository;
import com.filipnikolov.launchpad.user.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String OWNER_EMAIL = "owner@launchpad.local";

    private final UserAccountRepository repository;

    @Value("${app.api-key:}")
    private String appApiKey;

    @PostConstruct
    @Transactional
    public void seedOwnerIfMissing() {
        if (repository.count() > 0) return;
        if (appApiKey == null || appApiKey.isBlank()) {
            log.warn("Skipping owner user seed — app.api-key not configured");
            return;
        }
        UserAccount owner = new UserAccount();
        owner.setEmail(OWNER_EMAIL);
        owner.setApiKeyHash(new BCryptPasswordEncoder().encode(appApiKey));
        owner.setPreferences(new HashMap<>());
        owner.setCreatedAt(LocalDateTime.now());
        repository.save(owner);
        log.info("Seeded owner user account ({})", OWNER_EMAIL);
    }

    @Override
    public UserAccount getOwner() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Owner user not initialised"));
    }

    @Override
    @Transactional
    public UserAccount mergePreferences(Map<String, Object> partial) {
        UserAccount user = getOwner();
        if (user.getPreferences() == null) user.setPreferences(new HashMap<>());
        user.getPreferences().putAll(partial);
        return repository.save(user);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public UserAccount addPin(String appName) {
        UserAccount user = getOwner();
        if (user.getPreferences() == null) user.setPreferences(new HashMap<>());
        List<String> pinned = (List<String>) user.getPreferences()
                .computeIfAbsent("pinned_apps", k -> new ArrayList<String>());
        if (!pinned.contains(appName)) pinned.add(appName);
        return repository.save(user);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public UserAccount removePin(String appName) {
        UserAccount user = getOwner();
        if (user.getPreferences() == null) return user;
        List<String> pinned = (List<String>) user.getPreferences().get("pinned_apps");
        if (pinned != null) pinned.remove(appName);
        return repository.save(user);
    }
}
