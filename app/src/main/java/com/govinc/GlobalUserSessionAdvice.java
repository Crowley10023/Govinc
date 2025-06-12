package com.govinc;

import com.govinc.session.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUserSessionAdvice {
    @Autowired
    private UserSession userSession;

    @ModelAttribute("userId")
    public String addUserIdToModel() {
        return userSession.getUserId(); // returns null if not set
    }
}
