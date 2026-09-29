package com.example.redisdemo.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class Product implements Serializable {

    private Long id;
    private String name;
    private BigDecimal price;

    public Product() {
    }

    public Product(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
