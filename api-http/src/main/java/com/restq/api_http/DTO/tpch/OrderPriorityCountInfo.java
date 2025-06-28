package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderPriorityCountInfo {

    @JsonProperty("o_orderpriority")
    private String orderPriority;

    @JsonProperty("order_count")
    private Long orderCount;

    public OrderPriorityCountInfo(String orderPriority, Long orderCount) {
        this.orderPriority = orderPriority;
        this.orderCount = orderCount;
    }

    public String getOrderPriority() {
        return orderPriority;
    }

    public void setOrderPriority(String orderPriority) {
        this.orderPriority = orderPriority;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }
}
