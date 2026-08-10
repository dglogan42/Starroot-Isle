/**
 * Starroot Isle — online visit co-op + tile patch sync
 *
 * Protocol (JSON over WebSocket):
 *  → { type: "create", name, seed }
 *  ← { type: "created", room, playerId, seed, host, peers, patches }
 *  → { type: "join", name, room }
 *  ← { type: "joined", room, playerId, seed, host, peers, patches }
 *  → { type: "state", x, y, fx, fy, tool, day, color }
 *  ← { type: "peers", peers }
 *  → { type: "tile", patch: { x,y,t,b,rh,th,ck?,cs?,cw? } }
 *  ← { type: "tile", from, name, patch }
 *  → { type: "tiles", patches: [...] }   // batch (host snapshot)
 *  ← { type: "tiles", patches }
 *  → { type: "day_tick" }               // host slept
 *  ← { type: "day_tick", from, name }
 *  → { type: "say", text }
 *  ← { type: "chat", from, name, text }
 *  → { type: "ping" } / ← { type: "pong" }
 */

import { WebSocketServer } from "ws";
import { randomBytes } from "crypto";

const PORT = Number(process.env.PORT || 8790);
const MAX_PATCHES = 4000;

/** @type {Map<string, Room>} */
const rooms = new Map();

/**
 * @typedef {{ id: string, name: string, ws: import('ws').WebSocket, x: number, y: number, fx: number, fy: number, tool: string, color: number, host: boolean, day: number, lastSeen: number }} Peer
 * @typedef {{ code: string, seed: number, peers: Map<string, Peer>, patches: Map<string, object> }} Room
 */

function code() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let s = "";
  const b = randomBytes(4);
  for (let i = 0; i < 4; i++) s += alphabet[b[i] % alphabet.length];
  return s;
}

function uniqueCode() {
  for (let i = 0; i < 20; i++) {
    const c = code();
    if (!rooms.has(c)) return c;
  }
  return code() + code().slice(0, 2);
}

function patchKey(p) {
  return `${p.x},${p.y}`;
}

function sanitizePatch(raw) {
  if (!raw || typeof raw !== "object") return null;
  const x = Number(raw.x);
  const y = Number(raw.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  if (x < 0 || y < 0 || x > 128 || y > 128) return null;
  const t = String(raw.t || raw.type || "").slice(0, 32);
  if (!t) return null;
  const out = {
    x: Math.floor(x),
    y: Math.floor(y),
    t,
    b: String(raw.b || "MEADOW").slice(0, 24),
    rh: Math.max(0, Math.min(20, Number(raw.rh) || 0)),
    th: Math.max(0, Math.min(20, Number(raw.th) || 0)),
  };
  if (raw.ck) {
    out.ck = String(raw.ck).slice(0, 32);
    out.cs = Math.max(0, Math.min(10, Number(raw.cs) || 0));
    out.cw = !!raw.cw;
  }
  return out;
}

function allPublic(room) {
  return [...room.peers.values()].map((p) => ({
    id: p.id,
    name: p.name,
    x: p.x,
    y: p.y,
    fx: p.fx,
    fy: p.fy,
    tool: p.tool,
    color: p.color,
    host: p.host,
    day: p.day,
  }));
}

function patchesArray(room) {
  return [...room.patches.values()];
}

function send(ws, obj) {
  if (ws.readyState === 1) ws.send(JSON.stringify(obj));
}

function broadcast(room, obj, exceptId = null) {
  const data = JSON.stringify(obj);
  for (const p of room.peers.values()) {
    if (p.id === exceptId) continue;
    if (p.ws.readyState === 1) p.ws.send(data);
  }
}

function leave(peer, roomCode) {
  if (!peer || !roomCode) return;
  const room = rooms.get(roomCode);
  if (!room) return;
  room.peers.delete(peer.id);
  broadcast(room, { type: "peers", peers: allPublic(room) });
  broadcast(room, {
    type: "chat",
    from: "system",
    name: "Isle",
    text: `${peer.name} left the island.`,
  });
  if (room.peers.size === 0) {
    rooms.delete(roomCode);
  } else if (peer.host) {
    const next = room.peers.values().next().value;
    if (next) {
      next.host = true;
      broadcast(room, {
        type: "chat",
        from: "system",
        name: "Isle",
        text: `${next.name} is now host.`,
      });
      broadcast(room, { type: "peers", peers: allPublic(room) });
    }
  }
}

function storePatch(room, patch) {
  if (room.patches.size >= MAX_PATCHES && !room.patches.has(patchKey(patch))) {
    // drop oldest-ish: clear 10% 
    const keys = [...room.patches.keys()].slice(0, Math.floor(MAX_PATCHES * 0.1));
    for (const k of keys) room.patches.delete(k);
  }
  room.patches.set(patchKey(patch), patch);
}

const wss = new WebSocketServer({ port: PORT });
console.log(`Starroot Isle online co-op on ws://0.0.0.0:${PORT}`);

wss.on("connection", (ws) => {
  /** @type {Peer | null} */
  let peer = null;
  /** @type {string | null} */
  let roomCode = null;

  ws.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(String(raw));
    } catch {
      return send(ws, { type: "error", message: "bad json" });
    }

    const type = msg.type;

    if (type === "ping") return send(ws, { type: "pong" });

    if (type === "create") {
      if (peer) return send(ws, { type: "error", message: "already in room" });
      const seed = Number(msg.seed) || Date.now();
      const name = String(msg.name || "Settler").slice(0, 16);
      const c = uniqueCode();
      const id = randomBytes(4).toString("hex");
      /** @type {Room} */
      const room = { code: c, seed, peers: new Map(), patches: new Map() };
      peer = {
        id,
        name,
        ws,
        x: 28,
        y: 28,
        fx: 0,
        fy: 1,
        tool: "HOE",
        color: 0,
        host: true,
        day: 1,
        lastSeen: Date.now(),
      };
      room.peers.set(id, peer);
      rooms.set(c, room);
      roomCode = c;
      return send(ws, {
        type: "created",
        room: c,
        playerId: id,
        seed,
        host: true,
        peers: allPublic(room),
        patches: patchesArray(room),
      });
    }

    if (type === "join") {
      if (peer) return send(ws, { type: "error", message: "already in room" });
      const c = String(msg.room || "")
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, "")
        .slice(0, 6);
      const room = rooms.get(c);
      if (!room) return send(ws, { type: "error", message: "room not found" });
      if (room.peers.size >= 4) {
        return send(ws, { type: "error", message: "room full (max 4)" });
      }
      const name = String(msg.name || "Visitor").slice(0, 16);
      const id = randomBytes(4).toString("hex");
      peer = {
        id,
        name,
        ws,
        x: 28.5,
        y: 28.5,
        fx: 0,
        fy: 1,
        tool: "HAND",
        color: room.peers.size % 4,
        host: false,
        day: 1,
        lastSeen: Date.now(),
      };
      room.peers.set(id, peer);
      roomCode = c;
      send(ws, {
        type: "joined",
        room: c,
        playerId: id,
        seed: room.seed,
        host: false,
        peers: allPublic(room),
        patches: patchesArray(room),
      });
      broadcast(
        room,
        {
          type: "chat",
          from: "system",
          name: "Isle",
          text: `${name} arrived to visit.`,
        },
        id
      );
      broadcast(room, { type: "peers", peers: allPublic(room) });
      return;
    }

    if (!peer || !roomCode) {
      return send(ws, { type: "error", message: "join or create a room first" });
    }
    const room = rooms.get(roomCode);
    if (!room) return;

    if (type === "state") {
      peer.x = Number(msg.x) || peer.x;
      peer.y = Number(msg.y) || peer.y;
      peer.fx = Number(msg.fx) || 0;
      peer.fy = Number(msg.fy) || 1;
      peer.tool = String(msg.tool || peer.tool).slice(0, 20);
      peer.color = Number(msg.color) || peer.color;
      peer.day = Number(msg.day) || peer.day;
      peer.lastSeen = Date.now();
      broadcast(room, { type: "peers", peers: allPublic(room) });
      return;
    }

    if (type === "tile") {
      const patch = sanitizePatch(msg.patch || msg);
      if (!patch) return send(ws, { type: "error", message: "bad tile patch" });
      storePatch(room, patch);
      broadcast(
        room,
        { type: "tile", from: peer.id, name: peer.name, patch },
        peer.id
      );
      return;
    }

    if (type === "tiles") {
      const arr = Array.isArray(msg.patches) ? msg.patches : [];
      const cleaned = [];
      for (const raw of arr.slice(0, 500)) {
        const p = sanitizePatch(raw);
        if (p) {
          storePatch(room, p);
          cleaned.push(p);
        }
      }
      if (cleaned.length) {
        broadcast(room, { type: "tiles", from: peer.id, patches: cleaned }, peer.id);
      }
      return;
    }

    if (type === "day_tick") {
      broadcast(
        room,
        { type: "day_tick", from: peer.id, name: peer.name },
        peer.id
      );
      broadcast(room, {
        type: "chat",
        from: "system",
        name: "Isle",
        text: `${peer.name} rested — crops grow across the shared isle.`,
      });
      return;
    }

    if (type === "say") {
      const text = String(msg.text || "").slice(0, 80);
      if (!text) return;
      broadcast(room, {
        type: "chat",
        from: peer.id,
        name: peer.name,
        text,
      });
      return;
    }
  });

  ws.on("close", () => leave(peer, roomCode));
  ws.on("error", () => leave(peer, roomCode));
});
