package com.restq.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCC", matchIfMissing = true)
@EntityScan(basePackages = {
    "com.restq.core.Models.tpcc.Warehouse",
    "com.restq.core.Models.tpcc.District", 
    "com.restq.core.Models.tpcc.Customer",
    "com.restq.core.Models.tpcc.Item",
    "com.restq.core.Models.tpcc.Stock",
    "com.restq.core.Models.tpcc.NewOrder",
    "com.restq.core.Models.tpcc.History",
    "com.restq.core.Models.tpcc.OrderLine"
})
public class TPCCEntityConfig {
} 
