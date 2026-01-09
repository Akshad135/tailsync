import asyncio
import json
import logging
import sys
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import List, Optional

# --- LOGGING SETUP ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("TailSync")

# --- CONFIGURATION ---
MAX_PAYLOAD_SIZE = 10 * 1024 * 1024

class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []
        self.last_payload: Optional[str] = None

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        logger.info(f"Connection accepted. Total clients: {len(self.active_connections)}")

        if self.last_payload:
            try:
                await websocket.send_text(self.last_payload)
                logger.debug("Sent cached clipboard to new client.")
            except Exception as e:
                logger.error(f"Failed to send cached state: {e}")

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
            logger.info(f"Connection closed. Total clients: {len(self.active_connections)}")

    async def broadcast(self, message: str, sender: WebSocket = None):
        try:
            parsed = json.loads(message)
            if parsed.get("type") != "ping":
                self.last_payload = message
        except json.JSONDecodeError:
            pass

        targets = [conn for conn in self.active_connections if conn != sender]

        for connection in targets:
            try:
                await connection.send_text(message)
            except Exception as e:
                logger.error(f"Broadcast failed for a client, disconnecting: {e}")
                self.disconnect(connection)

manager = ConnectionManager()

# --- HEARTBEAT LOGIC ---
async def server_heartbeat():
    """Sends a ping every 20s to keep connections alive through NAT/Tailscale."""
    logger.info("Heartbeat task started.")
    while True:
        try:
            await asyncio.sleep(20)
            if manager.active_connections:
                heartbeat = json.dumps({"type": "ping", "source": "server"})
                await manager.broadcast(heartbeat)
        except asyncio.CancelledError:
            logger.info("Heartbeat task stopped.")
            break
        except Exception as e:
            logger.error(f"Heartbeat error: {e}")

# --- LIFESPAN MANAGER ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("TailSync Server starting up...")
    heartbeat_task = asyncio.create_task(server_heartbeat())

    yield

    logger.info("TailSync Server shutting down...")
    heartbeat_task.cancel()
    try:
        await heartbeat_task
    except asyncio.CancelledError:
        pass

app = FastAPI(lifespan=lifespan)

@app.get("/")
async def get_status():
    return {"status": "online", "clients": len(manager.active_connections)}

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()

            # --- SECURITY CHECK ---
            payload_size = len(data)
            if payload_size > MAX_PAYLOAD_SIZE:
                logger.warning(f"Blocked oversized payload: {payload_size / (1024*1024):.2f} MB")
                continue

            try:
                parsed = json.loads(data)

                if parsed.get("type") == "ping":
                    continue

                if "source" in parsed:
                    source = parsed.get("source")
                    logger.info(f"RECV: Update from [{source}] | Size: {payload_size} bytes")

                    await manager.broadcast(data, sender=websocket)
            except json.JSONDecodeError:
                logger.warning("Received invalid JSON data")

    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket Error: {e}")
        manager.disconnect(websocket)