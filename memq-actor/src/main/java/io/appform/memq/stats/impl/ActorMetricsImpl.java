package io.appform.memq.stats.impl;

import com.codahale.metrics.*;
import io.appform.memq.stats.ActorMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;


public class ActorMetricsImpl implements ActorMetrics {

    private static final String METRIC_PREFIX = "memq.actor";
    private final Meter publishSuccess;
    private final Meter publishFailed;
    private final Meter processing;
    private final Meter sideline;
    private final Meter released;
    private final Timer processingTime;

    public ActorMetricsImpl(final MetricRegistry metricRegistry, final String name, final LongSupplier sizeFunction) {

        publishSuccess = metricRegistry.meter(String.join(".", METRIC_PREFIX,name, "publish", "success"
                                ));

        publishFailed = metricRegistry.meter(String.join(".", METRIC_PREFIX,name, "publish", "failed"
                                ));

        processing = metricRegistry.meter(String.join(".", METRIC_PREFIX,name, "processing"
                                ));

        sideline = metricRegistry.meter(String.join(".", METRIC_PREFIX,name, "sideline"
                                ));

        released = metricRegistry.meter(String.join(".", METRIC_PREFIX,name, "released"
                                ));

        processingTime = metricRegistry.timer(String.join(".", METRIC_PREFIX, name, "timed"),
                () -> new Timer(LockFreeExponentiallyDecayingReservoir.builder().build()));

        //Register size
        metricRegistry.gauge(String.join(".", METRIC_PREFIX, name, "size"),
                (MetricRegistry.MetricSupplier<Gauge<Long>>) () ->
                        new CachedGauge<>(10, TimeUnit.MILLISECONDS) {
                            @Override
                            protected Long loadValue() {
                                return sizeFunction.getAsLong();
                            }
                        });
    }

    @Override
    public void markPublishSuccess() {
        publishSuccess.mark();
    }

    @Override
    public void markPublishFailed() {
        publishFailed.mark();
    }

    @Override
    public void markProcessing() {
        processing.mark();
    }

    @Override
    public void markSideline() {
        sideline.mark();
    }

    @Override
    public void markReleased() {
        released.mark();
    }

    @Override
    public void updateProcessTime(Duration elapsed) {
        processingTime.update(elapsed);
    }
}
