package dev.filipnikolov.vector.envvar.service.impl;

import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.envvar.model.EnvVar;
import dev.filipnikolov.vector.envvar.repository.EnvVarRepository;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
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
