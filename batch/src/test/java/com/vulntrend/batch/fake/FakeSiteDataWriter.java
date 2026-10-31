package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 사이트 데이터 쓰기 흉내. 경로마다 마지막으로 받은 문서와 쓴 순서를 기억한다. 이미 있는 파일을 넣어 둘 수 있고,
 * 같은 문서를 다시 받으면 그대로 둔 것으로 센다.
 */
public class FakeSiteDataWriter implements SiteDataWriter {

    private final Map<String, Object> documents = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Set<String> files = new TreeSet<>();
    private final List<String> removed = new ArrayList<>();

    public FakeSiteDataWriter existing(String path) {
        files.add(path);
        return this;
    }

    @Override
    public WriteOutcome write(DataFilePath path, Object document) {
        order.add(path.getValue());
        files.add(path.getValue());
        Object before = documents.put(path.getValue(), document);
        return document.equals(before) ? WriteOutcome.UNCHANGED : WriteOutcome.WRITTEN;
    }

    @Override
    public List<DataFilePath> listFolder(String folder) {
        return files.stream().filter(path -> path.startsWith(folder + "/")).map(DataFilePath::of).toList();
    }

    @Override
    public boolean remove(DataFilePath path) {
        removed.add(path.getValue());
        return files.remove(path.getValue());
    }

    public <T> Optional<T> document(String path, Class<T> type) {
        return Optional.ofNullable(documents.get(path)).map(type::cast);
    }

    public Map<String, Object> documents() {
        return documents;
    }

    public List<String> order() {
        return order;
    }

    public List<String> removed() {
        return removed;
    }

    public Set<String> files() {
        return files;
    }
}
