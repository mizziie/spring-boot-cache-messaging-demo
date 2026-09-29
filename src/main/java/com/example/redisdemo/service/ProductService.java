package com.example.redisdemo.service;

import com.example.redisdemo.config.RabbitConfig;
import com.example.redisdemo.event.ProductEvent;
import com.example.redisdemo.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    public static final String PRODUCT_CACHE = "products";
    public static final String PRODUCT_LIST_CACHE = "productList";

    private final Map<Long, Product> db = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);
    private final RabbitTemplate rabbitTemplate;

    public ProductService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        createInternal(new Product(null, "Keyboard", new BigDecimal("59.99")));
        createInternal(new Product(null, "Mouse", new BigDecimal("29.99")));
        createInternal(new Product(null, "Monitor", new BigDecimal("199.99")));
    }

    @Cacheable(cacheNames = PRODUCT_CACHE, key = "#id")
    public Product getById(Long id) {
        log.info("Fetching product id={} from database", id);
        simulateSlowQuery();
        Product product = db.get(id);
        if (product == null) {
            log.warn("Product id={} not found", id);
            throw new ProductNotFoundException(id);
        }
        log.info("Found product id={}: {}", id, product);
        return product;
    }

    @Cacheable(cacheNames = PRODUCT_LIST_CACHE, key = "'all'")
    public List<Product> getAll() {
        log.info("Fetching all products from database");
        simulateSlowQuery();
        List<Product> products = new ArrayList<>(db.values());
        log.info("Returning {} products", products.size());
        return products;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public Product create(Product request) {
        log.info("Creating product: {}", request);
        Product product = createInternal(request);
        log.info("Product created: id={}", product.getId());
        publishProductEvent("CREATE", product);
        return product;
    }

    private Product createInternal(Product request) {
        long id = idGen.incrementAndGet();
        Product product = new Product(id, request.getName(), request.getPrice());
        db.put(id, product);
        return product;
    }

    @Caching(
            put = {@CachePut(cacheNames = PRODUCT_CACHE, key = "#id")},
            evict = {@CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)}
    )
    public Product update(Long id, Product request) {
        log.info("Updating product id={}: {}", id, request);
        if (!db.containsKey(id)) {
            log.warn("Cannot update: product id={} not found", id);
            throw new ProductNotFoundException(id);
        }
        Product updated = new Product(id, request.getName(), request.getPrice());
        db.put(id, updated);
        log.info("Product id={} updated", id);
        publishProductEvent("UPDATE", updated);
        return updated;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id"),
            @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void delete(Long id) {
        log.info("Deleting product id={}", id);
        Product removed = db.get(id);
        if (removed == null) {
            log.warn("Cannot delete: product id={} not found", id);
            throw new ProductNotFoundException(id);
        }
        publishProductEvent("DELETE", removed);
        db.remove(id);
        log.info("Product id={} deleted", id);
    }

    private void publishProductEvent(String action, Product product) {
        ProductEvent event = new ProductEvent(action, product.getId(), product.getName(), product.getPrice());
        log.info("Publishing {} event for product id={}", action, product.getId());
        rabbitTemplate.convertAndSend(RabbitConfig.PRODUCT_EXCHANGE, RabbitConfig.PRODUCT_ROUTING_KEY, event);
    }

    private void simulateSlowQuery() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
