package com.xlvchao.clickhouse.component;

import com.google.common.base.Preconditions;
import com.xlvchao.clickhouse.datasource.ServerNode;
import com.xlvchao.clickhouse.model.ClickHouseSettings;
import com.xlvchao.clickhouse.util.ThreadUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ClickHouseSinkManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSinkManager.class);
    private final ClickHouseSettings clickHouseSettings;
    private final Properties properties;
    private final Properties staticProperties;
    private final ScheduledCheckerAndCleaner scheduledCheckerAndCleaner;
    private final List<CompletableFuture<Boolean>> futures = Collections.synchronizedList(new LinkedList<>());
    private final List<ServerNode> serverNodes = Collections.synchronizedList(new ArrayList<>());
    private final List<DataSource> dataSources = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isClosed = false;
    private final String queryClusterInfoSql = "select d.host_address from (select x.host_address, row_number() over (partition by x.shard_num order by x.errors_count) as row_num from system.clusters x) d where d.row_num = 1";
    private static final String PREFIX = "jdbc:clickhouse://";
    private static final String SUFFIX = "?socket_timeout=3600000&max_execution_time=3600";



    public ClickHouseSinkManager(Properties properties) {
        this.properties = properties;
        this.staticProperties = createStaticProperties(properties);

        this.clickHouseSettings = new ClickHouseSettings(properties);
        this.scheduledCheckerAndCleaner = new ScheduledCheckerAndCleaner(clickHouseSettings, futures);

        initHikariDataSource();

        logger.info("Build sink manager success, properties = {}", properties);
    }


    private Properties createStaticProperties(Properties properties) {
        Preconditions.checkNotNull(properties);
        String address = properties.getProperty("clickhouse.hikari.address");
        String username = properties.getProperty("clickhouse.hikari.username");
        String password = properties.getProperty("clickhouse.hikari.password");
        Preconditions.checkNotNull(address);
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);

        Properties properties1  = (Properties) properties.clone();
        properties1.put("clickhouse.hikari.poolName", "aiops-hikari-ds-updater");
        properties1.put("clickhouse.hikari.jdbcUrl", PREFIX.concat(address).concat(SUFFIX));
        return properties1;
    }


    public void initHikariDataSource() {
        try {
            // Do once first
            updateDatasources();

            ThreadFactory factory = ThreadUtil.threadFactory("clickhouse-datasource-scheduled-updater");
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(factory);
            scheduledExecutorService.scheduleWithFixedDelay(() -> updateDatasources(), 10, 10, TimeUnit.MINUTES);

        } catch (Exception e) {
            logger.error("Init Hikari DataSource For ClickHouse Error!", e);
        }
    }

    private void updateDatasources() {
        try (HikariDataSource ds = genHikariDataSource(staticProperties);
             Connection conn = ds.getConnection();
             PreparedStatement prepareStatement = conn.prepareStatement(queryClusterInfoSql);
             ResultSet rs = prepareStatement.executeQuery()) {

            List<DataSource> newDataSources = new ArrayList<>();
            List<ServerNode> newServerNodes = convertResultSetToList(rs);

            //clear & update
            if(!(newServerNodes.size() == serverNodes.size() && newServerNodes.containsAll(serverNodes))) {
                newServerNodes.forEach( item -> {
                    properties.put("clickhouse.hikari.poolName", "aiops-hikari-ds-" + PREFIX.concat(item.getIp()));
                    properties.put("clickhouse.hikari.jdbcUrl", PREFIX.concat(item.getIp() + ":" + item.getPort()).concat(SUFFIX));
                    newDataSources.add(genHikariDataSource(properties));
                });

                serverNodes.clear();
                serverNodes.addAll(newServerNodes);
                dataSources.clear();
                dataSources.addAll(newDataSources);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ServerNode> convertResultSetToList(ResultSet rs) throws SQLException {
        List<ServerNode> list = new ArrayList();
        while (rs.next()) {
            ServerNode sn = ServerNode.Builder
                    .newServerNode()
                    .withIp(rs.getString("host_address"))
                    .withPort(8123)
                    .build();
            list.add(sn);
        }
        return list;
    }


    public HikariDataSource genHikariDataSource(Properties properties) {
        // 连接池配置
        HikariConfig config = new HikariConfig();
        //连接池名
        config.setPoolName(properties.getProperty("clickhouse.hikari.poolName", "aiops-hikari-ds"));
        // 最小空闲连接数
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("clickhouse.hikari.minimumIdle", "1")));
        // 最大连接数
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("clickhouse.hikari.maximumPoolSize", "3")));
        // 此属性控制从池中拿到的连接的默认自动提交行为，默认值：true
        config.setAutoCommit(false);
        // 空闲连接存活最大时间
        config.setIdleTimeout(MINUTES.toMillis(10));
        // 池中连接的最长存活时间，值0表示无限存活时间
        config.setMaxLifetime(MINUTES.toMillis(30));
        // 数据库连接超时时间，默认30秒
        config.setConnectionTimeout(SECONDS.toMillis(30));
        //测试链接是否可用的sql语句
        config.setConnectionTestQuery("SELECT 1");
        //账号设置
        config.setJdbcUrl(properties.getProperty("clickhouse.hikari.jdbcUrl"));
        config.setUsername(properties.getProperty("clickhouse.hikari.username"));
        config.setPassword(properties.getProperty("clickhouse.hikari.password"));
        return new HikariDataSource(config);
    }

    public Sink buildSink(Class clazz, int threadNum, int batchSize) {
        Preconditions.checkNotNull(scheduledCheckerAndCleaner);

        ClickHouseWriter clickHouseWriter = new ClickHouseWriter(threadNum, properties, futures, dataSources, new DefaultSinkFailureHandler());

        ClickHouseSinkBuffer clickHouseSinkBuffer = ClickHouseSinkBuffer.Builder
                .newClickHouseSinkBuffer()
                .withClass(clazz)
                .withBatchSize(batchSize)
                .withWriter(clickHouseWriter)
                .withWriteTimeout(clickHouseSettings.getWriteTimeout())
                .withFutures(futures)
                .build();

        scheduledCheckerAndCleaner.addSinkBuffer(clickHouseSinkBuffer);

        return new ClickHouseSink(clickHouseSinkBuffer);
    }

    public Sink buildSink(Class clazz, int threadNum, int batchSize, SinkFailureHandler handler) {
        Preconditions.checkNotNull(scheduledCheckerAndCleaner);

        ClickHouseWriter clickHouseWriter = new ClickHouseWriter(threadNum, properties, futures, dataSources, handler);

        ClickHouseSinkBuffer clickHouseSinkBuffer = ClickHouseSinkBuffer.Builder
                .newClickHouseSinkBuffer()
                .withClass(clazz)
                .withBatchSize(batchSize)
                .withWriter(clickHouseWriter)
                .withWriteTimeout(clickHouseSettings.getWriteTimeout())
                .withFutures(futures)
                .build();

        scheduledCheckerAndCleaner.addSinkBuffer(clickHouseSinkBuffer);

        return new ClickHouseSink(clickHouseSinkBuffer);
    }

    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void close() throws Exception {
        logger.info("Sink manager is shutting down...");
        scheduledCheckerAndCleaner.close();
        isClosed = true;
        logger.info("Sink manager shutdown complete!");
    }

}

