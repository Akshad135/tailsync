import asyncio
import json
import os
import sys
import threading
import time
import socket
import pystray
from PIL import Image, ImageDraw
import websockets
import klembord
from clipboard_monitor import on_update

if getattr(sys, 'frozen', False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CONFIG_FILE = os.path.join(BASE_DIR, "config.json")

DEVICE_ID = "desktop-client"
SINGLE_INSTANCE_PORT = 65433

class TailSyncClient:
    def __init__(self):
        self.instance_lock = self.check_single_instance()
        self.load_config()
        
        self.ws_url = None
        self.active_ws = None
        self.main_loop = None
        self.tray_icon = None
        
        self.is_connected = False
        self.should_reconnect = True
        self.last_sent_at = 0
        self.ignore_until = 0
        self.last_received_plain = None
        self.debounce_delay = 0.5

    def check_single_instance(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.bind(('127.0.0.1', SINGLE_INSTANCE_PORT))
            return s
        except socket.error:
            sys.exit(0)

    def get_config_from_user(self):
            import tkinter as tk
            from tkinter import simpledialog
            
            root = tk.Tk()
            root.withdraw()
            
            # 1. Ask for Protocol (Secure vs Insecure)
            secure_input = simpledialog.askstring(
                "TailSync Setup", 
                "Select Connection Type:\n1. Secure (WSS) - For https/vpn urls\n2. Standard (WS) - For direct IPs\n\nEnter 1 or 2:", 
                initialvalue="1",
                parent=root
            )
            if not secure_input: sys.exit(0)
            
            # Determine protocol based on input
            use_secure = True if secure_input.strip() == "1" else False

            # 2. Ask for IP
            server_ip = simpledialog.askstring("TailSync Setup", "Enter Tailscale IP or Domain:", parent=root)
            if not server_ip: sys.exit(0)

            # 3. Ask for Port
            port = simpledialog.askstring("TailSync Setup", "Enter Port (Default 8000):", initialvalue="8000", parent=root)
            if not port: sys.exit(0)
            
            # Save all 3 values to config
            config_data = {
                "server_ip": server_ip.strip(), 
                "port": port.strip(),
                "use_secure": use_secure
            }
            
            try:
                with open(CONFIG_FILE, "w", encoding='utf-8') as f:
                    json.dump(config_data, f, indent=4)
            except Exception:
                pass
                
            root.destroy()
            return config_data

    def load_config(self):
        if not os.path.exists(CONFIG_FILE):
            return self.get_config_from_user()
        try:
            with open(CONFIG_FILE, "r", encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            return self.get_config_from_user()

    def refresh_connection_info(self):
            config = self.load_config()
            
            server_ip = config.get("server_ip", "").strip()
            port = config.get("port", "8000").strip()
            use_secure = config.get("use_secure", False)
            
            self.debounce_delay = config.get("debounce_delay", 1)

            # Clean up IP if user accidentally pasted http:// or ws://
            if "://" in server_ip:
                server_ip = server_ip.split("://")[-1]
                if server_ip.endswith("/"):
                    server_ip = server_ip[:-1]

            # Select protocol based on the user's choice
            protocol = "wss" if use_secure else "ws"
            
            self.ws_url = f"{protocol}://{server_ip}:{port}/ws"

    def create_image(self, color, filled=True):
        image = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
        dc = ImageDraw.Draw(image)
        coords = (22, 22, 42, 42)
        
        if filled:
            dc.ellipse(coords, fill=color)
        else:
            dc.ellipse(coords, outline=color, width=3)
        return image

    def update_status(self, connected):
        try:
            if self.is_connected == connected:
                return
            
            self.is_connected = connected
            
            if self.tray_icon:
                color = "#00FF00" if connected else "#FF0000"
                self.tray_icon.icon = self.create_image(color, filled=connected)
                state_text = 'Connected' if connected else ('Paused' if not self.should_reconnect else 'Disconnected')
                self.tray_icon.title = f"TailSync: {state_text}"
        except Exception:
            pass

    def on_tray_click(self, icon, item):
        if self.should_reconnect:
            self.should_reconnect = False
            self.update_status(False)
            if self.active_ws and self.main_loop:
                asyncio.run_coroutine_threadsafe(self.active_ws.close(), self.main_loop)
        else:
            self.should_reconnect = True

    def quit_app(self, icon, item):
        icon.stop()
        if self.instance_lock:
            self.instance_lock.close()
        os._exit(0)

    async def send_clipboard(self, plain, html):
        if not self.active_ws or not self.should_reconnect: return
        payload = {
            "plain_text": plain or "",
            "html_text": html or "",
            "source": DEVICE_ID,
            "timestamp": time.time()
        }
        try:
            await self.active_ws.send(json.dumps(payload))
            self.last_sent_at = time.time()
        except Exception:
            pass

    def on_clipboard_change(self):
        if not self.should_reconnect: return
        if time.time() < self.ignore_until: return
        if (time.time() - self.last_sent_at) < self.debounce_delay: return

        try:
            plain, html = klembord.get_with_rich_text()
            if plain == self.last_received_plain: return

            if self.main_loop and self.active_ws:
                asyncio.run_coroutine_threadsafe(
                    self.send_clipboard(plain, html), 
                    self.main_loop
                )
        except Exception:
            pass

    async def run_sync_loop(self):
        self.main_loop = asyncio.get_running_loop()
        
        while True:
            if not self.should_reconnect:
                self.update_status(False)
                await asyncio.sleep(1)
                continue

            try:
                self.update_status(False)
                self.refresh_connection_info()
                
                async with websockets.connect(
                    self.ws_url, 
                    ping_interval=20, 
                    ping_timeout=20,
                    open_timeout=2
                ) as websocket:
                    
                    self.active_ws = websocket
                    await websocket.ping()
                    self.update_status(True)
                    
                    async for message in websocket:
                        if not self.should_reconnect: break

                        data = json.loads(message)
                        if data.get("source") == DEVICE_ID or data.get("type") == "ping":
                            continue
                            
                        plain = data.get("plain_text", "")
                        html = data.get("html_text", "")

                        if plain != self.last_received_plain:
                            self.ignore_until = time.time() + 1.0
                            self.last_received_plain = plain
                            self.last_received_html = html
                            klembord.set_with_rich_text(plain, html)

            except Exception:
                if self.should_reconnect:
                    self.update_status(False)
                    await asyncio.sleep(3)

    def start(self):
        try:
            klembord.init()
            
            def setup(icon):
                self.tray_icon.visible = True
                t_monitor = threading.Thread(target=lambda: on_update(self.on_clipboard_change), daemon=True)
                t_monitor.start()
                t_async = threading.Thread(target=lambda: asyncio.run(self.run_sync_loop()), daemon=True)
                t_async.start()

            icon_img = self.create_image("#FF0000", filled=False) 
            
            menu = pystray.Menu(
                pystray.MenuItem("Toggle Connection", self.on_tray_click, default=True, visible=False),
                pystray.MenuItem("Quit", self.quit_app)
            )
            
            self.tray_icon = pystray.Icon("TailSync", icon_img, "TailSync: Disconnected", menu)
            self.tray_icon.run(setup=setup)
            
        except Exception:
            os._exit(1)

if __name__ == "__main__":
    client = TailSyncClient()
    client.start()