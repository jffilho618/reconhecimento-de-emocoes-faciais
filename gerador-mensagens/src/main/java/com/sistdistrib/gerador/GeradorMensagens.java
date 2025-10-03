package com.sistdistrib.gerador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class GeradorMensagens {
    private static final String EXCHANGE_NAME = "image_exchange";
    private static final String FACE_ROUTING_KEY = "face";
    private static final String TEAM_ROUTING_KEY = "team";
    private static final int MESSAGES_PER_SECOND = 5;

    private Connection connection;
    private Channel channel;
    private ObjectMapper objectMapper;
    private Random random;

    public GeradorMensagens() {
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }

    public void start() {
        try {
            setupRabbitMQ();
            System.out.println("=== GERADOR DE MENSAGENS INICIADO ===");
            System.out.println("Enviando " + MESSAGES_PER_SECOND + " mensagens por segundo...");

            generateMessages();
        } catch (Exception e) {
            System.err.println("Erro no gerador: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupRabbitMQ() throws IOException, TimeoutException {
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
        String username = System.getenv().getOrDefault("RABBITMQ_USER", "admin");
        String password = System.getenv().getOrDefault("RABBITMQ_PASS", "admin123");

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

        // Declarar exchange do tipo topic
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);

        // Declarar filas
        channel.queueDeclare("face_queue", true, false, false, null);
        channel.queueDeclare("team_queue", true, false, false, null);

        // Bind filas ao exchange
        channel.queueBind("face_queue", EXCHANGE_NAME, FACE_ROUTING_KEY);
        channel.queueBind("team_queue", EXCHANGE_NAME, TEAM_ROUTING_KEY);

        System.out.println("Conectado ao RabbitMQ em " + host + ":" + port);
    }

    private void generateMessages() {
        // Usar imagens das pastas de test
        File facesTestDir = new File("/app/images/faces/test/images");
        File teamsTestDir = new File("/app/images/teams/test/images");

        // Verificar se os diretórios existem
        if (!facesTestDir.exists() || !teamsTestDir.exists()) {
            System.err.println("❌ Diretórios de test não encontrados!");
            System.err.println("   Faces: " + facesTestDir.getAbsolutePath());
            System.err.println("   Teams: " + teamsTestDir.getAbsolutePath());
            return;
        }

        // Listar imagens disponíveis
        File[] faceImages = facesTestDir.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".png"));
        File[] teamImages = teamsTestDir.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".png"));

        if (faceImages == null || faceImages.length == 0) {
            System.err.println("❌ Nenhuma imagem de face encontrada em: " + facesTestDir.getAbsolutePath());
            return;
        }
        if (teamImages == null || teamImages.length == 0) {
            System.err.println("❌ Nenhuma imagem de time encontrada em: " + teamsTestDir.getAbsolutePath());
            return;
        }

        System.out.println("✅ " + faceImages.length + " imagens de faces encontradas");
        System.out.println("✅ " + teamImages.length + " imagens de times encontradas");

        while (true) {
            try {
                for (int i = 0; i < MESSAGES_PER_SECOND; i++) {
                    // Escolher aleatoriamente entre face ou team
                    boolean isFace = random.nextBoolean();

                    if (isFace) {
                        sendFaceMessage(faceImages);
                    } else {
                        sendTeamMessage(teamImages);
                    }

                    // Pequeno delay entre mensagens
                    Thread.sleep(200 / MESSAGES_PER_SECOND);
                }

                // Esperar para completar 1 segundo
                Thread.sleep(1000 - (200 * MESSAGES_PER_SECOND / MESSAGES_PER_SECOND));

            } catch (InterruptedException e) {
                System.err.println("Thread interrompida: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private void sendFaceMessage(File[] faceImages) throws Exception {
        // Selecionar imagem aleatória das pastas de test
        File selectedImage = faceImages[random.nextInt(faceImages.length)];
        byte[] imageData = Files.readAllBytes(selectedImage.toPath());
        String filename = selectedImage.getName();

        ImageMessage message = new ImageMessage("face", filename, imageData);

        sendMessage(message, FACE_ROUTING_KEY);
        System.out.println("✓ Mensagem FACE enviada - " + filename);
    }

    private void sendTeamMessage(File[] teamImages) throws Exception {
        // Selecionar imagem aleatória das pastas de test
        File selectedImage = teamImages[random.nextInt(teamImages.length)];
        byte[] imageData = Files.readAllBytes(selectedImage.toPath());
        String filename = selectedImage.getName();

        ImageMessage message = new ImageMessage("team", filename, imageData);

        sendMessage(message, TEAM_ROUTING_KEY);
        System.out.println("✓ Mensagem TEAM enviada - " + filename);
    }

    private void sendMessage(ImageMessage message, String routingKey) throws Exception {
        byte[] messageBody = objectMapper.writeValueAsBytes(message);

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .deliveryMode(2) // Mensagem persistente
                .timestamp(new java.util.Date())
                .build();

        channel.basicPublish(EXCHANGE_NAME, routingKey, properties, messageBody);
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            System.err.println("Erro ao fechar conexões: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        GeradorMensagens gerador = new GeradorMensagens();

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(gerador::close));

        gerador.start();
    }
}