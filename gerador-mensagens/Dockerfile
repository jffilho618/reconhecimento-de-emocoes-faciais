# Dockerfile para Gerador de Mensagens
FROM openjdk:17-jdk-slim

# Instalar Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Definir diretório de trabalho
WORKDIR /app

# Copiar arquivos do projeto
COPY pom.xml .
COPY src ./src

# Compilar o projeto
RUN mvn clean package -DskipTests

# Criar diretório para imagens
RUN mkdir -p /app/images/faces /app/images/teams

# Comando para executar a aplicação
CMD ["java", "-jar", "target/gerador-mensagens-1.0.0.jar"]