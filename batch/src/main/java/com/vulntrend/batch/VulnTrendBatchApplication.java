package com.vulntrend.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VulnTrendBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(VulnTrendBatchApplication.class, args);
    }
}
