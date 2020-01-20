package pl.edu.agh.misows;

import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResourceDescriptor;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceClient.ListMetricDescriptorsPagedResponse;
import com.google.cloud.monitoring.v3.MetricServiceClient.ListMonitoredResourceDescriptorsPagedResponse;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.ProjectName;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Main {
    public static void main(String[] args) throws IOException {
        GoogleCredentials googleCredentials = ComputeEngineCredentials.create();

        MetricServiceSettings metricServiceSettings = MetricServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();

        MetricServiceClient metricServiceClient = MetricServiceClient.create(metricServiceSettings);

        ListMetricDescriptorsPagedResponse list1 = metricServiceClient.listMetricDescriptors(ProjectName.of("misows"));
        ListMonitoredResourceDescriptorsPagedResponse list2 = metricServiceClient.listMonitoredResourceDescriptors(ProjectName.of("misows"));

        List<String> metricNames = StreamSupport
                .stream(list1.iterateAll().spliterator(), false)
                .map(MetricDescriptor::getName)
                .collect(Collectors.toList());

        List<String> monitoredNames = StreamSupport
                .stream(list2.iterateAll().spliterator(), false)
                .map(MonitoredResourceDescriptor::getName)
                .collect(Collectors.toList());

        System.out.println(metricNames);
        System.out.println(monitoredNames);
    }
}
