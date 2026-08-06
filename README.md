# Reserve_Sin

Reserve_Sin — персональное Android-приложение для ручного учёта накоплений по категориям с синхронизацией с личным сервером. Проект рассчитан на одного пользователя: он не подключается к банкам, не выполняет переводы и хранит только введённые пользователем операции.

## Текущее состояние

В репозитории есть начальная local-first основа Android-приложения и Go-сервер. Android хранит локальные категории, метки и операции в Room, позволяет создавать, изменять и архивировать категории, а также создавать одиночные и групповые операции со статусом `PENDING`. Главный Compose-экран получает данные через `ViewModel` и `Repository`; настройки позволяют сохранить HTTPS-адрес и API-токен в Android Keystore, проверить Bearer-подключение и вручную синхронизировать категории, метки и операции с сервером по revision. Метки в интерфейсе, история, отмена и фоновая синхронизация пока не реализованы. Сервер открывает SQLite, автоматически применяет SQL-миграции, предоставляет Bearer-защищённый REST API, `GET /health` и отдельную команду импорта CSV истории. Остальные описанные ниже возможности являются требованиями [ТЗ](docs/ТЗ.md).

## Предусмотренные возможности

- Категории накоплений, их цели, порядок отображения и архивирование.
- Одиночные и групповые операции пополнения и списания; история и мягкая отмена.
- Локальная работа без сети и последующая синхронизация.
- Фильтрация истории и экспорт в CSV.
- Личный HTTPS-сервер с SQLite и доступом по постоянному Bearer API-токену.

## Предусмотренный стек

- Android: Kotlin, Jetpack Compose, Material 3, MVVM, ViewModel, StateFlow, Room, Ktor Client, kotlinx.serialization, WorkManager, DataStore, Android Keystore.
- Сервер: Go, `net/http`, Chi, REST API, JSON, `database/sql`, SQLite, SQL-миграции, systemd и journald.
- Публикация: Caddy и домен `reserve-sin.duckdns.org`; Docker для Reserve_Sin не используется.

## Требования, установка и запуск

Для Android нужен JDK 17, Android SDK с API 37 и Gradle, совместимый с Android Gradle Plugin 9.2.0. Сборка debug APK: `gradle :androidApp:assembleDebug`.

Для сервера нужны Go 1.22 или новее, C-компилятор и CGO (SQLite-драйвер `github.com/mattn/go-sqlite3`). Перед запуском задайте `RESERVE_SIN_API_TOKEN`; проверка: `cd server && go test ./...`, запуск: `cd server && RESERVE_SIN_API_TOKEN=... go run ./cmd/reserve-server`.

ТЗ предполагает Ubuntu 24 для сервера, запуск Go-сервиса через systemd и Android-среду разработки для сборки приложения.

## Подробная документация

- [Архитектура](docs/architecture.md)
- [Структура проекта](docs/project-structure.md)
- [Конфигурация и секреты](docs/configuration.md)
- [База данных](docs/database.md)
- [Планируемый REST API](docs/api.md)
- [Внешние компоненты](docs/integrations.md)
- [Развертывание и резервные копии](docs/deployment.md)
- [Локальная разработка и проверка](docs/development.md)
- [Зафиксированные решения](docs/decisions/)
