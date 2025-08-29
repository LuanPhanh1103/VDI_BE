package com.mobifone.vdi.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")  // Kích hoạt auditing và cấu hình Bean "auditorAware"
public class JpaConfig {
    @Bean(name = "auditorAware")
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();  // Đảm bảo bean này có tên "auditorAware"
    }
}