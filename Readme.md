# TailSync

TailSync is a clipboard synchronization tool designed to work over a **Tailscale** network. It allows you to share copied text and HTML between Android, Windows, Linux, and a central server.

---

## 1. Server

The server is a Python/FastAPI application.

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

On the first run, select your **Connection Type**, enter your **Tailscale Server IP** and **Port** when prompted.

---

## 3. Android App

The Android app uses a foreground service to maintain a persistent connection for syncing.

### Setup

1. Install the provided APK on your device.
2. Open the app -> go to the settings menu from top right <br>
   -> Select your **Connection Type**, enter your **Server IP** and **Port** in the **Settings** screen.

**Note:** With android 10 and above background access of clipboard is not possible, we can try to do it via ADB (Didn't try it yet):

```bash
adb shell cmd appops set com.tailsync.app READ_CLIPBOARD allow
```

(Can also try to implement adb cmds via shizuku or use accessibility settings like Sefirah)
