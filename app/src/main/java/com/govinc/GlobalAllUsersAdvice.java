package com.govinc;

import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalAllUsersAdvice {
    @Autowired
    private UserRepository userRepository;

    @ModelAttribute("allUsers")
    public List<User> addAllUsersToModel() {
        return userRepository.findAll();
    }
}
