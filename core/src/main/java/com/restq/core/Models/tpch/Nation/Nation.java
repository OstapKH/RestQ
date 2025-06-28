package com.restq.core.Models.tpch.Nation;

import com.restq.core.Models.tpch.Customer.Customer;
import com.restq.core.Models.tpch.Region.Region;
import com.restq.core.Models.tpch.Supplier.Supplier;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;

import java.util.List;

@Entity
@Table(name = "NATION")
public class Nation {

    @Id
    @Column(name = "N_NATIONKEY", nullable = false)
    private Integer nationKey;

    @Column(name = "N_NAME", length = 25, nullable = false, columnDefinition = "bpchar")
    private String name;

    @ManyToOne
    @JoinColumn(name = "N_REGIONKEY", nullable = false)
    private Region region;

    @Column(name = "N_COMMENT", length = 152, nullable = false)
    private String comment;

    @OneToMany(mappedBy = "nation", cascade = CascadeType.ALL)
    private List<Customer> customers;

    @OneToMany(mappedBy = "nation", cascade = CascadeType.ALL)
    private List<Supplier> suppliers;

    public Nation() {
    }

    public Integer getNationKey() {
        return nationKey;
    }

    public void setNationKey(Integer nationKey) {
        this.nationKey = nationKey;
    }

    public List<Supplier> getSuppliers() {
        return suppliers;
    }

    public void setSuppliers(List<Supplier> suppliers) {
        this.suppliers = suppliers;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Getters and Setters
} 
