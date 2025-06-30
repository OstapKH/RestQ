package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class VolumeShippingOfNations {
    @JsonProperty("supp_nation")
    private String suppNation;

    @JsonProperty("cust_nation")
    private String custNation;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("revenue")
    private BigDecimal revenue;

    public VolumeShippingOfNations(String suppNation, String custNation, Integer year, BigDecimal revenue) {
        this.suppNation = suppNation;
        this.custNation = custNation;
        this.year = year;
        this.revenue = revenue;
    }
}
