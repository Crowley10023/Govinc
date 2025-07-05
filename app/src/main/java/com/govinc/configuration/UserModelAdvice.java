package com.govinc.configuration;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class UserModelAdvice {
    @ModelAttribute
    public void addUserAttributes(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetails) {
                UserDetails user = (UserDetails) principal;
                model.addAttribute("userName", user.getUsername());
                model.addAttribute("userId", user.getUsername());
            } else if (principal instanceof String && !"anonymousUser".equals(principal)) {
                model.addAttribute("userName", principal);
                model.addAttribute("userId", principal);
            }
        }
    }
}
