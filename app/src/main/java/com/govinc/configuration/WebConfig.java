package com.govinc.configuration;

import com.govinc.service.GeneralConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private GeneralConfigService generalConfigService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
          .addResourceHandler("/img/**")
          .addResourceLocations("file:uploads/img/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    int configured = generalConfigService.getSessionTimeoutMinutes();
                    Integer applied = (Integer) session.getAttribute("__appliedTimeoutMinutes");
                    if (applied == null || applied != configured) {
                        session.setMaxInactiveInterval(configured * 60);
                        session.setAttribute("__appliedTimeoutMinutes", configured);
                    }
                }
                return true;
            }
        });
    }
}
