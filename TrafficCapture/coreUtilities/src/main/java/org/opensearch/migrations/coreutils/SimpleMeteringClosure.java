package org.opensearch.migrations.coreutils;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.opensearch.migrations.tracing.IWithAttributes;
import org.opensearch.migrations.tracing.IWithStartTime;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class SimpleMeteringClosure {
    public final Meter meter;
    public final Tracer tracer;

    public SimpleMeteringClosure(String scopeName) {
        meter = GlobalOpenTelemetry.getMeter(scopeName);
        tracer = GlobalOpenTelemetry.getTracer(scopeName);
    }

    public static void initializeOpenTelemetry(String serviceName, String collectorEndpoint) {
        var serviceResource = Resource.getDefault().toBuilder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .build();

        OpenTelemetrySdk openTelemetrySdk =
                OpenTelemetrySdk.builder()
                        .setLoggerProvider(
                                SdkLoggerProvider.builder()
                                        .setResource(serviceResource)
                                        .addLogRecordProcessor(
                                                BatchLogRecordProcessor.builder(
                                                                OtlpGrpcLogRecordExporter.builder()
                                                                        .setEndpoint(collectorEndpoint)
                                                                        .build())
                                                        .build())
                                        .build())
                        .setTracerProvider(
                                SdkTracerProvider.builder()
                                        .setResource(serviceResource)
                                        .addSpanProcessor(
                                                BatchSpanProcessor.builder(
                                                                OtlpGrpcSpanExporter.builder()
                                                                        .setEndpoint(collectorEndpoint)
                                                                        .setTimeout(2, TimeUnit.SECONDS)
                                                                        .build())
                                                        .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                                                        .build())
                                        .build())
                        .setMeterProvider(
                                SdkMeterProvider.builder()
                                        .setResource(serviceResource)
                                        .registerMetricReader(
                                                PeriodicMetricReader.builder(
                                                                OtlpGrpcMetricExporter.builder()
                                                                        .setEndpoint(collectorEndpoint)
                                                                        .build())
                                                        .setInterval(Duration.ofMillis(1000))
                                                        .build())
                                        .build())
                        .buildAndRegisterGlobal();

        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(openTelemetrySdk::close));
        //OpenTelemetryAppender.install(GlobalOpenTelemetry.get());
    }

    public void meterIncrementEvent(IWithAttributes ctx, String eventName) {
        meterIncrementEvent(ctx, eventName, 1);
    }

    public void meterIncrementEvent(IWithAttributes ctx, String eventName, long increment) {
        if (ctx == null) {
            return;
        }
        meter.counterBuilder(eventName)
                .build().add(increment, ctx.getPopulatedAttributesBuilder()
                        .put("labelName", eventName)
                        .build());
    }

    public void meterDeltaEvent(IWithAttributes ctx, String eventName, long delta) {
        if (ctx == null) {
            return;
        }
        meter.upDownCounterBuilder(eventName)
                .build().add(delta, ctx.getPopulatedAttributesBuilder()
                        .put("labelName", eventName)
                        .build());
    }

    public <T extends IWithAttributes & IWithStartTime> void meterHistogramMillis(T ctx, String eventName) {
        meterHistogram(ctx, eventName, "ms", Duration.between(ctx.getStartTime(), Instant.now()).toMillis());
    }

    public <T extends IWithAttributes & IWithStartTime> void meterHistogramMicros(T ctx, String eventName) {
        meterHistogram(ctx, eventName, "us", Duration.between(ctx.getStartTime(), Instant.now()).toNanos()*1000);
    }

    public void meterHistogramMillis(IWithAttributes ctx, String eventName, Duration between) {
        meterHistogram(ctx, eventName, "ms", between.toMillis());
    }

    public void meterHistogramMicros(IWithAttributes ctx, String eventName, Duration between) {
        meterHistogram(ctx, eventName, "us", between.toNanos()*1000);
    }

    public void meterHistogram(IWithAttributes ctx, String eventName, String units, long value) {
        if (ctx == null) {
            return;
        }
        meter.histogramBuilder(eventName)
                .ofLongs()
                .setUnit(units)
                .build().record(value, ctx.getPopulatedAttributesBuilder()
                        .put("labelName", eventName)
                        .build());
    }

    public Span makeSpan(IWithAttributes ctx, String spanName) {
        var span = tracer.spanBuilder(spanName).startSpan();
        span.setAllAttributes(ctx.getPopulatedAttributesBuilder().build());
        return span;
    }
}
