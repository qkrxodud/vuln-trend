package com.vulntrend.batch.infrastructure.rules;

import com.vulntrend.batch.application.rules.RulesBean;
import com.vulntrend.batch.domain.library.EntityType;

// 계층 방향(5): infrastructure 는 application 을 보면 안 된다(domain 은 허용)
public class LayerLeak {
}
