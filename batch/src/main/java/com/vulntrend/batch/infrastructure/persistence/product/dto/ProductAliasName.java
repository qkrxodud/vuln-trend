package com.vulntrend.batch.infrastructure.persistence.product.dto;

/** 원래 이름 하나. CPE 면 vendor·product 원문, KEV 면 vendorProject·product 원문 */
public record ProductAliasName(String vendor, String product) {
}
