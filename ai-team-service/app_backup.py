import os
import torch
import cv2
import numpy as np
from flask import Flask, request, jsonify
import base64
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

class EmotionPredictor:
    def __init__(self, model_path='models/emotion_model.pt'):
        self.model_path = model_path
        self.model = None
        self.emotions = ['anger', 'fear', 'happy', 'neutral', 'sad']  # Ordem exata do data.yaml
        self.load_model()

    def load_model(self):
        """Carrega o modelo YOLOv5 treinado"""
        try:
            if os.path.exists(self.model_path):
                self.model = torch.hub.load('ultralytics/yolov5', 'custom', path=self.model_path, force_reload=True)
                self.model.eval()
                # Ajustar threshold de confiança para aceitar predições mais baixas
                self.model.conf = 0.15  # Padrão é 0.25, reduzimos para detectar mais
                app.logger.info(f"✅ Modelo carregado: {self.model_path}")
                app.logger.info(f"🎯 Threshold de confiança: {self.model.conf}")
            else:
                app.logger.warning(f"⚠️ Modelo não encontrado: {self.model_path}. Usando modo simulado.")
                self.model = None
        except Exception as e:
            app.logger.error(f"❌ Erro ao carregar modelo: {e}")
            self.model = None

    def predict_emotion(self, image_data):
        """Prediz emoção da imagem e retorna resultado com imagem anotada"""
        try:
            # Decodifica base64 para imagem
            image_bytes = base64.b64decode(image_data)
            image = Image.open(BytesIO(image_bytes))

            if self.model is not None:
                # Usa modelo real YOLOv5
                results = self.model(image)

                # Log de debug
                app.logger.debug(f"Detecções encontradas: {len(results.pred[0])}")

                # Processa resultados
                if len(results.pred[0]) > 0:
                    # Desenha bounding boxes na imagem
                    annotated_image = self.draw_detections(image.copy(), results.pred[0])

                    # Converte imagem anotada para base64
                    buffered = BytesIO()
                    annotated_image.save(buffered, format="JPEG")
                    annotated_base64 = base64.b64encode(buffered.getvalue()).decode('utf-8')

                    # Ordena por confiança e pega a melhor para retorno
                    detections = results.pred[0]
                    best_detection = detections[detections[:, 4].argmax()]

                    class_id = int(best_detection[5])
                    confidence = float(best_detection[4])

                    if class_id < len(self.emotions):
                        emotion = self.emotions[class_id]
                        app.logger.info(f"✅ Detectado: {emotion} ({confidence:.2%})")
                        return {
                            'emotion': emotion,
                            'confidence': confidence,
                            'method': 'YOLOv5',
                            'annotated_image': annotated_base64,
                            'detections_count': len(results.pred[0])
                        }

                # Nenhuma detecção encontrada
                app.logger.warning("⚠️ Nenhuma detecção encontrada na imagem")
                return {
                    'emotion': 'unknown',
                    'confidence': 0.0,
                    'method': 'no_detection'
                }
            else:
                # Modelo não carregado
                app.logger.error("❌ Modelo não está carregado")
                return {
                    'emotion': 'unknown',
                    'confidence': 0.0,
                    'method': 'model_not_loaded'
                }

        except Exception as e:
            app.logger.error(f"Erro na predição: {e}")
            return {
                'emotion': 'unknown',
                'confidence': 0.0,
                'method': 'error'
            }

    def draw_detections(self, image, detections):
        """Desenha bounding boxes e labels nas detecções"""
        draw = ImageDraw.Draw(image)

        # Tentar carregar fonte, se falhar usa padrão
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
        except:
            font = ImageFont.load_default()

        # Cores para cada emoção
        colors = {
            'anger': '#FF0000',    # Vermelho
            'fear': '#800080',     # Roxo
            'happy': '#00FF00',    # Verde
            'neutral': '#0000FF',  # Azul
            'sad': '#FFA500'       # Laranja
        }

        for detection in detections:
            # Extrair coordenadas e classe
            x1, y1, x2, y2, conf, class_id = detection[:6]
            x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
            class_id = int(class_id)
            confidence = float(conf)

            if class_id < len(self.emotions):
                emotion = self.emotions[class_id]
                color = colors.get(emotion, '#FFFFFF')

                # Desenhar bounding box
                draw.rectangle([x1, y1, x2, y2], outline=color, width=3)

                # Desenhar label com fundo
                label = f"{emotion} {confidence:.2f}"
                bbox = draw.textbbox((x1, y1), label, font=font)
                draw.rectangle([bbox[0]-2, bbox[1]-2, bbox[2]+2, bbox[3]+2], fill=color)
                draw.text((x1, y1), label, fill='black', font=font)

        return image

# Instância global do preditor
predictor = EmotionPredictor()

@app.route('/health', methods=['GET'])
def health():
    """Endpoint de saúde"""
    return jsonify({
        'status': 'healthy',
        'model_loaded': predictor.model is not None,
        'model_path': predictor.model_path
    })

@app.route('/predict', methods=['POST'])
def predict():
    """Endpoint principal de predição"""
    try:
        data = request.get_json()

        if not data or 'image' not in data:
            return jsonify({'error': 'Campo image é obrigatório'}), 400

        # Extrai dados
        image_data = data['image']
        filename = data.get('filename', 'unknown.jpg')

        # Faz predição
        result = predictor.predict_emotion(image_data)
        result['filename'] = filename

        app.logger.info(f"Predição para {filename}: {result['emotion']} ({result['confidence']:.2f})")

        return jsonify(result)

    except Exception as e:
        app.logger.error(f"Erro no endpoint predict: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/emotions', methods=['GET'])
def get_emotions():
    """Lista emoções suportadas"""
    return jsonify({
        'emotions': predictor.emotions,
        'count': len(predictor.emotions)
    })

if __name__ == '__main__':
    # Cria diretório de modelos se não existir
    os.makedirs('models', exist_ok=True)

    app.run(host='0.0.0.0', port=5000, debug=False)