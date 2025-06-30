package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class SupplierPartInfo {

    @JsonProperty("s_acctbal")
    private BigDecimal accountBalance;

    @JsonProperty("s_name")
    private String supplierName;

    @JsonProperty("n_name")
    private String nationName;

    @JsonProperty("p_partkey")
    private Integer partKey;

    @JsonProperty("p_mfgr")
    private String manufacturer;

    @JsonProperty("s_address")
    private String address;

    @JsonProperty("s_phone")
    private String phone;

    @JsonProperty("s_comment")
    private String comment;

    public SupplierPartInfo(BigDecimal accountBalance, String supplierName, String nationName, Integer partKey, String manufacturer, String address, String phone, String comment) {
        this.accountBalance = accountBalance;
        this.supplierName = supplierName;
        this.nationName = nationName;
        this.partKey = partKey;
        this.manufacturer = manufacturer;
        this.address = address;
        this.phone = phone;
        this.comment = comment;
    }
}