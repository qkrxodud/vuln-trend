package com.vulntrend.batch.domain.publish;

/**
 * 사이트 데이터 게시 창구(기획서 6장 9번, WBS 4.3). 구현은 infrastructure/publish 의 GitPusher.
 * 수집(1~4단계)이 모두 성공하고 데이터 파일을 다 쓴 실행에서만 부른다. 실패하면 PublishException 을 던진다.
 */
public interface Publisher {

    PublishResult publish();
}
