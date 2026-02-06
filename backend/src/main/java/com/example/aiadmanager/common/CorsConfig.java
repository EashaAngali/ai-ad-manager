package com.example.aiadmanager.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(
                                "http://localhost:5173",
                                "https://ai-ad-manager.onrender.com",
                                "https://ai-ad-manager-frontend.onrender.com"
                        )
                        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
