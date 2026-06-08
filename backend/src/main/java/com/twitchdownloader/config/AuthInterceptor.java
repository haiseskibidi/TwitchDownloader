package com.twitchdownloader.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        
        // Exclude authentication paths
        if (uri.startsWith("/api/auth/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return true;
        }

        // Return 401 Unauthorized for unauthenticated API requests
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Unauthorized. Please log in.\"}");
        return false;
    }
}
