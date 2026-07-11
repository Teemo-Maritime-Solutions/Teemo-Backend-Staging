package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.ai.configuration.NoaaAlertsProperties;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchProperties;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.shared.infrastructure.configuration.JwtProperties;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = "org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend")
@EnableMongoRepositories
@EnableMongoAuditing
@Configuration
@EnableConfigurationProperties({JwtProperties.class, NoaaAlertsProperties.class, GlobalFishingWatchProperties.class})
public class UpcPre202501Cc1asi07324441TeemoSolutionsBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpcPre202501Cc1asi07324441TeemoSolutionsBackEndApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
