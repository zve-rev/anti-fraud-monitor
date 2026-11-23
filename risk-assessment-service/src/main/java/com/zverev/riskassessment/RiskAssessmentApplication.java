package com.zverev.riskassessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.zverev.entity")
@EnableJpaRepositories("com.zverev.repository")
public class RiskAssessmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiskAssessmentApplication.class, args);
    }
}