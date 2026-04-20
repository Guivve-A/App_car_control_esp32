# App_car_control_esp32

App Android (Jetpack Compose) para controlar un carro basado en **ESP32** por **Bluetooth SPP** (joystick + comandos por voz), con modo de telemetría y respuestas **TTS** (voz estilo “Wall‑E” usando Piper + fallback nativo de Android).

---

## Navegación rápida

- [Qué hace](#qué-hace)
- [Cómo se usa (rápido)](#cómo-se-usa-rápido)
- [Protocolo Bluetooth (comandos)](#protocolo-bluetooth-comandos)
- [Arquitectura (diagrama)](#arquitectura-diagrama)
- [Código por módulos (explorable)](#código-por-módulos-explorable)
- [Personalización](#personalización)
- [Troubleshooting](#troubleshooting)
- [Licencias / terceros](#licencias--terceros)

---

## Qué hace

- Se conecta por Bluetooth clásico (perfil **SPP**) a un dispositivo emparejado llamado **`Car-ESP32`**.
- Envía comandos simples (por ejemplo `F`, `B`, `L`, `R`, `S`) terminados en salto de línea (`\n`).
- Recibe mensajes desde el ESP32 y actualiza la UI (telemetría) o reproduce audio (mensajes `TTS:`).
- Integra reconocimiento de voz (es-ES) para disparar comandos.
- Genera voz offline con **Piper** (modelo ONNX) y le aplica un **procesamiento DSP** para un timbre robótico.

---

## Cómo se usa (rápido)

### 1) Requisitos

- Android Studio (recomendado), SDK Android (target 36), NDK (para el módulo nativo), dispositivo Android con Bluetooth.
- Emparejar el ESP32 en Ajustes del teléfono (Bluetooth) **antes** de abrir la app.

### 2) Abrir y ejecutar

1. Clona el repo y ábrelo en Android Studio.
2. Sincroniza Gradle.
3. Ejecuta en un dispositivo físico.
4. En la app, pulsa **Conectar** y valida el estado en el indicador de conexión.

> La app solicita permisos: Bluetooth (Android 12+), ubicación (requerido por Android para scan clásico), y micrófono (voz).

---

## Protocolo Bluetooth (comandos)

La comunicación es por texto UTF‑8. Cada comando se envía como:

```text
<COMANDO>\n
```

### Salida (App → ESP32)

| Acción | Comando |
|---|---|
| Adelante | `F` |
| Atrás | `B` |
| Izquierda | `L` |
| Derecha | `R` |
| Stop / soltar joystick | `S` |
| Giro 90° izquierda | `G` |
| Giro 90° derecha | `H` |
| Explorar (aleatorio) | `X` |
| Bailar | `D` |
| Vigilar / escaneo 360 | `V` |
| “Susto” / avance rápido | `P` |
| Encender ojos | `ON` |
| Apagar ojos | `OFF` |
| Cambiar modo UI → ESP32 | `STANDBY` / `DRIVING` / `MODE1` / `MODE2` |
| Enviar texto (saludo) | `TEXT:<texto>` |
| Telemetría: set hora | `SET_HORA:<HH:mm>` |
| Telemetría: set clima | `SET_TIEMPO:<texto>` |
| Telemetría: set nombre | `SET_NOMBRE:<texto>` |

### Entrada (ESP32 → App)

| Prefijo | Ejemplo | Qué hace en la app |
|---|---|---|
| `TTS:` | `TTS: Hola Juan` | Genera y reproduce audio (Piper → DSP Wall‑E). También evita duplicados consecutivos (ventana ~1.5s). |
| `HORA:` | `HORA:12:34` | Actualiza “Hora del teléfono” en Telemetría. |
| `TIEMPO:` | `TIEMPO:28 C Soleado` | Actualiza “Clima” en Telemetría. |
| `NOMBRE:` | `NOMBRE:Wall‑E` | Actualiza “Nombre robot” en Telemetría. |
| `OK:` / `ERROR:` | `OK:ON` / `ERROR:CMD` | Muestra un toast con el mensaje. |

---

## Arquitectura (diagrama)

```mermaid
flowchart LR
  UI[Jetpack Compose UI\nMainActivity] -->|events| VM[BluexViewModel]
  VM -->|sendCommand()| BT[BluetoothRepository\nRFCOMM/SPP]
  BT -->|texto + \\n| ESP[ESP32\nCar-ESP32]
  ESP -->|mensajes| BT
  BT -->|incomingMessages| VM

  VM -->|TTS:...| USE[HandleEsp32MessageUseCase]
  USE --> GM[GreetingManager]
  GM --> PIPER[PiperTtsEngine\nJNI + ONNX Runtime]
  PIPER --> DSP[WallEAudioProcessor\nDSP]
  DSP --> PLAY[RobotAudioPlayer\nAudioTrack + EQ]
```

---

## Código por módulos (explorable)

<details>
  <summary><strong>UI (Compose)</strong></summary>

- `app/src/main/java/com/example/bluex/MainActivity.kt`: pantallas por modo, permisos, barra superior, joystick, telemetría, etc.
- `app/src/main/java/com/example/bluex/ui/components/JoystickControl.kt`: joystick táctil con “dead zone”, direcciones y háptica.
- `app/src/main/java/com/example/bluex/ui/components/AnimatedConnectionIndicator.kt`: indicador animado de estado Bluetooth.
</details>

<details>
  <summary><strong>Estado y coordinación (ViewModel)</strong></summary>

- `app/src/main/java/com/example/bluex/BluexViewModel.kt`: orquesta UI ↔ Bluetooth ↔ voz ↔ TTS, y maneja prefijos de mensajes entrantes.
- Modos principales (`AppMode`): `STANDBY`, `DRIVING`, `MODE1` (saludos), `MODE2` (telemetría).
</details>

<details>
  <summary><strong>Bluetooth (SPP/RFCOMM)</strong></summary>

- `app/src/main/java/com/example/bluex/bluetooth/BluetoothRepository.kt`
  - Busca el dispositivo emparejado con nombre `Car-ESP32`.
  - Conecta por UUID SPP estándar: `00001101-0000-1000-8000-00805F9B34FB`.
  - Envía comandos con `\n` y escucha mensajes entrantes en un loop (coroutines).
  - Auto‑reconexión (hasta 5 intentos, delay 3s).
</details>

<details>
  <summary><strong>Voz (SpeechRecognizer)</strong></summary>

- `app/src/main/java/com/example/bluex/voice/VoiceControlManager.kt`
  - Reconoce voz en `es-ES`.
  - Normaliza texto y lo mapea a comandos (`F`, `B`, `L`, `R`, `S`, `X`, `D`, `V`, `P`, `ON`, `OFF`, …).
</details>

<details>
  <summary><strong>TTS “Wall‑E” (Piper + DSP)</strong></summary>

- `app/src/main/java/com/example/bluex/tts/HandleEsp32MessageUseCase.kt`: procesa `TTS:` + supresión de duplicados.
- `app/src/main/java/com/example/bluex/tts/GreetingManager.kt`: pipeline “texto → audio”.
- `app/src/main/java/com/example/bluex/tts/PiperTtsEngine.kt`: síntesis offline (assets → runtime → JNI).
- `app/src/main/java/com/example/bluex/tts/WallEAudioProcessor.kt`: cadena DSP (bitcrush, decimate, resonancia, wobble, chirps).
- `app/src/main/java/com/example/bluex/tts/RobotAudioPlayer.kt`: reproducción con AudioTrack + EQ + pitch/speed.
- `app/src/main/java/com/example/bluex/tts/FallbackTextToSpeechEngine.kt`: fallback con TTS nativo (si Piper falla).

Assets del runtime de Piper:
- `app/src/main/assets/piper/`
  - `es_ES-carlfm-x_low.onnx` + `es_ES-carlfm-x_low.onnx.json`
  - `espeak-ng-data/`
</details>

<details>
  <summary><strong>Native (C++ / CMake / JNI)</strong></summary>

- `app/src/main/cpp/CMakeLists.txt`: build del puente JNI y dependencias.
- `app/src/main/cpp/bluex_piper_jni.cpp`: bindings nativos para crear sintetizador y sintetizar.
</details>

---

## Personalización

- Cambiar nombre del dispositivo Bluetooth:
  - Edita `deviceName = "Car-ESP32"` en `app/src/main/java/com/example/bluex/bluetooth/BluetoothRepository.kt`.
- Cambiar mapeo de comandos de voz:
  - Edita `commandMap` en `app/src/main/java/com/example/bluex/voice/VoiceControlManager.kt`.
- Cambiar modelo/paths de Piper:
  - Revisa `buildConfigField(...)` en `app/build.gradle.kts`.
- Ajustar el “timbre Wall‑E”:
  - `RobotVoiceStyle` en `app/src/main/java/com/example/bluex/tts/RobotVoiceStyle.kt`.

---

## Troubleshooting

<details>
  <summary><strong>No conecta al ESP32</strong></summary>

- Verifica que el ESP32 esté emparejado y el nombre coincida exactamente con `Car-ESP32`.
- Android 12+: confirma permisos `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN`.
- Asegura que el ESP32 esté usando SPP/RFCOMM y el UUID SPP estándar.
</details>

<details>
  <summary><strong>El joystick “no responde”</strong></summary>

- Confirma que el modo sea **Control** (DRIVING) y que la app esté conectada.
- Revisa que el ESP32 entienda `F/B/L/R/S` y procese `\n` como fin de comando.
</details>

<details>
  <summary><strong>No funciona la voz</strong></summary>

- Revisa permiso de micrófono.
- Algunos dispositivos requieren conectividad para SpeechRecognizer (depende del motor de reconocimiento).
- Puedes probar sin voz usando el joystick y los botones.
</details>

<details>
  <summary><strong>Piper falla / no se oye la voz robótica</strong></summary>

- Verifica que existan los assets en `app/src/main/assets/piper/` (modelo + config + `espeak-ng-data/`).
- La app intenta fallback con el TTS nativo de Android si Piper falla.
</details>

---

## Licencias / terceros

Este repo incluye dependencias y assets de terceros. Revisa sus licencias antes de redistribuir:

- Piper (vendor): `/_vendor/piper1-gpl/COPYING`
- ONNX Runtime (AAR): `/_vendor/onnxruntime-android-1.22.0.aar`
- espeak-ng data: `app/src/main/assets/piper/espeak-ng-data/`

---

## Créditos

Hecho para un proyecto de control de carro con ESP32 + experiencia “robot” (voz y UI).

