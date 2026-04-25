package com.filipnikolov.launchpad.user.dto;

import com.filipnikolov.launchpad.user.model.UserAccount;

import java.util.Map;

public record UserDto(String email, Map<String, Object> preferences) {

    public static UserDto from(UserAccount user) {
        return new UserDto(user.getEmail(), user.getPreferences());
    }
}
