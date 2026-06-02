package com.ticketing.config;

import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {

    public static Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
