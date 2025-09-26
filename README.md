# Adaptive Emotional State Manager (MobileSensorApp)

[![Android CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue)](#-ci)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple)](#-tech-stack)
[![Min SDK](https://img.shields.io/badge/minSdk-?\[укажи\]-informational)](#-requirements)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-yes-success)](#-tech-stack)

Android-приложение для **сбора физиологических и поведенческих метрик** пользователя (пульс, шаги, шум, акселерометр) и **аналитики эмоционального состояния**. Данные агрегируются и **отправляются каждые 15 минут** в бекенд (Firebase) через **WorkManager**, поддерживается **ручная отправка**, **оффлайн-кэш** и **панель аналитики** на Jetpack Compose.

> Цель проекта — собрать качественный датасет и исследовать подходы к детекции стресса/перегрузки с опорой на мобильные сенсоры.

---

## ✨ Возможности

* 📡 **Сбор сенсоров**: пульс*, шаги, уровень шума, акселерометр
  (*источник пульса: внешнее BLE/умные часы или системный провайдер — см. конфиг)
* 🔄 **Фоновая отправка каждые 15 минут** через WorkManager (retry/backoff, merge)
* 📴 **Offline-first**: локальный кэш (Room) + настройки и токены в DataStore
* 📊 **Аналитика**: экран метрик и история на Jetpack Compose (графики, списки)
* 🧪 **Метрики качества**: Crashlytics/Analytics (crash-free %, аномалии сенсоров)
* 🖱️ **Ручная отправка**: форс-батч из UI
* 🔐 **Конфиденциальность**: минимум PII, анонимные идентификаторы, opt-in

---

## 🧱 Технологический стек

* **Kotlin**, **Jetpack Compose**, **Material 3**, **Navigation**
* **MVVM**, **Clean Architecture**, Repository, **Hilt (DI)**
* **Coroutines** / **Flow**
* **Room**, **DataStore (Preferences)**, **Paging 3**
* **Retrofit/OkHttp** (если используется внешний REST)
* **WorkManager** (планирование фоновых задач)
* **Firebase**
* CI: **GitHub Actions**

---

## ✅ Требования

* **Android Studio** Giraffe/Koala или новее
* **Kotlin** 1.9+
* **minSdk**: `24` • **targetSdk**: `35`
* Аккаунт **Firebase** + `google-services.json` в `app/`

---

## 🚀 Установка и запуск

1. **Клонировать**

   ```bash
   git clone https://github.com/GogaTheCoder/MobileSensorApp.git
   cd MobileSensorApp
   ```
2. **Добавить Firebase конфиг**

   * Скачай `google-services.json` из Firebase Console и положи в `app/`.
3. **Собрать**

   ```bash
   ./gradlew clean assembleDebug
   ```
4. **Запустить** на устройстве/эмуляторе с разрешениями:

   * `ACTIVITY_RECOGNITION` (шаги)
   * `BODY_SENSORS` / `BODY_SENSORS_BACKGROUND` (пульс — если требуется)
   * `RECORD_AUDIO` (уровень шума)
   * `POST_NOTIFICATIONS` (для сервисов, опционально)

> ⚠️ На некоторых OEM (Xiaomi, Huawei и др.) вручную **разреши работу в фоне** и **отключи оптимизацию батареи** для стабильной работы WorkManager.

---

## 🗂️ Структура проекта (пример)

```
app/
 ├─ data/
 │   ├─ local/ (Room: Dao, Entities, Database)
 │   ├─ remote/ (Firebase / API)
 │   ├─ repository/ (SensorRepository, UploadRepository)
 │   └─ prefs/ (DataStore)
 ├─ domain/
 │   ├─ model/ (SensorData, Batch, Metrics ...)
 │   └─ usecase/ (CollectSensorData, EnqueueUpload, ...)
 ├─ ui/
 │   ├─ screens/
 │   │   ├─ dashboard/ (current values + charts)
 │   │   └─ history/ (list + details)
 │   ├─ components/ (charts, cards)
 │   └─ theme/
 ├─ workers/ (UploadWorker, MergeWorker, RetryPolicy)
 ├─ di/ (Hilt modules)
 ├─ utils/ (logging, permissions)
 └─ MainApplication.kt / MainActivity.kt
```

---

## 🧭 Архитектура

```text
Sensors -> Collectors -> Repository -> (Room cache) -> WorkManager -> Uploader -> Firebase
                                  \-> Flow -> ViewModel -> Compose UI (Charts/History)
```

* **MVVM + Clean**: UI ↔ ViewModel (Flow/State), бизнес-логика в use-cases, доступ к данным через Repository.
* **Offline-first**: все входящие данные кешируются в Room; отправка — батчами через WorkManager с backoff.
* **Observability**: Crashlytics для ошибок, Analytics — для пользовательских событий и аномалий сенсоров.

---

## ⚙️ Конфигурация

* **Интервал фоновой отправки**: по умолчанию 15 минут (PeriodicWorkRequest)
* **Слияние задач**: unique work + `ExistingPeriodicWorkPolicy.KEEP`
* **Ограничения**: только при сети / не в режиме low battery 

---

## 🔍 Данные/Схема (пример)

```kotlin
data class SensorData(
    val ts: Long,              // timestamp
    val heartRate: Int?,       // bpm (nullable)
    val steps: Int?,           // delta steps
    val noiseDb: Float?,       // decibels
    val accel: Vec3?,          // x/y/z
    val battery: Int?,         // %
    val deviceId: String       // anonymized id
)
```

Батчи отправляются списками `SensorData` за период. Для приватности не храним PII и геолокацию (опционально).

---

## 🖼️ Скриншоты

| Dashboard (текущие значения)              | History (списки + детали)               | Charts (графики)                       |
| ----------------------------------------- | --------------------------------------- | -------------------------------------- |
| ![screen1](docs/img/screen_dashboard.png) | ![screen2](docs/img/screen_history.png) | ![screen3](docs/img/screen_charts.png) |

> Добавь 2–3 PNG в `docs/img/` и обнови пути.

---

## 🧪 Тестирование

* **Unit**: ViewModel/use-cases — **JUnit5**, **MockK**, тесты `Flow`
* **UI**: базовые тесты Compose (по ключевым сценариям)
* **Инструментальные**: тест воркеров WorkManager (опционально)

Запуск:

```bash
./gradlew test
```

---

## 🔧 CI

Минимальный GitHub Actions workflow: `/.github/workflows/android.yml`

```yaml
name: Android CI
on:
  push: { branches: ["main"] }
  pull_request: { branches: ["main"] }

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - name: Gradle cache
        uses: gradle/actions/setup-gradle@v3
      - name: Lint & Unit tests
        run: ./gradlew lintDebug test
      - name: Assemble Debug
        run: ./gradlew assembleDebug
```

> Если используешь `google-services.json`, для PR можно собрать с mock-конфигом (или пропускать шаг Firebase-зависимых задач).

---

## 📈 Метрики и перформанс (рекомендуемые поля в README)

* **Crash-free sessions**: > **99%**
* **TTFB** при повторном открытии: −**25%** с кэшем
* **Надёжность фоновых задач**: ~**99%** успешных отправок
* **Батарея**: среднее потребление за сутки `[укажи]`
* **Размер батча/суток**: `[N]` записей, `[M]` батчей

---

## 🔐 Конфиденциальность

* Без PII по умолчанию; ID устройства — анонимный (UUID/DataStore)
* Нет передачи геолокации, если не включено явно
* Возможность **opt-out** от фонового сбора/аналитики в настройках

---

## 🗺️ Roadmap

* [ ] Экспорт данных (CSV/JSON)
* [ ] Расширение набора сенсоров (Gyro/HRV)
* [ ] Авто-детекция стресса (он-девайс модель / TF Lite)
* [ ] A/B метрики UI панели
* [ ] Локализация (ru/en)

---

## 🧩 FAQ

**Почему WorkManager не запускается на моём устройстве?**
→ Проверь «Battery optimization», «Auto-start», разрешения на фоновые задачи и сеть. На некоторых OEM нужно добавить приложение в исключения энергосбережения.

**Как добавить новый сенсор?**
→ Создай `Collector` в `data/...`, опиши модель в `domain/model`, зарегистрируй обработку в репозитории и добавь визуализацию в UI.

---

## 🤝 Вклад

PR приветствуются: заведите issue, опишите сценарий и предложите изменения.
Для больших фич — короткий RFC в issue.
