package dev.filipnikolov.vector.user.service;

import dev.filipnikolov.vector.user.model.UserAccount;

import java.util.Map;

public interface UserService {

    UserAccount getOwner();

    UserAccount mergePreferences(Map<String, Object> partial);

    UserAccount addPin(String appName);

    UserAccount removePin(String appName);
}
