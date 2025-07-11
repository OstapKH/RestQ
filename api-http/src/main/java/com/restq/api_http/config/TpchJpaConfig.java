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
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCH")
@EnableJpaRepositories(
    basePackages = "com.restq.api_http.Repositories.tpch",
    entityManagerFactoryRef = "tpchEntityManagerFactory",
    transactionManagerRef = "tpchTransactionManager"
)
public class TpchJpaConfig {

    @Primary
    @Bean(name = "tpchEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tpchEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.restq.core.Models.tpch")
                .persistenceUnit("tpch")
                .build();
    }

    @Primary
    @Bean(name = "tpchTransactionManager")
    public PlatformTransactionManager tpchTransactionManager(
            @Qualifier("tpchEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
} 
