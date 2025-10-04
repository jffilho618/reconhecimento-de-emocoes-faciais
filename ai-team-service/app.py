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

class TeamPredictor:
    def __init__(self, model_path='models/team_model.pt'):
        self.model_path = model_path
        self.model = None
        # Times na ordem exata do data.yaml
        self.teams = [
            'arsenal', 'aston-villa-new', 'bournemouth', 'brentford', 'brighton',
            'burnley', 'chelsea', 'crystal-palace', 'everton', 'fulham',
            'liverpool', 'luton', 'mancity', 'manutd', 'newcastle',
            'nottingham', 'sheffield', 'tottenham', 'westham', 'wolves'
        ]
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

    def predict_team(self, image_data):
        """Prediz time da imagem e retorna resultado com imagem anotada"""
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

                    if class_id < len(self.teams):
                        team = self.teams[class_id]
                        app.logger.info(f"✅ Detectado: {team} ({confidence:.2%})")
                        return {
                            'team': team,
                            'confidence': confidence,
                            'method': 'YOLOv5',
                            'annotated_image': annotated_base64,
                            'detections_count': len(results.pred[0])
                        }

                # Nenhuma detecção encontrada
                app.logger.warning("⚠️ Nenhuma detecção encontrada na imagem")
                return {
                    'team': 'unknown',
                    'confidence': 0.0,
                    'method': 'no_detection'
                }
            else:
                # Modelo não carregado
                app.logger.error("❌ Modelo não está carregado")
                return {
                    'team': 'unknown',
                    'confidence': 0.0,
                    'method': 'model_not_loaded'
                }

        except Exception as e:
            app.logger.error(f"Erro na predição: {e}")
            return {
                'team': 'unknown',
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

        # Cores para times (usando cores vibrantes)
        colors = ['#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#FF00FF',
                  '#00FFFF', '#FFA500', '#800080', '#008000', '#000080',
                  '#FF1493', '#00CED1', '#FFD700', '#FF4500', '#32CD32',
                  '#8B4513', '#4169E1', '#DC143C', '#00FA9A', '#FF6347']

        for detection in detections:
            # Extrair coordenadas e classe
            x1, y1, x2, y2, conf, class_id = detection[:6]
            x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
            class_id = int(class_id)
            confidence = float(conf)

            if class_id < len(self.teams):
                team = self.teams[class_id]
                color = colors[class_id % len(colors)]

                # Desenhar bounding box
                draw.rectangle([x1, y1, x2, y2], outline=color, width=3)

                # Desenhar label com fundo
                label = f"{team} {confidence:.2f}"
                bbox = draw.textbbox((x1, y1), label, font=font)
                draw.rectangle([bbox[0]-2, bbox[1]-2, bbox[2]+2, bbox[3]+2], fill=color)
                draw.text((x1, y1), label, fill='black', font=font)

        return image

# Instância global do preditor
predictor = TeamPredictor()

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
        result = predictor.predict_team(image_data)
        result['filename'] = filename

        app.logger.info(f"Predição para {filename}: {result['team']} ({result['confidence']:.2f})")

        return jsonify(result)

    except Exception as e:
        app.logger.error(f"Erro no endpoint predict: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/teams', methods=['GET'])
def get_teams():
    """Lista times suportados"""
    return jsonify({
        'teams': predictor.teams,
        'count': len(predictor.teams)
    })

if __name__ == '__main__':
    # Cria diretório de modelos se não existir
    os.makedirs('models', exist_ok=True)

    app.run(host='0.0.0.0', port=5001, debug=False)