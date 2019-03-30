package andesite.node.util;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.prometheus.client.Collector;
import io.prometheus.client.Histogram;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import static com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION;
import static com.sun.management.GarbageCollectionNotificationInfo.from;

//based on https://github.com/Frederikam/Lavalink/blob/a6f5631b0ddd4b25a7e6720ae3f08c3a0a9729b5/LavalinkServer/src/main/java/lavalink/server/metrics/GcNotificationListener.java
public class GCListener implements NotificationListener {
    private static final Histogram GC_PAUSES = Histogram.build()
            .name("andesite_gc_pauses_seconds")
            .help("Garbage collection pauses by buckets")
            .labelNames("action", "cause", "name")
            .buckets(0.025, 0.050, 0.100, 0.200, 0.400, 0.800, 1.600)
            .register();
    
    @Override
    public void handleNotification(Notification notification, Object handback) {
        if(GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            GarbageCollectionNotificationInfo notificationInfo = from((CompositeData) notification.getUserData());
            GcInfo info = notificationInfo.getGcInfo();
            
            if(info != null && !"No GC".equals(notificationInfo.getGcCause())) {
                GC_PAUSES.labels(
                        notificationInfo.getGcAction(),
                        notificationInfo.getGcCause(),
                        notificationInfo.getGcName()
                ).observe(info.getDuration() / Collector.MILLISECONDS_PER_SECOND);
            }
        }
    }
}
