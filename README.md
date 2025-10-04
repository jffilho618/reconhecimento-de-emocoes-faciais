# 🧠 Sistema Distribuído de Análise de Imagens com YOLOv5

<div align="center">

[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![YOLOv5](https://img.shields.io/badge/YOLOv5-00FFFF?style=for-the-badge&logo=yolo&logoColor=black)](https://github.com/ultralytics/yolov5)

**Sistema distribuído moderno para detecção e classificação de emoções faciais e identificação de times de futebol usando YOLOv5 e microsserviços.**

[Características](#-características-principais) •
[Arquitetura](#-arquitetura) •
[Instalação](#-instalação-rápida) •
[Uso](#-como-usar) •
[Treinamento](#-treinamento-de-modelos)

</div>

---

## 📋 Índice

- [Características Principais](#-características-principais)
- [Arquitetura](#-arquitetura)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Instalação Rápida](#-instalação-rápida)
- [Como Usar](#-como-usar)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Treinamento de Modelos](#-treinamento-de-modelos)
- [APIs](#-apis)
- [Monitoramento](#-monitoramento)
- [Visualização de Resultados](#-visualização-de-resultados)
- [Solução de Problemas](#-solução-de-problemas)

---

## 🎯 Características Principais

### ✨ Detecção em Tempo Real

- **Análise de Emoções Faciais**: Detecta 5 emoções (raiva, medo, felicidade, neutro, tristeza)
- **Identificação de Times**: Reconhece 20 times da Premier League
- **Bounding Boxes Visuais**: Desenha retângulos coloridos e labels nas detecções
- **Múltiplas Detecções**: Processa grids com várias faces/escudos simultaneamente

### 🚀 Arquitetura Distribuída

- **Microsserviços**: APIs Python (Flask) separadas para cada tipo de análise
- **Message Broker**: RabbitMQ com Topic Exchange para roteamento inteligente
- **Escalabilidade**: Containers Docker independentes e orquestrados
- **Alta Performance**: Threshold otimizado (0.15) para detecções precisas

### 🎨 Visualização Inteligente

- **Cores por Categoria**: Cada emoção/time tem cor única
- **Confiança em Tempo Real**: Exibe % de confiança em cada detecção
- **Imagens Anotadas**: Salva resultados com marcações visuais
- **Logs Detalhados**: Acompanhamento completo do fluxo de processamento

---

## 🏗️ Arquitetura

### Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────────────┐
│                        GOOGLE COLAB (Treinamento)               │
│  ┌──────────────────┐         ┌──────────────────┐              │
│  │ YOLOv5 Training  │───────▶│  emotion_model.pt │             │
│  │   + GPU          │         │  team_model.pt   │              │
│  └──────────────────┘         └──────────────────┘              │
└──────────────────────┬──────────────────────────────────────────┘
                       │ Deploy dos modelos .pt
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SISTEMA DISTRIBUÍDO (Docker)                 │
│                                                                 │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────────┐    │
│  │  Gerador    │───▶│  RabbitMQ    │───▶│  Consumidor     │    │
│  │  Mensagens  │    │    Topic     │    │    Face          │    │
│  │  (Java)     │    │   Exchange   │    │   (Java)         │    │
│  │             │    │              │    │                  │    │
│  │ 5 msg/s     │    │ face_queue   │    │      │ HTTP      │    │
│  │ (imagens    │    │ team_queue   │    │      ▼           │    │
│  │  de test)   │    │              │    │  ┌──────────┐    │    │
│  └─────────────┘    └──────────────┘    │  │AI Service│    │    │
│                            │            │  │  Face    │    │    │
│                            │            │  │ (Flask)  │    │    │
│                            │            │  │          │    │    │
│                            │            │  │ YOLOv5   │    │    │
│                            │            │  │ Emotion  │    │    │
│                            │            │  └──────────     │    │
│                            │            └──────────────────┘    │
│                            │                                    │
│                            │             ┌──────────────────┐   │
│                            └────────────▶│  Consumidor      |  │
│                                          │    Team          │   │
│                                          │   (Java)         │   │
│                                          │                  │   │
│                                          │      │ HTTP      │   │
│                                          │      ▼           │   │
│                                          │  ┌──────────┐    │   │
│                                          │  │AI Service│    │   │
│                                          │  │  Team    │    │   │
│                                          │  │ (Flask)  │    │   │
│                                          │  │          │    │   │
│                                          │  │ YOLOv5   │    │   │
│                                          │  │  Teams   │    │   │
│                                          │  └──────────┘    │   │
│                                          └──────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Fluxo de Dados

1. **Gerador**: Lê imagens reais das pastas `test/images` e envia para RabbitMQ
2. **RabbitMQ**: Roteia mensagens usando routing keys (`face` ou `team`)
3. **Consumidores Java**: Recebem mensagens e chamam APIs Python via HTTP
4. **APIs Python**: Executam YOLOv5 nos modelos treinados e retornam:
   - Classe detectada (emoção ou time)
   - Confiança (0-1)
   - Imagem anotada com bounding boxes (base64)
5. **Consumidores**: Salvam imagens anotadas na pasta `processed`

---

## 💻 Tecnologias Utilizadas

### Backend

- **Java 17** - Consumidores e gerador de mensagens
- **Python 3.9** - APIs de inferência com Flask
- **Maven** - Gerenciamento de dependências Java

### Inteligência Artificial

- **YOLOv5** - Detecção de objetos em tempo real
- **PyTorch** - Framework de deep learning
- **Ultralytics** - Biblioteca YOLOv5 otimizada
- **OpenCV** - Processamento de imagens

### Infraestrutura

- **Docker & Docker Compose** - Containerização e orquestração
- **RabbitMQ 3.13** - Message broker com management UI
- **Flask** - Framework web leve para APIs REST

### Bibliotecas Auxiliares

- **Jackson** - Serialização JSON (Java)
- **OkHttp** - Cliente HTTP (Java)
- **Pillow** - Manipulação de imagens (Python)
- **NumPy, Pandas, SciPy** - Computação científica

---

## 🚀 Instalação Rápida

### Pré-requisitos

- **Docker Desktop** instalado e rodando
- **8GB RAM** disponível (mínimo 6GB)
- **10GB** de espaço em disco
- **Git** (para clonar o repositório)

### Passo 1: Clone o Repositório

```bash
git clone <url-do-repositorio>
cd sistema-distribuido
```

### Passo 2: Verifique os Modelos

Os modelos YOLOv5 treinados devem estar em:

```
sistema-distribuido/
├── ai-face-service/models/emotion_model.pt  (55MB)
└── ai-team-service/models/team_model.pt     (14MB)
```

> **📝 Nota**: Se os modelos não estiverem presentes, consulte a seção [Treinamento de Modelos](#-treinamento-de-modelos).

### Passo 3: Execute o Sistema

```bash
docker-compose up --build
```

Aguarde ~10-15 minutos no primeiro build (instalação de dependências).

### Passo 4: Acompanhe os Logs

```bash
# Em outro terminal
docker-compose logs -f
```

---

## 📖 Como Usar

### Iniciar o Sistema

```bash
# Build e start
docker-compose up --build

# Executar em background
docker-compose up -d

# Ver logs em tempo real
docker-compose logs -f
```

### Parar o Sistema

```bash
# Parar containers
docker-compose down

# Parar e limpar volumes
docker-compose down -v
```

### Verificar Status

```bash
# Listar containers ativos
docker-compose ps

# Monitorar recursos
docker stats
```

---

## 📁 Estrutura do Projeto

```
sistema-distribuido/
│
├── 📄 docker-compose.yml                    # Orquestração de serviços
├── 📄 README.md                             # Este arquivo
├── 📄 README_YOLO.md                        # Documentação detalhada YOLOv5
│
├── 📁 gerador-mensagens/                    # Gerador de mensagens Java
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/sistdistrib/gerador/
│       ├── GeradorMensagens.java            # Lê imagens de test e envia
│       └── ImageMessage.java
│
├── 📁 consumidor-face/                      # Consumidor de emoções Java
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/sistdistrib/consumidor/
│       ├── ConsumidorFace.java              # Processa mensagens de faces
│       └── ml/
│           └── EmotionApiClient.java        # Cliente HTTP para API Python
│
├── 📁 consumidor-team/                      # Consumidor de times Java
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/sistdistrib/consumidor/
│       ├── ConsumidorTeam.java              # Processa mensagens de times
│       └── ml/
│           └── TeamApiClient.java           # Cliente HTTP para API Python
│
├── 📁 ai-face-service/                      # API Python - Emoções
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app.py                               # Flask + YOLOv5 + Desenho de boxes
│   └── models/
│       └── emotion_model.pt                 # Modelo treinado (55MB)
│
├── 📁 ai-team-service/                      # API Python - Times
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app.py                               # Flask + YOLOv5 + Desenho de boxes
│   └── models/
│       └── team_model.pt                    # Modelo treinado (14MB)
│
├── 📁 notebooks/                            # Treinamento
│   └── YOLOv5_Training_Emotions_Teams.ipynb # Notebook Google Colab completo
│
└── 📁 images/                               # Datasets e resultados
    ├── faces/                               # Dataset de emoções
    │   ├── train/
    │   │   ├── images/                      # Imagens de treino
    │   │   └── labels/                      # Labels YOLO format
    │   ├── test/
    │   │   ├── images/                      # Imagens de teste (usadas pelo gerador)
    │   │   └── labels/                      # Labels YOLO format
    │   ├── data.yaml                        # Config do dataset
    │   └── processed/                       # 🎨 IMAGENS ANOTADAS SALVAS AQUI
    │
    └── teams/                               # Dataset de times
        ├── train/
        │   ├── images/
        │   └── labels/
        ├── test/
        │   ├── images/                      # Imagens de teste (usadas pelo gerador)
        │   └── labels/
        ├── data.yaml
        └── processed/                       # 🎨 IMAGENS ANOTADAS SALVAS AQUI
```

---

## 🎓 Treinamento de Modelos

### Datasets

#### 📦 Dataset de Times (Premier League)
- **Nome:** FindMyGame > crowd_square_1
- **URL:** https://universe.roboflow.com/premierleague-qymml/findmygame
- **Licença:** CC BY 4.0
- **Extração:** Baixe e extraia as imagens nas pastas:
  - `images/teams/train/images/` e `images/teams/train/labels/`
  - `images/teams/test/images/` e `images/teams/test/labels/`

#### 😀 Dataset de Expressões Faciais
- **Nome:** facial expression > 2023-07-23 10:29pm
- **URL:** https://universe.roboflow.com/fisrt-one/facial-expression-gtvqk
- **Licença:** CC BY 4.0
- **Extração:** Baixe e extraia as imagens nas pastas:
  - `images/faces/train/images/` e `images/faces/train/labels/`
  - `images/faces/test/images/` e `images/faces/test/labels/`

> **⚠️ Importante:** Certifique-se de extrair os datasets e organizá-los conforme a estrutura de diretórios definida na [arquitetura do projeto](#-estrutura-do-projeto) antes de iniciar o treinamento.

### Via Google Colab (Recomendado)

1. **Baixe os datasets** dos links acima
2. **Extraia e organize** as imagens conforme a estrutura indicada
3. **Abra o notebook** `notebooks/YOLOv5_Training_Emotions_Teams.ipynb` no Google Colab
4. **Configure o Google Drive** com a estrutura de datasets
5. **Execute todas as células** (treinamento com GPU)
6. **Baixe os modelos** gerados:
   - `emotion_model.pt` → `ai-face-service/models/`
   - `team_model.pt` → `ai-team-service/models/`

### Estrutura de Dados no Drive

```
MyDrive/YOLOv5_Models/
└── images/
    ├── faces/
    │   ├── train/
    │   │   ├── images/  (imagens .jpg)
    │   │   └── labels/  (anotações .txt)
    │   └── test/
    │       ├── images/
    │       └── labels/
    └── teams/
        ├── train/
        │   ├── images/
        │   └── labels/
        └── test/
            ├── images/
            └── labels/
```

### Classes Suportadas

#### 😀 Emoções (5 classes)

| ID  | Classe    | Descrição | Cor         |
| --- | --------- | --------- | ----------- |
| 0   | `anger`   | Raiva     | 🔴 Vermelho |
| 1   | `fear`    | Medo      | 🟣 Roxo     |
| 2   | `happy`   | Feliz     | 🟢 Verde    |
| 3   | `neutral` | Neutro    | 🔵 Azul     |
| 4   | `sad`     | Triste    | 🟠 Laranja  |

#### ⚽ Times Premier League (20 classes)

| ID  | Classe            | Time                    |
| --- | ----------------- | ----------------------- |
| 0   | `arsenal`         | Arsenal FC              |
| 1   | `aston-villa-new` | Aston Villa             |
| 2   | `bournemouth`     | AFC Bournemouth         |
| 3   | `brentford`       | Brentford FC            |
| 4   | `brighton`        | Brighton & Hove Albion  |
| 5   | `burnley`         | Burnley FC              |
| 6   | `chelsea`         | Chelsea FC              |
| 7   | `crystal-palace`  | Crystal Palace FC       |
| 8   | `everton`         | Everton FC              |
| 9   | `fulham`          | Fulham FC               |
| 10  | `liverpool`       | Liverpool FC            |
| 11  | `luton`           | Luton Town FC           |
| 12  | `mancity`         | Manchester City         |
| 13  | `manutd`          | Manchester United       |
| 14  | `newcastle`       | Newcastle United        |
| 15  | `nottingham`      | Nottingham Forest       |
| 16  | `sheffield`       | Sheffield United        |
| 17  | `tottenham`       | Tottenham Hotspur       |
| 18  | `westham`         | West Ham United         |
| 19  | `wolves`          | Wolverhampton Wanderers |

---

## 🌐 APIs

### API de Emoções - `ai-face-service:5000`

#### `GET /health`

Verifica status do serviço e carregamento do modelo.

**Resposta:**

```json
{
  "status": "healthy",
  "model_loaded": true,
  "model_path": "models/emotion_model.pt"
}
```

#### `POST /predict`

Realiza predição de emoção em uma imagem.

**Request:**

```json
{
  "image": "base64_encoded_image",
  "filename": "test_image.jpg"
}
```

**Resposta:**

```json
{
  "emotion": "happy",
  "confidence": 0.9235,
  "method": "YOLOv5",
  "annotated_image": "base64_encoded_annotated_image",
  "detections_count": 16,
  "filename": "test_image.jpg"
}
```

#### `GET /emotions`

Lista emoções suportadas.

**Resposta:**

```json
{
  "emotions": ["anger", "fear", "happy", "neutral", "sad"],
  "count": 5
}
```

### API de Times - `ai-team-service:5001`

#### `GET /health`

Verifica status do serviço.

#### `POST /predict`

Realiza predição de time em uma imagem.

**Request:** (igual à API de emoções)

**Resposta:**

```json
{
  "team": "liverpool",
  "confidence": 0.9635,
  "method": "YOLOv5",
  "annotated_image": "base64_encoded_annotated_image",
  "detections_count": 1,
  "filename": "test_image.jpg"
}
```

#### `GET /teams`

Lista times suportados.

**Resposta:**

```json
{
  "teams": ["arsenal", "aston-villa-new", ..., "wolves"],
  "count": 20
}
```

---

## 📊 Monitoramento

### RabbitMQ Management UI

**URL:** http://localhost:15672
**Login:** `admin` / `admin123`

**Funcionalidades:**

- 📈 Visualizar filas e mensagens em tempo real
- 🔄 Monitorar throughput (msg/s)
- 📊 Gráficos de taxa de publicação/consumo
- ⚙️ Configurar bindings e exchanges

### Logs dos Serviços

```bash
# Todos os serviços
docker-compose logs -f

# Apenas um serviço
docker-compose logs -f ai-face-service
docker-compose logs -f consumidor-face
docker-compose logs -f gerador-mensagens
```

### Métricas Esperadas

| Métrica                    | Valor                          |
| -------------------------- | ------------------------------ |
| **Taxa de Geração**        | 5 mensagens/segundo            |
| **Latência de API**        | ~300ms por predição            |
| **Throughput**             | ~10-15 imagens/segundo (total) |
| **Threshold de Confiança** | 0.15 (15%) mínimo              |

---

## 🎨 Visualização de Resultados

### Imagens Processadas com Bounding Boxes

As imagens salvas em `images/*/processed/` contêm:

✅ **Retângulos coloridos** ao redor de cada detecção
✅ **Labels** com classe e confiança (ex: "happy 0.92")
✅ **Múltiplas detecções** em grids com várias faces
✅ **Cores únicas** por categoria para fácil identificação

### Exemplo de Output

**Grid 4x4 com 16 faces:**

- Cada face tem seu próprio bounding box
- Labels mostram: `anger 0.85`, `happy 0.91`, etc.
- Cores diferentes para cada emoção

**Escudos de times:**

- Bounding box ao redor do escudo detectado
- Label: `liverpool 0.96`
- Cor única para o time

### Logs de Detecção

```
INFO:app:✅ Detectado: happy (92.35%)
INFO:app:✅ Detectado: burnley (96.35%)
```

---

## 🔧 Solução de Problemas

### Problema: Containers não iniciam

```bash
# Verificar logs
docker-compose logs

# Rebuild sem cache
docker-compose build --no-cache
docker-compose up
```

### Problema: Modelo não carrega

**Erro:** `❌ Erro ao carregar modelo: No module named 'seaborn'`

**Solução:** Verifique se `requirements.txt` está completo e faça rebuild.

```bash
docker-compose down
docker-compose up --build
```

### Problema: API retorna "unknown"

**Possíveis causas:**

- Modelo não está carregado
- Threshold muito alto
- Imagem sem detecções válidas

**Verificar:**

```bash
# Health check da API
curl http://localhost:5000/health
curl http://localhost:5001/health
```

### Problema: Filas crescendo indefinidamente

**Causa:** Consumidores mais lentos que o gerador (comportamento esperado).

**Para estabilizar:**

- Aumentar número de consumidores (scale)
- Reduzir taxa de geração
- Aumentar QoS dos consumidores

```bash
# Escalar consumidores
docker-compose up --scale consumidor-face=3 --scale consumidor-team=3
```

### Problema: Build muito lento

**Primeira vez:** Normal (~10-15 min) instalando PyTorch e dependências.

**Builds subsequentes:** Rápidos (~1-2 min) com cache do Docker.

**Para limpar cache:**

```bash
docker system prune -a
```

---

## 📚 Recursos Adicionais

### Documentação

- [README_YOLO.md](README_YOLO.md) - Detalhes completos da arquitetura YOLOv5
- [YOLOv5 Official Docs](https://docs.ultralytics.com/yolov5/)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/getstarted.html)

### Tutoriais

- [Google Colab Notebook](notebooks/YOLOv5_Training_Emotions_Teams.ipynb)
- [Docker Compose Reference](https://docs.docker.com/compose/)

---

## 👥 Contribuição

Contribuições são bem-vindas! Por favor:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

---

## 📄 Licença

Este projeto é para fins educacionais.

---

## 🙏 Agradecimentos

- **Ultralytics** pelo YOLOv5
- **RoboFlow** pelos datasets
- **RabbitMQ Team** pelo excelente message broker
- **Docker** pela simplificação de deployment

---

<div align="center">



[⬆ Voltar ao topo](#-sistema-distribuído-de-análise-de-imagens-com-yolov5)

</div>
