# TailSync

TailSync is a simple, bi-directional clipboard synchronization tool designed to work over a **Tailscale** network. It allows you to instantly share copied text and HTML between your Android device, Windows/Linux desktop, and a central server "brain".

---

### How it works

1. **Server**: A central Python/FastAPI instance that receives clipboard updates from any device and broadcasts them to all others.
2. **Desktop**: A background script that monitors your local clipboard and pushes changes to the server.
3. **Android**: A foreground service that keeps a WebSocket alive to receive updates and uses a transparent activity to bypass Android's background clipboard restrictions.

---

## 1. Server

The server is the central hub. It is best run using Docker for a "set it and forget it" experience.

### Setup

1. Ensure you have **Docker** and **Docker Compose** installed on your server.
2. Navigate to the root directory of this project.
3. Run the following command:

```bash
docker-compose up -d
```

1. The server will now be running on port **8000**.

---

## 2. Desktop Client

The desktop client runs in the background with no terminal window. It will ask for your server details on the first run.

### Setup

1. Navigate to the `desktop-clients/` folder.
2. If you are running the source code:

- Install dependencies: `pip install -r requirements.txt`.
- Run the script: `python main.py`.

1. **On First Run**: A setup box will appear. Enter your **Tailscale Server IP** and **Port** (default 8000).
2. The app will create a `config.json` file and start syncing in the background.

---

## 3. Android App

The Android app is built with Jetpack Compose and designed to stay alive in the background via a foreground service.

### Setup

1. Install the APK on your device.
2. Open the app and go to **Settings**.
3. Enter your **Tailscale Server IP** and **Port**.
4. **Important**: Because of Android's privacy rules, you must grant a special permission via ADB to let the app read the clipboard in the background.

### ADB Permission Command

Connect your phone to your computer and run:

```bash
adb shell cmd appops set com.tailsync.app READ_CLIPBOARD allow
```

### Features

- **Quick Settings Tile**: Add the "TailSync" tile to your notification shade to sync manually at any time.
- **Auto-Connect**: The app can automatically start syncing whenever your phone boots up.
- **History**: View the last few items you've synced directly on the dashboard.
