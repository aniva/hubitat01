# PC Sleep & Wake-on-LAN Controller for Hubitat Elevation

A robust, multi-target PC deep sleep and Wake-on-LAN controller for Hubitat Elevation designed for bedroom/office acoustic automation.

![TV-Box Sleep Agent System Tray](images/tray_icon.jpg)

## Features

* **Single Virtual Switch Control:** Exposes standard `Switch` capability. Toggle `ON` to put PCs to sleep (Silent Night mode), or toggle `OFF` to wake both PCs up.
* **Strict Idempotency:** Duplicate commands do nothing. If machines are already sleeping, firing `on()` will not issue redundant shutdowns. If machines are awake, firing `off()` skips redundant WoL packets.
* **Manual & Dashboard Safety Directive:** Designed solely for manual dashboard or physical button triggers. Strictly exclude this device from voice assistant integrations (Alexa / Google Assistant / Siri) to prevent misheard phrases from causing unintended shutdowns.
* **Multi-Target Architecture:** Independently controls both Windows 11 (`tv-box`) and Linux (`ai-box`) targets.
* **Windows Taskbar Tray Indicator:** Includes a lightweight Python/Win32 background agent on Windows with a system tray icon, live hover tooltip, and right-click restart controls.
* **Pre-Flight Safety Interlock Reporting:** If `ai-box` is running a critical job (such as a 3D print monitor via `qidi-guard` or an interactive SSH session), the sleep command is safely rejected and reported in the Hubitat UI under `safetyStatus`.
* **Async & Thread-Safe:** Uses non-blocking `asynchttpPost` so Hubitat's event loop is never blocked.
* **Wake-on-LAN & PiKVM Fallback:** Sends native Hubitat Wake-on-LAN magic packets to wake machines; supports optional PiKVM v2 REST ATX power pulsing if WoL is insufficient.
* **Live Presence Detection:** Automatically pings targets on a configurable schedule to display live status (`online`, `sleeping`, `shutdown`, `offline`).

## Hardware Targets

| Target | Example Endpoint | Wake Method | Sleep Mode | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Linux Target** | `192.168.1.50:8099` | Wake-on-LAN (`AA:BB:CC:DD:EE:01`) / PiKVM | S5 Graceful Shutdown | Protects SAS HBAs & disk mounts |
| **Windows Target** | `192.168.1.60:8099` | Wake-on-LAN (`AA:BB:CC:DD:EE:02`) | S3 Standby | 0 RPM fan silence |

## Configuration & Environment

Copy `.env.example` to `.env` (which is gitignored) for storing your local private environment settings:

```bash
cp .env.example .env
```

## Installation via HPM (Hubitat Package Manager)

1. Open **Hubitat Package Manager**.
2. Select **Install** $\rightarrow$ **From a Repository**.
3. Point to `https://raw.githubusercontent.com/aniva/hubitat01/master/repository.json` (or find **PC Sleep and Wake Controller**).
4. Create a virtual device using driver type `PC Sleep and Wake Controller`.
5. Enter your target IPs, MAC addresses, and authentication tokens.
