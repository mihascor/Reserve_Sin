# Reserve_Sin

Reserve_Sin — персональное Android-приложение для ручного учёта накоплений по категориям с синхронизацией с личным сервером. Проект рассчитан на одного пользователя: он не подключается к банкам, не выполняет переводы и хранит только введённые пользователем операции.

## Текущее состояние

В репозитории есть начальные каркасы Android-приложения и Go-сервера, их сборочные конфигурации и зависимости. Android запускает базовый Compose-экран, а сервер предоставляет `GET /health` без подключения к базе. Бизнес-функции, миграции, синхронизация, авторизация и серверная конфигурация ещё не реализованы. Остальные описанные ниже возможности являются требованиями [ТЗ](docs/ТЗ.md).

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

Для Android нужен JDK 17, Android SDK с API 36 и Gradle, совместимый с Android Gradle Plugin 9.2.0. Сборка debug APK: `gradle :androidApp:assembleDebug`.

Для сервера нужен Go 1.22 или новее. Проверка: `cd server && go test ./...`. Запуск: `cd server && go run ./cmd/reserve-server`.

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
