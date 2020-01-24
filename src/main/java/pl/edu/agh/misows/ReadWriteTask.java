package pl.edu.agh.misows;

import com.google.api.Metric;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceClient.ListTimeSeriesPagedResponse;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.monitoring.v3.*;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Comparator.comparing;

public class ReadWriteTask implements Runnable {

    private enum MetricType {
        INSTANCE, TIME, MEMORY, CPU
    }

    private final ProjectName projectName;
    private final Table<String, String, MetricType> metrics;

    private final MetricServiceClient metricClient;
    private final InfluxDB influxDB;

    public ReadWriteTask() throws IOException {
        projectName = ProjectName.of("thinking-window-265711");

        metrics = ImmutableTable.<String, String, MetricType>builder()
                .put("compute.googleapis.com/instance/disk/read_ops_count", "iops.read", MetricType.INSTANCE)
                .put("compute.googleapis.com/instance/disk/write_ops_count", "iops.write", MetricType.INSTANCE)
//                .put("dataflow.googleapis.com/job/elapsed_time", "job.seconds", MetricType.TIME)
                .put("kubernetes.io/container/memory/used_bytes", "memory.bytes", MetricType.MEMORY)
                .put("compute.googleapis.com/instance/cpu/utilization", "cpu.percent", MetricType.INSTANCE)
                .build();

        GoogleCredentials googleCredentials = ComputeEngineCredentials.create();

        MetricServiceSettings metricServiceSettings = MetricServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();

        metricClient = MetricServiceClient.create(metricServiceSettings);

        influxDB = InfluxDBFactory.connect("http://35.205.72.177:8086", "influxdb-admin", "hr2qMAhrJJoWR5bj9CRJBAdRro6GFDFU");
    }

    @Override
    public void run() {
        long endMillis = System.currentTimeMillis();
        System.out.println(format("Started read for timestamp: %s", new Date(endMillis).toString()));

        TimeInterval timeInterval = TimeInterval.newBuilder()
                .setStartTime(Timestamps.fromMillis(endMillis - (70L * 60L * 1_000L)))
                .setEndTime(Timestamps.fromMillis(endMillis))
                .build();

        BatchPoints batchPoints = BatchPoints
                .database("misows")
                .retentionPolicy("defaultPolicy")
                .build();

        for (Cell<String, String, MetricType> metricCell : metrics.cellSet()) {
            String googleMetricName = metricCell.getRowKey();
            String influxMetricName = metricCell.getColumnKey();
            MetricType metricType = metricCell.getValue();

            ListTimeSeriesRequest request = ListTimeSeriesRequest.newBuilder()
                    .setName(projectName.toString())
                    .setFilter(format("metric.type=\"%s\"", googleMetricName))
                    .setInterval(timeInterval)
                    .setView(ListTimeSeriesRequest.TimeSeriesView.FULL)
                    .build();

            ListTimeSeriesPagedResponse response = metricClient.listTimeSeries(request);

            for (TimeSeries series : response.iterateAll()) {
                if (metricType == MetricType.INSTANCE) {
                    addInstancePoints(batchPoints, series, influxMetricName);
                } else if (metricType == MetricType.MEMORY) {
                    addMemoryPoints(batchPoints, series, influxMetricName);
                } else if (metricType == MetricType.CPU) {
                    addCpuPoints(batchPoints, series, influxMetricName);
                }
            }
        }

        influxDB.write(batchPoints);
        System.out.println(format("Ended write for timestamp: %s", new Date(endMillis).toString()));

    }

    private void addCpuPoints(BatchPoints batchPoints,
                              TimeSeries series,
                              String influxMetricName) {
        Map<String, String> labels = series.getResource().getLabelsMap();
        String podName = labels.get("pod_name");
        String containerName = labels.get("container_name");

        Optional<Point> point = series.getPointsList()
                .stream()
                .max(comparing(p -> p.getInterval().getEndTime().getSeconds()));

        if (point.isPresent()) {
            double value = point.get().getValue().getDoubleValue();
            Timestamp timestamp = point.get().getInterval().getEndTime();

            org.influxdb.dto.Point influxPoint = org.influxdb.dto.Point
                    .measurement(influxMetricName)
                    .time(timestamp.getSeconds(), TimeUnit.SECONDS)
                    .addField("value", value)
                    .tag("container", containerName)
                    .tag("pod", podName)
                    .build();

            batchPoints.point(influxPoint);
            System.out.println(format("Added point for: %s", influxMetricName));
        }
    }

    private void addMemoryPoints(BatchPoints batchPoints,
                                 TimeSeries series,
                                 String influxMetricName) {
        String memoryType = series.getMetric().getLabelsOrDefault("memory_type", "");
        Map<String, String> labels = series.getResource().getLabelsMap();
        String podName = labels.get("pod_name");
        String containerName = labels.get("container_name");

        Optional<Point> point = series.getPointsList()
                .stream()
                .max(comparing(p -> p.getInterval().getEndTime().getSeconds()));

        if (point.isPresent()) {
            long value = point.get().getValue().getInt64Value();
            Timestamp timestamp = point.get().getInterval().getEndTime();

            org.influxdb.dto.Point influxPoint = org.influxdb.dto.Point
                    .measurement(influxMetricName)
                    .time(timestamp.getSeconds(), TimeUnit.SECONDS)
                    .addField("value", value)
                    .tag("container", containerName)
                    .tag("pod", podName)
                    .tag("memory_type", memoryType)
                    .build();

            batchPoints.point(influxPoint);
            System.out.println(format("Added point for: %s", influxMetricName));
        }
    }

    private void addInstancePoints(BatchPoints batchPoints,
                                   TimeSeries series,
                                   String influxMetricName) {
        Metric metric = series.getMetric();
        Map<String, String> labels = metric.getLabelsMap();
        String instanceName = labels.get("instance_name");
        String deviceName = labels.get("device_name");

        if (!Objects.equals(instanceName, deviceName)) {
            System.out.println(format("Skipping device: %s", deviceName));
            return;
        }

        Optional<Point> point = series.getPointsList()
                .stream()
                .max(comparing(p -> p.getInterval().getEndTime().getSeconds()));

        if (point.isPresent()) {
            Number value;
            if (influxMetricName.contains("ops_count")) {
                value = point.get().getValue().getInt64Value();
            } else {
                value = point.get().getValue().getDoubleValue();
            }
            Timestamp timestamp = point.get().getInterval().getEndTime();

            org.influxdb.dto.Point influxPoint = org.influxdb.dto.Point
                    .measurement(influxMetricName)
                    .time(timestamp.getSeconds(), TimeUnit.SECONDS)
                    .addField("value", value)
                    .tag("instance", instanceName)
                    .build();

            batchPoints.point(influxPoint);
            System.out.println(format("Added point for: %s", influxMetricName));
        }
    }
}
