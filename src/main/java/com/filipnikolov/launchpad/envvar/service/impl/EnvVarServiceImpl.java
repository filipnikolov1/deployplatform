package com.filipnikolov.launchpad.envvar.service.impl;

import com.filipnikolov.launchpad.envvar.crypto.EncryptionService;
import com.filipnikolov.launchpad.envvar.model.EnvVar;
import com.filipnikolov.launchpad.envvar.repository.EnvVarRepository;
import com.filipnikolov.launchpad.envvar.service.EnvVarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnvVarServiceImpl implements EnvVarService {

    private final EnvVarRepository envVarRepository;
    private final EncryptionService encryptionService;

    @Override
    public void setEnvVar(String appName, String key, String value) {
        EnvVar envVar = envVarRepository.findByAppNameAndVarKey(appName, key)
                .orElseGet(() -> {
                    EnvVar newVar = new EnvVar();
                    newVar.setAppName(appName);
                    newVar.setVarKey(key);
                    newVar.setCreatedAt(LocalDateTime.now());
                    return newVar;
                });

        envVar.setEncryptedValue(encryptionService.encrypt(value));
        envVar.setUpdatedAt(LocalDateTime.now());
        envVarRepository.save(envVar);
    }

    @Override
    public Map<String, String> getEnvVars(String appName) {
        Map<String, String> result = new LinkedHashMap<>();
        for (EnvVar envVar : envVarRepository.findByAppName(appName)) {
            result.put(envVar.getVarKey(), encryptionService.decrypt(envVar.getEncryptedValue()));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteEnvVar(String appName, String key) {
        envVarRepository.deleteByAppNameAndVarKey(appName, key);
    }
}
