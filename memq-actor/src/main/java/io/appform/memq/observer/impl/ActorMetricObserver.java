package io.appform.memq.observer.impl;


import com.codahale.metrics.*;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.stats.MetricData;
import io.appform.memq.stats.MetricKeyData;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ActorMetricObserver extends ActorObserver {

    private static final String ACTOR_PREFIX = "actor";
    private static final String DELIMITER = ".";
    private static final String DELIMITER_REPLACEMENT = "_";
    private final MetricRegistry metricRegistry;
    private final String actorName;
    @Getter
    private final Map<MetricKeyData, MetricData> metricCache = new ConcurrentHashMap<>();

    public ActorMetricObserver(
            final String actorName,
            final MetricRegistry metricRegistry) {
        super(null);
        this.metricRegistry = metricRegistry;
        this.actorName = actorName;

    }

    private static String normalizeString(final String name) {
        return name.replace(DELIMITER, DELIMITER_REPLACEMENT);
    }

    @Override
    public void initialize(Actor<?> actor) {
        this.metricRegistry.gauge(MetricRegistry.name(getMetricPrefix(actorName), "size"),
                                  (MetricRegistry.MetricSupplier<Gauge<Long>>) () ->
                                          new CachedGauge<>(5, TimeUnit.SECONDS) {
                                              @Override
                                              protected Long loadValue() {
                                                  return actor.size();
                                              }
                                          });
    }

    @Override
    public boolean execute(final ActorObserverContext<? extends Message> context, final Runnable runnable) {
        return metered(context, runnable);
    }

    private boolean metered(ActorObserverContext<? extends Message> context, Runnable runnable) {
        val metricData = getMetricData(context);
        metricData.getTotal().mark();
        val timer = metricData.getTimer().time();
        var ret = false;
        try {
            ret = proceed(context, runnable);
            if (ret) {
                metricData.getSuccess().mark();
            } else {
                metricData.getRejected().mark();
            }
        }
        catch (Throwable t) {
            metricData.getFailed().mark();
            throw t;
        }
        finally {
            timer.stop();
        }
        return ret;
    }

    private MetricData getMetricData(final ActorObserverContext<? extends Message> context) {
        val metricKeyData = MetricKeyData.builder()
                .actorName(actorName)
                .operation(context.getOperation().name())
                .build();
        return metricCache.computeIfAbsent(metricKeyData, key ->
                getMetricData(getMetricPrefix(metricKeyData)));
    }

    private MetricData getMetricData(final String metricPrefix) {
        return MetricData.builder()
                .timer(metricRegistry.timer(MetricRegistry.name(metricPrefix, "latency"),
                                            () -> new Timer(new SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS))))
                .success(metricRegistry.meter(MetricRegistry.name(metricPrefix, "success")))
                .failed(metricRegistry.meter(MetricRegistry.name(metricPrefix, "failed")))
                .rejected(metricRegistry.meter(MetricRegistry.name(metricPrefix, "rejected")))
                .total(metricRegistry.meter(MetricRegistry.name(metricPrefix, "total")))
                .build();
    }

    private String getMetricPrefix(final MetricKeyData metricKeyData) {
        return getMetricPrefix(actorName, metricKeyData.getOperation());
    }

    private String getMetricPrefix(String... metricNames) {
        val metricPrefix = new StringBuilder(ACTOR_PREFIX);
        for (val metricName : metricNames) {
            metricPrefix.append(DELIMITER).append(normalizeString(metricName));
        }
        return metricPrefix.toString();
    }
}