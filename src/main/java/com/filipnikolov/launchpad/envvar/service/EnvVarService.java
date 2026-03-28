package com.filipnikolov.launchpad.envvar.service;

import java.util.Map;

public interface EnvVarService {

    void setEnvVar(String appName, String key, String value);

    Map<String, String> getEnvVars(String appName);

    void deleteEnvVar(String appName, String key);
}
