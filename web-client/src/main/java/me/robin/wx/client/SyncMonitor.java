package me.robin.wx.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Lubin.Xuan on 2017-06-29.
 */
public class SyncMonitor {

    private static final Logger logger = LoggerFactory.getLogger(SyncMonitor.class);

    private static final Map<String, Long> SYNC_TIME_MAP = new ConcurrentHashMap<>();

    static void updateSyncTime(BaseServer baseServer) {
        SYNC_TIME_MAP.put(baseServer.getInstanceId(), System.currentTimeMillis());
    }

    static void evict(BaseServer baseServer) {
        SYNC_TIME_MAP.remove(baseServer.getInstanceId());
    }

    static {
        Timer timer = new Timer("WechatInsSyncStatusCheck");
        long period = TimeUnit.MINUTES.toMillis(1);
        timer.schedule(new TimerTask() {
            long waring = TimeUnit.MINUTES.toMillis(2);

            @Override
            public void run() {
                Set<String> instanceIds = new HashSet<>(SYNC_TIME_MAP.keySet());
                long current = System.currentTimeMillis();
                for (String instanceId : instanceIds) {
                    long lastSyncTime = SYNC_TIME_MAP.getOrDefault(instanceId, -1L);
                    if (lastSyncTime == -1) {
                        continue;
                    }
                    if (current - lastSyncTime > waring) {
                        logger.warn("微信实例[{}] 已经{}分钟没有更新同步状态了", instanceId, TimeUnit.MILLISECONDS.toMinutes(current - lastSyncTime));
                    }
                }
            }
        }, period, period);
    }
}
