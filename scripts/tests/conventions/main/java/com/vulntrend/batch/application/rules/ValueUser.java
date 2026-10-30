package com.vulntrend.batch.application.rules;

import org.springframework.beans.factory.annotation.Value;
import lombok.Setter;

// 다른 규칙 회귀: @Value(6), @Setter(4), import 순서(8 — org 가 lombok 보다 앞)
public class ValueUser {

    @Value("${clarity.x}")
    private String x;

    @Setter
    private String y;
}
