package com.govinc.user;

import com.govinc.authorization.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/personal-settings")
public class PersonalSettingsController {
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public PersonalSettingsController(AuthorizationService authorizationService, UserRepository userRepository) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Settings getSettings() {
        return new Settings(currentUser().isMailForNewTasks());
    }

    @PostMapping
    public Settings saveSettings(@RequestBody Settings settings) {
        if (settings.mailForNewTasks() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing mail preference");
        }
        User user = currentUser();
        user.setMailForNewTasks(settings.mailForNewTasks());
        userRepository.save(user);
        return new Settings(user.isMailForNewTasks());
    }

    private User currentUser() {
        User user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found");
        }
        return user;
    }

    public record Settings(Boolean mailForNewTasks) {
    }
}