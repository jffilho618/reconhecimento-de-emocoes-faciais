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
 * Cliente para API de predição de emoções usando YOLOv5
 */
public class EmotionApiClient {

    private static final Logger logger = LoggerFactory.getLogger(EmotionApiClient.class);

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    public EmotionApiClient() {
        this.apiUrl = System.getenv().getOrDefault("EMOTION_API_URL", "http://ai-face-service:5000");
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        logger.info("EmotionApiClient inicializado - URL: {}", apiUrl);
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
     * Prediz emoção da imagem
     *
     * @param imageData Dados binários da imagem
     * @param filename Nome do arquivo
     * @return Resultado da predição
     */
    public EmotionResult predictEmotion(byte[] imageData, String filename) {
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

                    String emotion = result.get("emotion").asText();
                    double confidence = result.get("confidence").asDouble();
                    String method = result.get("method").asText();

                    // Extrair imagem anotada se disponível
                    byte[] annotatedImageBytes = null;
                    if (result.has("annotated_image")) {
                        String annotatedImageBase64 = result.get("annotated_image").asText();
                        annotatedImageBytes = Base64.getDecoder().decode(annotatedImageBase64);
                        logger.debug("Imagem anotada recebida para {}", filename);
                    }

                    logger.debug("Predição para {}: {} ({:.2f}) via {}", filename, emotion, confidence, method);

                    return new EmotionResult(filename, emotion, "IA_REAL", confidence, annotatedImageBytes);
                } else {
                    logger.error("Erro na API: {} - {}", response.code(), response.message());
                    return new EmotionResult(filename, "unknown", "ERRO", 0.0);
                }
            }

        } catch (Exception e) {
            logger.error("Erro ao chamar API de emoção para {}: {}", filename, e.getMessage());
            return new EmotionResult(filename, "unknown", "ERRO", 0.0);
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
     * Classe para resultado de emoção
     */
    public static class EmotionResult {
        private final String filename;
        private final String emotion;
        private final String category;
        private final double confidence;
        private final long processedAt;
        private final byte[] annotatedImage;

        public EmotionResult(String filename, String emotion, String category, double confidence) {
            this(filename, emotion, category, confidence, null);
        }

        public EmotionResult(String filename, String emotion, String category, double confidence, byte[] annotatedImage) {
            this.filename = filename;
            this.emotion = emotion;
            this.category = category;
            this.confidence = confidence;
            this.processedAt = System.currentTimeMillis();
            this.annotatedImage = annotatedImage;
        }

        public String getFilename() { return filename; }
        public String getEmotion() { return emotion; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public long getProcessedAt() { return processedAt; }
        public byte[] getAnnotatedImage() { return annotatedImage; }
        public boolean hasAnnotatedImage() { return annotatedImage != null; }

        @Override
        public String toString() {
            String icon = "IA_REAL".equals(category) ? "🧠" : "🤖";
            return String.format("%s %s[%s]: %s - %.1f%% confiança",
                icon, category, filename, emotion, confidence * 100);
        }
    }
}