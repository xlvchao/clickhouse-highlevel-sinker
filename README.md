# 简介

ClickHouse批量写SDK，支持在**Springboot、Flink**等框架中使用，承担着我们团队日均千亿级别数据的落库任务，人性化、高性能、极简依赖！



# 背景
为了极大地减小ZK的压力，采用“读写分离”方案，即写本地表、读分布式表；写本地表时采用的默认负载均衡策略是轮询，这样可使数据比较均匀的分布在各个分片上，尽量避免数据倾斜的情况发生！



# 使用方式

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.xlvchao.clickhouse</groupId>
    <artifactId>clickhouse-highlevel-sinker</artifactId>
    <version>1.0.7</version>
</dependency>
```



## 2. Springboot中使用

**定义POJO：**

```java
import TableName;
import lombok.Data;

@Data
@TableName("aiops_local.interfacelog") //想要达到前文介绍的读写分离效果，这里必须使用本地表。格式：database.table
public class InterfaceLog {
    private String product; //业务
    private String service; //服务
    private String itf; //接口
    private String accountErrorCode;
    private String addn;
    private Long aggregateDelay;
    private Long avgAggregateDelay;
    private Long nonAggregatedDelay;
    private Long latency;
    private Double avgLatency;
    private String destinationId;
    private String errorDetail;
    private Long fail;
    private String ip;
    private String ipCity;
    private String ipCountry;
    private String ipProvince;
    private String itfGroup;
    private String returnCode;
    private String sourceId;
    private Long success;
    private Long total;
    private Long totalInterfaceLogAggregateDelay;
    private String type;
    private LocalDateTime sysTime;
    private LocalDateTime time;
}
```

**注册Bean:**

```java
import ClickHouseSinkManager;
import Sink;
import com.hihonor.basecloud.cloudsoa.security.cipher.AESCryptor;
import com.hihonor.basecloud.cloudsoa.security.cipher.CipherTextCodec;
import com.hihonor.basecloud.cloudsoa.security.cipher.CryptoAlg;
import com.hihonor.basecloud.cloudsoa.security.exception.CipherException;
import com.hihonor.basecloud.cloudsoa.security.exception.KeyProviderException;
import com.hihonor.basecloud.cloudsoa.security.storage.KeyStorage;
import com.hihonor.basecloud.cloudsoa.security.workkey.WorkKeyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Properties;

@Configuration
@Slf4j
public class ClickHouseConfig {

    @Resource
    private ClickHouseSinkManager clickHouseSinkManager;

    //注意：一个微服务实例中，只能注册一个ClickHouseSinkManager！！！
    @Bean
    public ClickHouseSinkManager clickHouseSinkManager() {
        Properties properties = new Properties();
        properties.put("clickhouse.hikari.username", "username");
        properties.put("clickhouse.hikari.password", "password");
        properties.put("clickhouse.hikari.address", "10.68.178.71:8123"); //代理地址 or 集群中任一节点地址
        properties.put("clickhouse.hikari.minimumIdle", "5");
        properties.put("clickhouse.hikari.maximumPoolSize", "10");

        properties.put("clickhouse.sink.queueMaxCapacity", "1000"); //公共消费队列容量（即最大能承载多少批次），默认是1000
        properties.put("clickhouse.sink.writeTimeout", "3"); //写超时时间（单位秒），即当Buffer中数据超过该时间还未被flush则立马刷到上述公共消费队列中，默认是3
        properties.put("clickhouse.sink.retryTimes", "3"); //下沉到CK时如果失败的重试次数，默认是3
        properties.put("clickhouse.sink.ignoreSinkExceptionEnabled", "true"); //下沉到CK时是否忽略写异常，默认为true
        return new ClickHouseSinkManager(properties);
    }

    @Bean
    public Sink interfaceLogSink() {
        return clickHouseSinkManager.buildSink(InterfaceLog.class, 3, 1000, new DefaultSinkFailureHandler()); //批量插入失败处理器
    }
}
```



## 3. Flink中使用

**定义POJO：**

```java
import TableName;
import lombok.Data;

@Data
@TableName("aiops_local.interfacelog") //想要达到前文介绍的读写分离效果，这里必须使用本地表。格式：database.table
public class InterfaceLog {
    private String product; //业务
    private String service; //服务
    private String itf; //接口
    private String accountErrorCode;
    private String addn;
    private Long aggregateDelay;
    private Long avgAggregateDelay;
    private Long nonAggregatedDelay;
    private Long latency;
    private Double avgLatency;
    private String destinationId;
    private String errorDetail;
    private Long fail;
    private String ip;
    private String ipCity;
    private String ipCountry;
    private String ipProvince;
    private String itfGroup;
    private String returnCode;
    private String sourceId;
    private Long success;
    private Long total;
    private Long totalInterfaceLogAggregateDelay;
    private String type;
    private LocalDateTime sysTime;
    private LocalDateTime time;
}
```

**定义Sink:**

```java
import com.hihonor.aiops.clickhouse.component.ClickHouseSinkManager;
import com.hihonor.aiops.clickhouse.component.Sink;
import com.hihonor.aiops.clickhouse.pojo.InterfaceLog;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.util.Properties;


public class FlinkSinkDemo extends RichSinkFunction<InterfaceLog> {
    private volatile static transient ClickHouseSinkManager sinkManager;
    private transient Sink sink;

    public FlinkSinkDemo() {
    }

    @Override
    public void open(Configuration config) {
        // DCL单例模式
        if (sinkManager == null) {
            synchronized (FlinkSinkDemo.class) {
                if (sinkManager == null) {
                    Properties properties = new Properties();
                    properties.put("clickhouse.hikari.username", "root");
                    properties.put("clickhouse.hikari.password", "AigWNjWH");
                    properties.put("clickhouse.hikari.address", "10.68.178.71:8123"); //代理地址 or 集群中任一节点地址
                    properties.put("clickhouse.hikari.minimumIdle", "5");
                    properties.put("clickhouse.hikari.maximumPoolSize", "10");

                    properties.put("clickhouse.sink.queueMaxCapacity", "1000"); //公共消费队列容量（即最大能承载多少批次），默认是1000
                    properties.put("clickhouse.sink.writeTimeout", "3"); //写超时时间（单位秒），即当Buffer中数据超过该时间还未被flush则立马刷到上述公共消费队列中，默认是3
                    properties.put("clickhouse.sink.retryTimes", "3"); //下沉到CK时如果失败的重试次数，默认是3
                    properties.put("clickhouse.sink.ignoreSinkExceptionEnabled", "true"); //下沉到CK时是否忽略写异常，默认为true
                    sinkManager = new ClickHouseSinkManager(properties);
                }
            }
        }
        sink = sinkManager.buildSink(InterfaceLog.class,  3, 1000, new DefaultSinkFailureHandler()); //批量插入失败处理器
    }

    @Override
    public void invoke(InterfaceLog log, Context context) {
        try {
            sink.put(log);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (sink != null) {
            sink.close();
        }
        if (sinkManager != null && !sinkManager.isClosed()) {
            synchronized (FlinkSinkDemo.class) {
                if (sinkManager != null && !sinkManager.isClosed()) {
                    sinkManager.close();
                    sinkManager = null;
                }
            }
        }
        super.close();
    }
}
```



# 更新日志

### 1.0.7
- 增加失败处理器配置
- 修复当待插入的批次中存在个别null对象时导致的批量插入异常

## 1.0.6
- 优化代码
- 当引入SDK后会覆盖项目原本使用的日志框架（比如logback）的配置文件的问题，该问题已修复

## 1.0.5
- 优化代码
- 初始化数据源时通过代理查询集群IP列表来构造数据源，并增加定时更新数据源机制

## 1.0.4
- 优化代码
- 落库时准备参数阶段，针对LocalDateTime类型做了兼容

## 1.0.3
- 优化代码
- 初始化数据源时不再通过代理查询IP列表，直接通过配置传入IP列表

## 1.0.2
- 优化代码
- 采用Hikari连接池管理数据库连接

## 1.0.1
- 更加灵活的配置
- 支持Flink

## 1.0.0
- 支持Springboot
- 完成批量写



# 公众号

关注不迷路，微信扫描下方二维码关注公众号「**南山有一郎**」，时刻收听**项目更新**通知！

在公众号后台回复“**加群**”，即可加入「**南山有一郎**」交流群！

![mp_qrcode](https://s1.ax1x.com/2022/06/26/jkx8Ds.jpg)




# 许可证

```
Copyright [2022] [xlvchao]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
