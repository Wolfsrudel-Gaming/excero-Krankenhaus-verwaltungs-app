# KI-Service: Fotoanalyse (OCR + eigene neuronale Netze) für das KKH-System
# Läuft rein auf CPU – kein GPU nötig.
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    OMP_NUM_THREADS=4 \
    EASYOCR_MODULE_PATH=/opt/easyocr \
    TORCH_HOME=/opt/torch

# Systemabhängigkeiten für OpenCV (von EasyOCR genutzt)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libgl1 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# CPU-Wheels von PyTorch (deutlich kleiner als die CUDA-Variante)
COPY requirements.txt .
RUN pip install --no-cache-dir torch==2.5.1 torchvision==0.20.1 \
        --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir easyocr==1.7.2 fastapi==0.115.6 uvicorn==0.34.0 \
        psycopg2-binary==2.9.10 pillow==11.0.0 "numpy<2"

# Modelle beim Build herunterladen, damit der Container offline starten kann:
# 1) EasyOCR-Erkennungsmodelle (Deutsch + Englisch)
# 2) MobileNetV3-Small Gewichte (Backbone des eigenen Klassifikators)
RUN python -c "import easyocr; easyocr.Reader(['de','en'], gpu=False)" \
    && python -c "from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights; mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)"

COPY . .

EXPOSE 8100
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
