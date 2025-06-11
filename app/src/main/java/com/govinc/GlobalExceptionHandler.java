package com.govinc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Environment env;

    public GlobalExceptionHandler(Environment env) {
        this.env = env;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(HttpServletRequest request, Exception ex) {
        ex.printStackTrace();
        boolean showDetails = false;
        // You can configure this with a specific property or use Spring's built-in
        // profile
        for (String profile : env.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development")) {
                showDetails = true;
                break;
            }
        }

        ModelAndView mav = new ModelAndView();
        mav.setViewName("error");
        mav.addObject("message", ex.getMessage());
        mav.addObject("showDetails", showDetails);

        if (showDetails) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            mav.addObject("details", sw.toString());
        }
        return mav;

    }
}
