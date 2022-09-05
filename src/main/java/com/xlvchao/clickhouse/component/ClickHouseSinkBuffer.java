package com.xlvchao.clickhouse.component;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xlvchao.clickhouse.model.ClickHouseSinkRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClickHouseSinkBuffer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSinkBuffer.class);

    private final ClickHouseWriter writer;
    private final Class clazz;
    private final int batchSize;
    private final long writeTimeoutMillis;
    private final List<Object> localBuffer;
    private final List<CompletableFuture<Boolean>> futures;
    private volatile long lastAddTimeMillis = System.currentTimeMillis();

    private ClickHouseSinkBuffer(ClickHouseWriter writer, long writeTimeout, int batchSize, Class clazz, List<CompletableFuture<Boolean>> futures) {
        this.writer = writer;
        //this.localBuffer = new ArrayList<>();
        this.localBuffer = Collections.synchronizedList(new ArrayList<>());
        this.writeTimeoutMillis = TimeUnit.SECONDS.toMillis(writeTimeout);
        this.batchSize = batchSize;
        this.clazz = clazz;
        this.futures = futures;
    }

    public Class getClazz() {
        return clazz;
    }

    public void put(Object object) {
        localBuffer.add(object);
        tryAddToCommonQueue();
    }

    synchronized void tryAddToCommonQueue() {
        if (satisfyAddCondition()) {
            addToCommonQueue();
            lastAddTimeMillis = System.currentTimeMillis();
        }
    }

    private void addToCommonQueue() {
        List<Object> deepCopy = deepCopy(localBuffer);
        localBuffer.clear();

        ClickHouseSinkRequest sinkRequest = ClickHouseSinkRequest.Builder
                .newClickHouseSinkRequest()
                .withValues(deepCopy)
                .withClass(clazz)
                .build();
        logger.debug("Build sink request success, buffer size = {}", sinkRequest.getData().size());

        writer.put(sinkRequest);
    }

    private boolean satisfyAddCondition() {
        return localBuffer.size() > 0 && (checkSize() || checkTime());
    }

    private boolean checkSize() {
        return localBuffer.size() >= batchSize;
    }

    private boolean checkTime() {
        long current = System.currentTimeMillis();
        return current - lastAddTimeMillis > writeTimeoutMillis;
    }

    private static List<Object> deepCopy(List<Object> original) {
        return Collections.unmodifiableList(new ArrayList<>(original));
    }

    @Override
    public void close() throws Exception {
        logger.info("Sink buffer is shutting down...");
        if (localBuffer != null && localBuffer.size() > 0) {
            addToCommonQueue();
        }
        writer.close();
        logger.info("Sink buffer shutdown complete!");
    }

    public static final class Builder {
        private ClickHouseWriter writer;
        private Class clazz;
        private int batchSize;
        private int writeTimeout;
        private List<CompletableFuture<Boolean>> futures;


        private Builder() {
        }

        public static Builder newClickHouseSinkBuffer() {
            return new Builder();
        }

        public Builder withWriter(ClickHouseWriter writer) {
            this.writer = writer;
            return this;
        }

        public Builder withClass(Class clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder withBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder withWriteTimeout(int writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public Builder withFutures(List<CompletableFuture<Boolean>> futures) {
            this.futures = futures;
            return this;
        }

        public ClickHouseSinkBuffer build() {
            Preconditions.checkNotNull(clazz);
            Preconditions.checkArgument(batchSize > 0);
            Preconditions.checkArgument(writeTimeout > 0);

            return new ClickHouseSinkBuffer(
                    this.writer,
                    this.writeTimeout,
                    this.batchSize,
                    this.clazz,
                    this.futures
            );
        }
    }
}
