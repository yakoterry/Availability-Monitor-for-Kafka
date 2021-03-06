package com.microsoft.kafkaavailability.module;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.microsoft.kafkaavailability.discovery.CommonUtils;
import com.microsoft.kafkaavailability.discovery.Constants;
import com.microsoft.kafkaavailability.discovery.CuratorClient;
import com.microsoft.kafkaavailability.discovery.CuratorManager;
import com.microsoft.kafkaavailability.properties.AppProperties;
import com.microsoft.kafkaavailability.properties.MetaDataManagerProperties;
import com.microsoft.kafkaavailability.threads.MonitorTaskFactory;
import com.microsoft.kafkaavailability.threads.ServiceSpecProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MonitorTasksModule extends AbstractModule {
    private final static Logger LOGGER = LoggerFactory.getLogger(MonitorTasksModule.class);

    public static final String LOCAL_IP_CONSTANT_NAME = "localIPAddress";
    public static final String CURATOR_PORT_CONSTANT_NAME = "curatorPort";
    public static final String HEART_BEAT_EXECUTOR_SERVICE = "heartBeatExecutorService";

    @Override
    protected void configure() {
        bindConstant().annotatedWith(Names.named(LOCAL_IP_CONSTANT_NAME)).to(CommonUtils.getIpAddress());
        bindConstant().annotatedWith(Names.named(CURATOR_PORT_CONSTANT_NAME)).to(generateCuratorPort());

        install(new FactoryModuleBuilder().build(MonitorTaskFactory.class));
    }

    @Provides
    @Singleton
    public CuratorManager curatorManager(@Named(MonitorTasksModule.LOCAL_IP_CONSTANT_NAME) String localIPAddress,
                                         ServiceSpecProvider serviceSpecProvider, final CuratorFramework curatorFramework,
                                         AppProperties appProperties) {

        LOGGER.info("Creating client, KAT in the Environment:" + appProperties.environmentName);

        final CuratorManager curatorManager = new CuratorManager(curatorFramework, Constants.DEFAULT_REGISTRATION_ROOT,
                localIPAddress, serviceSpecProvider.getServiceSpec());

        try {
            curatorManager.registerLocalService();

            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    LOGGER.info("Normal shutdown executing.");
                    curatorManager.unregisterService();
                    if (curatorFramework != null && (curatorFramework.getState().equals(CuratorFrameworkState.STARTED) || curatorFramework.getState().equals(CuratorFrameworkState.LATENT))) {
                        curatorFramework.close();
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return curatorManager;
    }

    @Provides
    @Singleton
    public CuratorFramework curatorFramework(MetaDataManagerProperties metaDataManagerProperties) {
        return CuratorClient.getCuratorFramework(metaDataManagerProperties.zooKeeperHosts);
    }

    @Provides
    @Named(HEART_BEAT_EXECUTOR_SERVICE)
    public ScheduledExecutorService hearBeatExecutorService() {
        return Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("HeartBeat-Thread")
                        .build());
    }

    private int generateCuratorPort() {
        return 20000 + new Random().nextInt(10000);
    }
}
