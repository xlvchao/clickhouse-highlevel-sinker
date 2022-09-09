package com.xlvchao.clickhouse.model;

import com.xlvchao.clickhouse.util.TableUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by lvchao on 2022/9/9 14:25
 */
public class ClickHouseSqlFactory {
    private static final Map<Class, String> sqls = new ConcurrentHashMap<>();

    public static void put(Class clazz) {
        sqls.put(clazz, TableUtil.genSqlTemplate(clazz));
    }

    public static String get(Class clazz) {
        return sqls.get(clazz);
    }

}
