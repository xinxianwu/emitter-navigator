# Emitter Navigator

A JetBrains IDE plugin that provides **Go-to-Declaration (F12) navigation** between event emitters and listeners in JavaScript / TypeScript projects.

---

## Features

- Press **F12** on an event name string inside an `emit()` or `on()` call to jump to all paired counterparts across the project
- Results are shown in a popup sorted by: current file first, then by line number
- Supports `.js`, `.ts`, `.jsx`, `.tsx`, and `.vue` files
- Supports event names passed as variables (`const key = "event"`)

---

## Configuration

Open **Settings → Tools → Emitter Navigation** to customize which methods are treated as emitters or listeners.

### Format

One method per line. Optionally specify the argument index (0-based) where the event name is located:

```
methodName           # match any string argument (up to 9)
methodName:argIndex  # match only the argument at the given index
```

### Examples

| Setting | Matches |
|---|---|
| `emit` | `emit("event")`, `this.emit("type", data)` — any string arg |
| `send_io_room:1` | `send_io_room(room, "event", ...)` — second arg only |

**Default emit methods:** `emit`

**Default on methods:** `on`

---

## Supported Patterns

```js
// Direct string literal
socket.emit("connect", data);
this.on("connect", handler);

// Variable reference (const/let with string initializer)
const key = "connect";
socket.emit(key, data);
this.on(key, handler);

// Custom method with event at a specific argument position
send_io_room(room, "connect", data);   // configured as send_io_room:1
```

---

## Context Menu Action

Right-click in any JS/TS file and select **Find Emitter Pairs** to see a summary of all emit/on pairs in the current file.