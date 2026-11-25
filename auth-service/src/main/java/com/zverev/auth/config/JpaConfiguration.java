package com.zverev.auth.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.zverev.shared.entity")
@EnableJpaRepositories("com.zverev.shared.repository")
public class JpaConfiguration {

}
