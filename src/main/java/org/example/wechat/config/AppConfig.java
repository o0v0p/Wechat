package org.example.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.avatar")
public class AppConfig {
    private String defaultPath;
    private String defaultUrl;
}