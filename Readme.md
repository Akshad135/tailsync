# TailSync

TailSync is a clipboard synchronization tool designed to work over a **Tailscale** network. It allows you to share copied text and HTML between Android, Windows, Linux, and a central server.

---

## 1. Server

The server is a Python/FastAPI application distributed as a multi-architecture Docker image.

### Setup

1. Create a `docker-compose.yml` file:

```yaml
services:
  tailsync:
    image: akshad135/tailsync:latest
    container_name: tailsync
    ports:
      - "8000:8000"
    restart: always
    environment:
      - PORT=8000
      - PYTHONUNBUFFERED=1
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

2. Start the server:

```bash
docker compose up -d
```

---

## 2. Desktop Client

The desktop client is a background script that monitors your local clipboard.
You can download it from [releases](https://github.com/Akshad135/tailsync/releases)

or

### Setup yourselves

1. Navigate to the `desktop-clients/` directory.
2. Install dependencies:

```bash
pip install -r requirements.txt
```

3. Run the client:

```bash
python main.py
```

4. On the first run, enter your **Tailscale Server IP** and **Port** when prompted.

---

## 3. Android App

The Android app uses a foreground service to maintain a persistent connection for syncing.

### Setup

1. Install the provided APK on your device.
2. Open the app and enter your **Tailscale Server IP** and **Port** in the **Settings** screen.
3. **Important**: Grant background clipboard access via ADB:

```bash
adb shell cmd appops set com.tailsync.app READ_CLIPBOARD allow
```

### Features

1. **Quick Settings Tile**: Add the "TailSync" tile to your notification shade for manual syncing.
2. **Auto-Connect**: Automatically starts the sync service on device boot.
3. **History**: View and copy your recently synced items from the dashboard.
