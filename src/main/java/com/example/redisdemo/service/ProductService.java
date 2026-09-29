package com.example.redisdemo.service;

import com.example.redisdemo.config.RabbitConfig;
import com.example.redisdemo.event.ProductEvent;
import com.example.redisdemo.model.Product;
import com.example.redisdemo.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    public static final String PRODUCT_CACHE = "products";
    public static final String PRODUCT_LIST_CACHE = "productList";

    private final ProductRepository productRepository;
    private final RabbitTemplate rabbitTemplate;

    public ProductService(ProductRepository productRepository, RabbitTemplate rabbitTemplate) {
        this.productRepository = productRepository;
        this.rabbitTemplate = rabbitTemplate;
        seedData();
    }

    private void seedData() {
        if (productRepository.count() == 0) {
            productRepository.save(new Product(null, "Keyboard", new BigDecimal("59.99")));
            productRepository.save(new Product(null, "Mouse", new BigDecimal("29.99")));
            productRepository.save(new Product(null, "Monitor", new BigDecimal("199.99")));
            log.info("Seeded initial products");
        }
    }

    @Cacheable(cacheNames = PRODUCT_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public Product getById(Long id) {
        log.info("Fetching product id={} from database", id);
        simulateSlowQuery();
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product id={} not found", id);
                    return new ProductNotFoundException(id);
                });
    }

    @Cacheable(cacheNames = PRODUCT_LIST_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<Product> getAll() {
        log.info("Fetching all products from database");
        simulateSlowQuery();
        List<Product> products = productRepository.findAll();
        log.info("Returning {} products", products.size());
        return products;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public Product create(Product request) {
        log.info("Creating product: {}", request);
        Product product = productRepository.save(new Product(null, request.getName(), request.getPrice()));
        log.info("Product created: id={}", product.getId());
        publishProductEvent("CREATE", product);
        return product;
    }

    @Transactional
    @Caching(
            put = {@CachePut(cacheNames = PRODUCT_CACHE, key = "#id")},
            evict = {@CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)}
    )
    public Product update(Long id, Product request) {
        log.info("Updating product id={}: {}", id, request);
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cannot update: product id={} not found", id);
                    return new ProductNotFoundException(id);
                });
        existing.setName(request.getName());
        existing.setPrice(request.getPrice());
        Product updated = productRepository.save(existing);
        log.info("Product id={} updated", id);
        publishProductEvent("UPDATE", updated);
        return updated;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id"),
            @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void delete(Long id) {
        log.info("Deleting product id={}", id);
        Product removed = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cannot delete: product id={} not found", id);
                    return new ProductNotFoundException(id);
                });
        publishProductEvent("DELETE", removed);
        productRepository.delete(removed);
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
