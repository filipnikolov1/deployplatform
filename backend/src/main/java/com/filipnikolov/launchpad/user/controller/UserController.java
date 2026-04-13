package com.filipnikolov.launchpad.user.controller;

import com.filipnikolov.launchpad.user.dto.UserDto;
import com.filipnikolov.launchpad.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserDto me() {
        return UserDto.from(userService.getOwner());
    }

    @PatchMapping("/preferences")
    public UserDto updatePrefs(@RequestBody Map<String, Object> partial) {
        return UserDto.from(userService.mergePreferences(partial));
    }

    @PostMapping("/pin/{appName}")
    public UserDto pin(@PathVariable String appName) {
        return UserDto.from(userService.addPin(appName));
    }

    @DeleteMapping("/pin/{appName}")
    public UserDto unpin(@PathVariable String appName) {
        return UserDto.from(userService.removePin(appName));
    }
}
