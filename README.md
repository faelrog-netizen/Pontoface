# PontoFace — App de Registro de Ponto com Face ID

## 📁 Estrutura do Projeto

```
PontoFaceApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/pontoface/
│       │   ├── PontoFaceApp.kt          # Application class (Hilt)
│       │   ├── camera/
│       │   │   └── CameraSource.kt     # Interface + Real + Mock
│       │   ├── face/
│       │   │   └── FaceDetector.kt     # ML Kit wrapper
│       │   ├── data/
│       │   │   └── PontoDatabase.kt    # Room DB + Repository
│       │   ├── di/
│       │   │   └── AppModule.kt        # Hilt injection
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── CameraActivity.kt
│       │       ├── CameraViewModel.kt
│       │       └── FaceOverlayView.kt  # Oval guide + scan line
│       └── res/layout/
│           ├── activity_main.xml
│           └── activity_camera.xml
```

---

## 🧪 Como Testar com Mock Camera (ADB)

### 1. Compile o APK de debug
```bash
./gradlew assembleDebug
```

### 2. Instale no dispositivo
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Envie a imagem de teste para o dispositivo
```bash
# Envie qualquer foto de rosto como mock_face.jpg
adb push /caminho/para/foto.jpg /sdcard/mock_face.jpg
```

### 4. Rode o app
O badge **"⚙️ MODO TESTE"** aparecerá na tela de câmera, confirmando
que a câmera mock está ativa.

### 5. Troque a imagem mock sem reinstalar
```bash
# Basta enviar uma nova imagem via ADB — sem rebuild!
adb push /nova/foto.jpg /sdcard/mock_face.jpg
```

---

## ⚙️ Como Funciona a Injeção de Mock

O `build.gradle` define:
```groovy
debug {
    buildConfigField "boolean", "USE_MOCK_CAMERA", "true"
    buildConfigField "String",  "MOCK_IMAGE_PATH", '"/sdcard/mock_face.jpg"'
}
release {
    buildConfigField "boolean", "USE_MOCK_CAMERA", "false"
}
```

O Hilt injeta automaticamente:
- **DEBUG** → `MockCameraSource` (imagem estática em loop a 30fps)
- **RELEASE** → `RealCameraSource` (câmera frontal real)

A interface `CameraSource` é **idêntica** para ambos, então o
`CameraViewModel` e o `FaceDetector` não sabem a diferença.

---

## 🧠 Detecção Facial (ML Kit)

O `FaceDetector.kt` valida:
- ✅ Rosto detectado
- ✅ Olhando para a câmera (yaw < 15°, pitch < 15°)
- ✅ Olhos abertos (probabilidade > 60%)
- ✅ 10 frames consecutivos válidos antes de confirmar

**Confiança mínima para registrar:** 65%

---

## 📦 Dependências Principais

| Biblioteca | Uso |
|---|---|
| CameraX 1.3.1 | Preview + análise de frames |
| ML Kit Face Detection 16.1.5 | Detecção facial |
| Room 2.6.1 | Banco de dados local |
| Hilt 2.50 | Injeção de dependência |
| Kotlin Coroutines | Async/Flow |

---

## 🚀 Build de Produção

```bash
./gradlew assembleRelease
```

No release:
- `USE_MOCK_CAMERA = false` → câmera real
- Código minificado com ProGuard
- Badge de teste não aparece

