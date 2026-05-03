package io.github.raphonzius.lvc.virtualareas.infrastructure.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.InfluxDbProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfluxDbProperties.class)
public class InfluxDbConfiguration {

    @Bean
    public InfluxDBClient influxDBClient(InfluxDbProperties props) {
        return InfluxDBClientFactory.create(props.url(), props.token().toCharArray());
    }

    @Bean
    public WriteApiBlocking influxWriteApi(InfluxDBClient client) {
        return client.getWriteApiBlocking();
    }

    @Bean
    public QueryApi influxQueryApi(InfluxDBClient client) {
        return client.getQueryApi();
    }
}
