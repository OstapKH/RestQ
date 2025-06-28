package com.restq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = {JpaRepositoriesAutoConfiguration.class})
@ComponentScan(
    basePackages = "com.restq",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, 
        classes = com.restq.InitDB.class
    )
)
public class APIApplication {
    public static void main(String[] args) {
        SpringApplication.run(APIApplication.class, args);
    }
}
