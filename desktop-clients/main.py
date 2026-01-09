import asyncio
import json
import os
import websockets
import klembord
import requests
import threading
import time
import tkinter as tk
from tkinter import messagebox, simpledialog
from clipboard_monitor import on_update

# --- INTERNAL SETTINGS ---
CONFIG_FILE = "config.json"
DEVICE_ID = "desktop-client"

def get_config_from_user():
    """Shows GUI to get only necessary server details."""
    root = tk.Tk()
    root.withdraw() 
    
    # 1. Ask for IP
    server_ip = simpledialog.askstring("TailSync Setup", "Enter Server IP (Tailscale IP):", parent=root)
    if not server_ip: return None
    
    # 2. Ask for Port
    port = simpledialog.askstring("TailSync Setup", "Enter Port Number (Default 8000):", initialvalue="8000", parent=root)
    if not port: return None

    config_data = {
        "server_ip": server_ip.strip(),
        "port": port.strip(),
        "debounce_delay": 0.5
    }
    
    with open(CONFIG_FILE, "w") as f:
        json.dump(config_data, f, indent=4)
    
    messagebox.showinfo("Setup Complete", "Connected! TailSync is now running in the background.")
    root.destroy()
    return config_data

def load_config():
    if not os.path.exists(CONFIG_FILE):
        config_data = get_config_from_user()
        if not config_data: os._exit(0)
        return config_data

    with open(CONFIG_FILE, "r") as f:
        try:
            return json.load(f)
        except:
            return get_config_from_user()

# Initialize Configuration
config = load_config()
SERVER_IP = config.get("server_ip")
PORT = config.get("port")
DEBOUNCE_DELAY = config.get("debounce_delay", 0.5)

HTTP_URL = f"http://{SERVER_IP}:{PORT}/"
WS_URL = f"ws://{SERVER_IP}:{PORT}/ws"

# --- SYNC LOGIC ---
last_received_html = None
last_received_plain = None
active_ws = None
main_loop = None
ignore_until = 0
last_sent_at = 0

def check_server():
    try:
        r = requests.get(HTTP_URL, timeout=3)
        return r.status_code == 200
    except:
        return False

async def send_to_network(websocket, plain, html):
    global last_sent_at
    p_safe = plain or ""
    h_safe = html or ""
    payload = {
        "plain_text": p_safe, "html_text": h_safe,
        "source": DEVICE_ID, "timestamp": time.time()
    }
    try:
        await websocket.send(json.dumps(payload))
        last_sent_at = time.time()
    except:
        pass

def on_clipboard_change():
    global last_received_html, last_received_plain, ignore_until, last_sent_at
    if time.time() < ignore_until or (time.time() - last_sent_at < DEBOUNCE_DELAY):
        return
    try:
        plain, html = klembord.get_with_rich_text()
        if plain == last_received_plain and html == last_received_html:
            return
        last_received_plain, last_received_html = plain, html
        if active_ws is not None:
            asyncio.run_coroutine_threadsafe(send_to_network(active_ws, plain, html), main_loop)
    except:
        pass

async def start_sync():
    global active_ws, main_loop, ignore_until, last_received_plain, last_received_html
    main_loop = asyncio.get_running_loop()
    while True:
        if not check_server():
            await asyncio.sleep(5)
            continue
        try:
            async with websockets.connect(WS_URL, ping_interval=10, ping_timeout=10) as websocket:
                active_ws = websocket
                async for message in websocket:
                    data = json.loads(message)
                    if data.get("type") == "ping" or data.get("source") == DEVICE_ID:
                        continue
                    p, h = data.get("plain_text") or "", data.get("html_text") or ""
                    if p == last_received_plain and h == last_received_html:
                        continue
                    ignore_until = time.time() + 1.5
                    last_received_plain, last_received_html = p, h
                    klembord.set_with_rich_text(p, h)
        except:
            active_ws = None
            await asyncio.sleep(5)

if __name__ == "__main__":
    klembord.init()
    t = threading.Thread(target=lambda: on_update(on_clipboard_change), daemon=True)
    t.start()
    try:
        asyncio.run(start_sync())
    except KeyboardInterrupt:
        pass