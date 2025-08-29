package com.mobifone.vdi.configuration;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // Lấy người dùng từ SecurityContext (nếu có)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.of(authentication.getName()); // Trả về tên người dùng
        }
        return Optional.of("system");  // Nếu không có người dùng, trả về giá trị mặc định
    }
}
