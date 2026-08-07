# Структура проекта

## Текущее состояние

Фактическая структура содержит начальные каркасы приложений:

```text
Reserve_Sin/
├── androidApp/                 # Android-модуль
│   └── src/main/java/.../data/  # Room-модели, DAO и Repository
│   └── src/main/java/.../data/settings/ # DataStore и Android Keystore
│   └── src/main/java/.../data/remote/   # Ktor-проверка и ручная синхронизация с сервером
│   └── src/main/java/.../ui/    # Главный экран и ViewModel
├── server/                     # Go-модуль
│   ├── cmd/reserve-server/     # Точка входа сервера
│   ├── cmd/reserve-import/     # Однократный импорт CSV истории
│   ├── internal/auth/          # Bearer-аутентификация API
│   ├── internal/database/      # SQLite, встроенные SQL-миграции и их запуск
│   └── internal/httpapi/       # HTTP-маршрутизация и /health
│   └── internal/logging/       # Конфигурация slog и HTTP-логирование
│   └── internal/importer/      # Разбор CSV и транзакционный импорт
├── deploy/                     # Материалы установки systemd, Caddy и резервного копирования
│   └── caddy/Caddyfile.local    # Loopback-only Caddy для локальной проверки /app/
├── web/app/                     # Статическая read-only страница истории (HTML, CSS, Vanilla JS)
├── gradle/libs.versions.toml   # Версии Android-зависимостей
├── VERSION                     # Текущая версия проекта
├── docs/
└── settings.gradle.kts
```

Служебные каталоги `.git`, `.agents` и `.codex` не содержат исходных материалов проекта, которые можно документировать как его модули.

## Документация

- `README.md` — краткое описание проекта и навигация.
- `docs/ТЗ.md` — исходное техническое задание.
- `docs/architecture.md` — целевая схема компонентов.
- `docs/configuration.md` — подтверждённые требования к конфигурации и секретам.
- `docs/database.md` — реализованная начальная серверная модель SQLite.
- `docs/api.md` — контракт API на уровне методов, заданном ТЗ.
- `docs/integrations.md` — внешние компоненты окружения.
- `docs/deployment.md` — целевое размещение и резервное копирование.
- `docs/development.md` — ограничения текущей локальной разработки и будущие проверки.
- `docs/decisions/` — решения, прямо закреплённые ТЗ.

`androidApp` содержит Room-модели локальных данных, DAO, `Repository`, ViewModel и Compose-экраны главной страницы, отдельной категории, категорий, создания операции, истории и настроек подключения. DataStore и Android Keystore сохраняют параметры сервера и токен; `data/export/HistoryCsv.kt` формирует и записывает CSV через системный URI. Ktor проверяет Bearer-подключение и синхронизирует категории, метки и операции по revision, включая отмены. `data/sync/SyncWork.kt` планирует и выполняет уникальную немедленную и периодическую фоновую синхронизацию WorkManager. `server` содержит SQLite-инициализацию с миграциями, Bearer-защищённый REST API, `GET /health`, проверяющий доступность базы, `GET /api/v1/web-history` для закрытого веба, пакет `internal/logging` на стандартном `log/slog` и транзакционную команду импорта CSV. `web/app` содержит статические файлы страницы `/app/`; `deploy/caddy/Caddyfile.local` — loopback-only контур их локального просмотра. Версии Android-зависимостей централизованы в `gradle/libs.versions.toml`; зависимости Go-сервера — в `server/go.mod`.

## Ожидаемые, но отсутствующие материалы

ТЗ требует инструкцию переноса исходной таблицы. Материалы production-развёртывания применены на VPS; сквозная проверка с Android и восстановление резервной копии вручную подтверждены. Перенос фактической истории остаётся отдельной задачей.
