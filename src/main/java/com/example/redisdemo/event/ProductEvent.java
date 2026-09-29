package com.example.redisdemo.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public class ProductEvent implements Serializable {

    private String action;
    private Long id;
    private String name;
    private BigDecimal price;
    private Instant timestamp;

    public ProductEvent() {
    }

    public ProductEvent(String action, Long id, String name, BigDecimal price) {
        this.action = action;
        this.id = id;
        this.name = name;
        this.price = price;
        this.timestamp = Instant.now();
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ProductEvent{action='" + action + "', id=" + id + ", name='" + name + "', price=" + price + ", timestamp=" + timestamp + "}";
    }
}
