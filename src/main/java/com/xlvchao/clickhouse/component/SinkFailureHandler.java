package com.xlvchao.clickhouse.component;

import java.util.List;

/**
 * Created by lvchao on 2022/9/5 18:13
 */
public interface SinkFailureHandler {
    void handle(List data, Class clazz);
}
