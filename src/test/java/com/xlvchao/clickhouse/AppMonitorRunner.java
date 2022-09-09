//package com.hihonor.aiops.clickhouse;
//
//import com.hihonor.aiops.clickhouse.component.ClickHouseSinkManager;
//import com.hihonor.aiops.clickhouse.component.Sink;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//
///**
// * 应用停止前，需要清空缓存！防止数据丢失！
// *
// * Created by lvchao on 2022/9/9 17:48
// */
//@Component
//public class AppMonitorRunner implements ApplicationRunner {
//
//    @Resource
//    private ClickHouseSinkManager clickHouseSinkManager;
//
//    @Resource
//    private Sink interfaceLogSink;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        Runtime.getRuntime().addShutdownHook(new Thread("ClickHouse-app-shutdown-hook") {
//            @Override
//            public void run() {
//                try {
//                    if (interfaceLogSink != null) {
//                        interfaceLogSink.close();
//                    }
//
//                    if (clickHouseSinkManager != null && !clickHouseSinkManager.isClosed()) {
//                        synchronized (AppMonitorRunner.class) {
//                            if (clickHouseSinkManager != null && !clickHouseSinkManager.isClosed()) {
//                                clickHouseSinkManager.close();
//                                clickHouseSinkManager = null;
//                            }
//                        }
//                    }
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }
//}
