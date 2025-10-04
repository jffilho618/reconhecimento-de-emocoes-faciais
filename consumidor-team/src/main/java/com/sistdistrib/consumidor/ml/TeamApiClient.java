package com.sistdistrib.consumidor.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Cliente para API de predição de times usando YOLOv5
 */
public class TeamApiClient {

    private static final Logger logger = LoggerFactory.getLogger(TeamApiClient.class);

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    public TeamApiClient() {
        this.apiUrl = System.getenv().getOrDefault("TEAM_API_URL", "http://ai-team-service:5001");
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        logger.info("TeamApiClient inicializado - URL: {}", apiUrl);
    }

    /**
     * Verifica se a API está funcionando
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                    .url(apiUrl + "/health")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonNode healthData = objectMapper.readTree(response.body().string());
                    boolean healthy = healthData.get("status").asText().equals("healthy");
                    logger.info("Health check: {}", healthy ? "✅ API OK" : "⚠️ API com problemas");
                    return healthy;
                }
            }
        } catch (Exception e) {
            logger.error("Erro no health check: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Prediz time da imagem
     *
     * @param imageData Dados binários da imagem
     * @param filename Nome do arquivo
     * @return Resultado da predição
     */
    public TeamResult predictTeam(byte[] imageData, String filename) {
        try {
            // Codifica imagem em base64
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            // Cria payload JSON
            String jsonPayload = objectMapper.writeValueAsString(new PredictionRequest(base64Image, filename));

            // Cria request
            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(apiUrl + "/predict")
                    .post(body)
                    .build();

            // Executa request
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode result = objectMapper.readTree(responseBody);

                    String team = result.get("team").asText();
                    double confidence = result.get("confidence").asDouble();
                    String method = result.get("method").asText();

                    // Extrair imagem anotada se disponível
                    byte[] annotatedImageBytes = null;
                    if (result.has("annotated_image")) {
                        String annotatedImageBase64 = result.get("annotated_image").asText();
                        annotatedImageBytes = Base64.getDecoder().decode(annotatedImageBase64);
                        logger.debug("Imagem anotada recebida para {}", filename);
                    }

                    logger.debug("Predição para {}: {} ({:.2f}) via {}", filename, team, confidence, method);

                    return new TeamResult(filename, team, "IA_REAL", confidence, annotatedImageBytes);
                } else {
                    logger.error("Erro na API: {} - {}", response.code(), response.message());
                    return new TeamResult(filename, "unknown", "ERRO", 0.0);
                }
            }

        } catch (Exception e) {
            logger.error("Erro ao chamar API de time para {}: {}", filename, e.getMessage());
            return new TeamResult(filename, "unknown", "ERRO", 0.0);
        }
    }

    /**
     * Classe para request de predição
     */
    private static class PredictionRequest {
        public final String image;
        public final String filename;

        public PredictionRequest(String image, String filename) {
            this.image = image;
            this.filename = filename;
        }
    }

    /**
     * Classe para resultado de time
     */
    public static class TeamResult {
        private final String filename;
        private final String teamName;
        private final String category;
        private final double confidence;
        private final long processedAt;
        private final byte[] annotatedImage;

        public TeamResult(String filename, String teamName, String category, double confidence) {
            this(filename, teamName, category, confidence, null);
        }

        public TeamResult(String filename, String teamName, String category, double confidence, byte[] annotatedImage) {
            this.filename = filename;
            this.teamName = teamName;
            this.category = category;
            this.confidence = confidence;
            this.annotatedImage = annotatedImage;
            this.processedAt = System.currentTimeMillis();
        }

        public String getFilename() { return filename; }
        public String getTeamName() { return teamName; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public long getProcessedAt() { return processedAt; }
        public byte[] getAnnotatedImage() { return annotatedImage; }
        public boolean hasAnnotatedImage() { return annotatedImage != null; }

        @Override
        public String toString() {
            String icon = "IA_REAL".equals(category) ? "🧠" : "⚽";
            return String.format("%s %s[%s]: %s - %.1f%% confiança",
                icon, category, filename, teamName, confidence * 100);
        }
    }
}