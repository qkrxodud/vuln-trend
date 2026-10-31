package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.gate.RepositoryHistory;
import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 저장소 기록 흉내. 넣어 둔 데이터 커밋(최신부터)과 크기를 돌려주고, 읽지 못하는 저장소도 흉내 낸다 */
public class FakeRepositoryHistory implements RepositoryHistory {

    private final List<DataCommit> commits = new ArrayList<>();
    private RepoSize size = new RepoSize(0, 0);
    private boolean unreadable;
    private boolean sizeUnreadable;

    public FakeRepositoryHistory commit(String runAt, int changedFiles) {
        commits.add(new DataCommit(LocalDateTime.parse(runAt), changedFiles));
        return this;
    }

    public FakeRepositoryHistory size(long packedBytes, long looseBytes) {
        this.size = new RepoSize(packedBytes, looseBytes);
        return this;
    }

    public FakeRepositoryHistory sizeUnreadable() {
        this.sizeUnreadable = true;
        return this;
    }

    public FakeRepositoryHistory unreadable() {
        this.unreadable = true;
        return this;
    }

    @Override
    public List<DataCommit> recentDataCommits(int limit) {
        failIfUnreadable();
        return commits.stream().limit(limit).toList();
    }

    @Override
    public RepoSize size() {
        failIfUnreadable();
        if (sizeUnreadable) {
            throw new SourceFetchException("git", "size could not be read.");
        }
        return size;
    }

    private void failIfUnreadable() {
        if (unreadable) {
            throw new SourceFetchException("git", "repository could not be read.");
        }
    }
}
