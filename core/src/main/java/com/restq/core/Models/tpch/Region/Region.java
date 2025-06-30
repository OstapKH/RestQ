package com.restq.core.Models.tpch.Region;

import com.restq.core.Models.tpch.Nation.Nation;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;

import java.util.List;

@Entity
@Table(name = "REGION")
public class Region {

    @Id
    @Column(name = "R_REGIONKEY", nullable = false)
    private Integer regionKey;

    @Column(name = "R_NAME", length = 25, nullable = false, columnDefinition = "bpchar")
    private String name;

    @Column(name = "R_COMMENT", length = 152, nullable = false)
    private String comment;

    @OneToMany(mappedBy = "region", cascade = CascadeType.ALL)
    private List<Nation> nations;

    public Region() {
    }

    public Integer getRegionKey() {
        return regionKey;
    }

    public void setRegionKey(Integer regionKey) {
        this.regionKey = regionKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<Nation> getNations() {
        return nations;
    }

    public void setNations(List<Nation> nations) {
        this.nations = nations;
    }
} 
