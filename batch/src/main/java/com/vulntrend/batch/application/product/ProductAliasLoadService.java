package com.vulntrend.batch.application.product;

import com.vulntrend.batch.domain.product.ProductAliasCatalog;
import com.vulntrend.batch.domain.product.ProductAliasRepository;
import com.vulntrend.batch.domain.product.ProductAliases;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 기동 시 product-alias.yml 을 product_alias 에 적재한다(WBS 0.5).
 * ApplicationStartedEvent 는 Flyway 가 저장 구조를 만든 뒤, 하루 한 번 실행(BatchRunner) 전에 온다 —
 * 그래서 대표 제품 선정(WBS 2.3)이 언제나 최신 표를 본다. 테스트 프로필(batch.runner.enabled=false)에서도 돈다.
 * 원본이 잘못 적혔으면 시작을 멈춘다. 반쯤 맞는 표로 하루치 대표 제품을 매기지 않기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAliasLoadService {

    private final ProductAliasCatalog catalog;
    private final ProductAliasRepository repository;

    @EventListener(ApplicationStartedEvent.class)
    public void loadOnStartup() {
        load();
    }

    public int load() {
        ProductAliases aliases = catalog.load();
        int loaded = repository.replaceAll(aliases);
        log.info("Loaded {} product aliases for {} products.", loaded, aliases.productCount());
        return loaded;
    }
}
