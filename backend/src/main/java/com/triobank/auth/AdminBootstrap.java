package com.triobank.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AdminBootstrapService} once the app has started.
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
class AdminBootstrap implements ApplicationRunner {

    private final AdminBootstrapService service;

    AdminBootstrap(AdminBootstrapService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.bootstrap();
    }

}
