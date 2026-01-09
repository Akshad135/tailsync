FROM python:3.11-slim

# Force stdin, stdout and stderr to be totally unbuffered.
ENV PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .

EXPOSE 8000

# Start uvicorn with the --log-level debug to see more info if needed
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]