package com.sistdistrib.consumidor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import com.sistdistrib.consumidor.ml.EmotionApiClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumidorFace {
    private static final String EXCHANGE_NAME = "image_exchange";
    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;
    private EmotionApiClient emotionApiClient;
    private AtomicLong processedCount;

    public ConsumidorFace() {
        this.objectMapper = new ObjectMapper();
        this.emotionApiClient = new EmotionApiClient();
        this.processedCount = new AtomicLong(0);
    }

    public void start() {
        try {
            setupRabbitMQ();

            System.out.println("🤖 === CONSUMIDOR DE FACES (YOLOv5 API) ===");

            // Verifica se API está funcionando
            if (emotionApiClient.isHealthy()) {
                System.out.println("✅ API de emoções conectada e funcionando!");
            } else {
                System.out.println("⚠️ API de emoções não está respondendo - usando modo simulado");
            }

            System.out.println("🚀 Iniciando processamento de mensagens...");
            System.out.println("Aguardando mensagens (pressione Ctrl+C para parar)");

            startConsuming();
        } catch (Exception e) {
            System.err.println("Erro no consumidor: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void setupRabbitMQ() throws IOException, TimeoutException {
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        String username = System.getenv().getOrDefault("RABBITMQ_USER", "admin");
        String password = System.getenv().getOrDefault("RABBITMQ_PASS", "admin123");
        String queueName = System.getenv().getOrDefault("QUEUE_NAME", "face_queue");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        // Configurar reconexão automática
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Declarar exchange e fila (caso não existam)
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, EXCHANGE_NAME, "face");

        // Configurar QoS para processar uma mensagem por vez
        channel.basicQos(1);

        System.out.println("Conectado ao RabbitMQ - Fila: " + queueName);
    }

    private void startConsuming() throws IOException {
        String queueName = System.getenv().getOrDefault("QUEUE_NAME", "face_queue");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                byte[] body = delivery.getBody();
                ImageMessage message = objectMapper.readValue(body, ImageMessage.class);

                System.out.println("📥 Recebida mensagem: " + message.getFilename());

                // Processar com API YOLOv5
                EmotionApiClient.EmotionResult result = emotionApiClient.predictEmotion(message.getData(), message.getFilename());

                // Simular tempo de processamento
                Thread.sleep(300);

                // Salvar imagem APÓS processamento bem-sucedido
                if (!"ERRO".equals(result.getCategory())) {
                    // Se tem imagem anotada, salva ela. Senão, salva a original
                    if (result.hasAnnotatedImage()) {
                        saveProcessedImage(result.getAnnotatedImage(), message.getFilename(), result.getEmotion());
                    } else {
                        saveProcessedImage(message.getData(), message.getFilename(), result.getEmotion());
                    }
                }

                long count = processedCount.incrementAndGet();
                System.out.println("🤖 [" + count + "] " + result.toString() + " [SALVA]");

                // Acknowledgment da mensagem
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem: " + e.getMessage());

                // Rejeitar mensagem em caso de erro
                try {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                } catch (IOException ioException) {
                    System.err.println("Erro ao rejeitar mensagem: " + ioException.getMessage());
                }
            }
        };

        CancelCallback cancelCallback = consumerTag -> {
            System.out.println("Consumidor cancelado: " + consumerTag);
        };

        // Iniciar consumo
        channel.basicConsume(queueName, false, deliverCallback, cancelCallback);

        // Manter o programa rodando
        try {
            while (true) {
                Thread.sleep(1000);

                // Log de status a cada 30 análises
                if (processedCount.get() % 30 == 0 && processedCount.get() > 0) {
                    System.out.println("📊 Status: " + processedCount.get() + " faces analisadas via YOLOv5 API");
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Consumidor interrompido.");
            Thread.currentThread().interrupt();
        }
    }



    private void saveProcessedImage(byte[] imageData, String filename, String emotion) {
        try {
            // Criar diretório de saída se não existir
            File outputDir = new File("/app/images/faces/processed");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Verificar se os dados são JPG válidos
            boolean isValidJpg = imageData.length > 4 &&
                                imageData[0] == (byte)0xFF &&
                                imageData[1] == (byte)0xD8;

            if (!isValidJpg) {
                System.err.println("⚠️ AVISO: Dados não parecem ser JPG válido para " + filename);
            }

            // Criar nome do arquivo baseado na predição da emoção
            String emotionFileName = getEmotionFileName(emotion);
            long timestamp = System.currentTimeMillis();
            String processedFileName = emotionFileName + "_" + timestamp + ".jpg";

            // Salvar a imagem processada com o nome da emoção
            File outputFile = new File(outputDir, processedFileName);
            Files.write(outputFile.toPath(), imageData);

            System.out.println("💾 Imagem salva como: " + processedFileName + " (" + imageData.length + " bytes)");
        } catch (IOException e) {
            System.err.println("Erro ao salvar imagem processada " + filename + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro debug: " + e.getMessage());
        }
    }

    private String getEmotionFileName(String emotion) {
        // Converter emoção em nome de arquivo limpo (conforme data.yaml)
        switch (emotion.toLowerCase()) {
            case "anger":
                return "Raiva";
            case "fear":
                return "Medo";
            case "happy":
                return "Feliz";
            case "neutral":
                return "Neutro";
            case "sad":
                return "Triste";
            default:
                return "Desconhecido";
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            System.out.println("Conexões fechadas. Total processado: " + processedCount.get());
        } catch (Exception e) {
            System.err.println("Erro ao fechar conexões: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        ConsumidorFace consumidor = new ConsumidorFace();

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(consumidor::close));

        consumidor.start();
    }
}