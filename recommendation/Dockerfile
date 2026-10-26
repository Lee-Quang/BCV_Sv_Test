FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY service ./service
RUN useradd --create-home appuser && chown -R appuser:appuser /app
USER appuser
EXPOSE 8090
CMD ["uvicorn", "service.app:app", "--host", "0.0.0.0", "--port", "8090"]
