package com.example.e_hospital.management.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
public class DatabaseSafetyConfiguration {
    // Validate resolved properties before DataSource, Flyway or JPA beans are created.
    @Bean
    static BeanFactoryPostProcessor databaseSafetyCheck(ConfigurableEnvironment environment) {
        return beanFactory -> DatabaseSafetyPolicy.validate(environment);
    }
}
