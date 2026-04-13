package com.filipnikolov.launchpad.user.service;

import com.filipnikolov.launchpad.user.model.UserAccount;

import java.util.Map;

public interface UserService {

    UserAccount getOwner();

    UserAccount mergePreferences(Map<String, Object> partial);

    UserAccount addPin(String appName);

    UserAccount removePin(String appName);
}
