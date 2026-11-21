FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app

# torch CPU (evita descargar ~2 GB de dependencias CUDA).
RUN pip install torch --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt requirements-ml.txt ./
RUN pip install -r requirements.txt -r requirements-ml.txt

COPY app ./app

EXPOSE 8096
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8096/actuator/health')"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8096", "--workers", "1"]
