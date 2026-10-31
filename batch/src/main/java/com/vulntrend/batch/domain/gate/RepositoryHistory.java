package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import java.util.List;

/** 저장소 기록 읽기(Gate G). 읽기만 한다 */
public interface RepositoryHistory {

    /** 최근 데이터 커밋, 최신부터 limit 개 */
    List<DataCommit> recentDataCommits(int limit);

    RepoSize size();
}
