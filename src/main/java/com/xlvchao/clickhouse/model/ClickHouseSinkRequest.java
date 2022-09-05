package com.xlvchao.clickhouse.model;

import java.util.List;

public class ClickHouseSinkRequest {
    private final List<Object> data;
    private final Class clazz;
    private int attemptCounter;

    private Exception exception;

    public ClickHouseSinkRequest(List<Object> data, Class clazz, Exception exception) {
        this.data = data;
        this.clazz = clazz;
        this.attemptCounter = 0;
        this.exception = exception;
    }

    public List<Object> getData() {
        return data;
    }

    public void incrementCounter() {
        this.attemptCounter++;
    }

    public int getAttemptCounter() {
        return attemptCounter;
    }

    public Class getClazz() {
        return clazz;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public static final class Builder {
        private List<Object> objects;
        private Class clazz;
        private Exception exception;

        private Builder() {
        }

        public static Builder newClickHouseSinkRequest() {
            return new Builder();
        }

        public Builder withValues(List<Object> objects) {
            this.objects = objects;
            return this;
        }

        public Builder withClass(Class clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder withException(Exception exception) {
            this.exception = exception;
            return this;
        }

        public ClickHouseSinkRequest build() {
            return new ClickHouseSinkRequest(objects, clazz, exception);
        }
    }

    @Override
    public String toString() {
        return "ClickHouseRequestBlank{" +
                "values=" + data +
                ", attemptCounter=" + attemptCounter +
                ", exception=" + exception +
                '}';
    }
}
