package com.restq.api_http.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCC", matchIfMissing = false)
@EnableJpaRepositories(
    basePackages = "com.restq.api_http.Repositories.tpcc",
    entityManagerFactoryRef = "tpccEntityManagerFactory",
    transactionManagerRef = "tpccTransactionManager"
)
public class TpccJpaConfig {

    @Primary
    @Bean(name = "tpccEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tpccEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.restq.core.Models.tpcc")
                .persistenceUnit("tpcc")
                .build();
    }

    @Primary
    @Bean(name = "tpccTransactionManager")
    public PlatformTransactionManager tpccTransactionManager(
            @Qualifier("tpccEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
} 
