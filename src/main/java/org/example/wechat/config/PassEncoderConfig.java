package org.example.wechat.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PassEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 强度 10（默认值，范围4-31，越大越安全但越慢）
        return new BCryptPasswordEncoder(10);
    }
}
