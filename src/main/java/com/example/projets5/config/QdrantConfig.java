package com.example.projets5.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {

    @Bean
    public QdrantClient qdrantClient(
            @Value("${qdrant.grpc.host:localhost}") String host,
            @Value("${qdrant.grpc.port:6334}") int port,
            @Value("${qdrant.api-key:}") String apiKey
    ) {
        QdrantGrpcClient.Builder b = QdrantGrpcClient.newBuilder(host, port, false);

        if (apiKey != null && !apiKey.isBlank()) {
            b = b.withApiKey(apiKey.trim());
        }

        return new QdrantClient(b.build());
    }
}
