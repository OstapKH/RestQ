package com.restq.core.Models.tpcc.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "ITEM")
public class Item {

    @Id
    @Column(name = "I_ID")
    private Integer itemId;

    @Column(name = "I_IM_ID", nullable = false)
    private Integer imageId;

    @Column(name = "I_NAME", length = 24, nullable = false)
    private String name;

    @Column(name = "I_PRICE", nullable = false, precision = 5, scale = 2)
    private BigDecimal price;

    @Column(name = "I_DATA", length = 50, nullable = false)
    private String data;

    public Item() {
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getImageId() {
        return imageId;
    }

    public void setImageId(Integer imageId) {
        this.imageId = imageId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
} 
