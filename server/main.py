import asyncio
import json
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import List

app = FastAPI()

class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        print(f"DEBUG: Connection accepted. Total: {len(self.active_connections)}", flush=True)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
            print(f"DEBUG: Connection closed. Total: {len(self.active_connections)}", flush=True)

    async def broadcast(self, message: str, sender: WebSocket = None):
        targets = [conn for conn in self.active_connections if conn != sender]
        if not targets:
            print("DEBUG: No other clients connected to receive broadcast.", flush=True)

        for connection in targets:
            try:
                await connection.send_text(message)
            except Exception as e:
                print(f"DEBUG: Send failed, disconnecting a client: {e}", flush=True)
                self.disconnect(connection)

manager = ConnectionManager()

# --- HEARTBEAT LOGIC ---
async def server_heartbeat():
    while True:
        await asyncio.sleep(20)
        if manager.active_connections:
            heartbeat = json.dumps({"type": "ping", "source": "server"})
            await manager.broadcast(heartbeat)

@app.on_event("startup")
async def startup_event():
    print("SERVER START: Bi-directional Sync Server is running...", flush=True)
    asyncio.create_task(server_heartbeat())

@app.get("/")
async def get_status():
    return {"status": "online", "clients": len(manager.active_connections)}

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            try:
                parsed = json.loads(data)
                # Filter heartbeats from logs to keep it clean
                if parsed.get("type") == "ping":
                    continue

                if "source" in parsed:
                    source = parsed.get("source")
                    content = parsed.get("plain_text", "")[:20]
                    print(f"RECV: From [{source}] content: {content}...", flush=True)
                    await manager.broadcast(data, sender=websocket)
            except json.JSONDecodeError:
                print("DEBUG: Received non-JSON data", flush=True)
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        print(f"ERROR: {e}", flush=True)
        manager.disconnect(websocket)