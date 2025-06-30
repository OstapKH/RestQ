package com.restq.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCH")
@EntityScan(basePackages = {
    "com.restq.core.Models.tpch.Customer",
    "com.restq.core.Models.tpch.LineItem",
    "com.restq.core.Models.tpch.Nation",
    "com.restq.core.Models.tpch.Orders",
    "com.restq.core.Models.tpch.Part",
    "com.restq.core.Models.tpch.PartSupp",
    "com.restq.core.Models.tpch.Region",
    "com.restq.core.Models.tpch.Supplier"
})
public class TPCHEntityConfig {
} 
