package com.govinc.user;

import com.govinc.session.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserSession userSession;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new User());
        return "user_form";
    }

    @PostMapping
    public String createUser(@ModelAttribute User user) {
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            model.addAttribute("user", user.get());
            return "user_form";
        } else {
            return "redirect:/users";
        }
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable Long id, @ModelAttribute User user) {
        user.setId(id);
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "redirect:/users";
    }

    // --- SESSION USER SETTING ----
    @PostMapping("/set-session-user")
    public String setSessionUser(@RequestParam("userId") String userId, @RequestParam(value = "redirect", required = false) String redirect) {
        userSession.setUserId(userId);
        if (redirect != null && !redirect.isEmpty()) {
            return "redirect:" + redirect;
        }
        return "redirect:/";
    }
}
