package com.xlvchao.clickhouse.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by lvchao on 2022/9/5 18:15
 */
public class DefaultSinkFailureHandler implements SinkFailureHandler {
    private static final Logger logger = LoggerFactory.getLogger(DefaultSinkFailureHandler.class);

    @Override
    public void handle(List logs, Class clazz) {
        logger.warn("You have {} data insert failed! Class type: {}", logs.size(), clazz.getName());
    }

}
