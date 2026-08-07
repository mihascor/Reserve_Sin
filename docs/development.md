# Локальная разработка и проверка

## Текущее состояние

В репозитории есть Gradle-конфигурация Android, Gradle Wrapper 9.4.1 и `server/go.mod`. Для Android нужен JDK, предоставленный Android Studio, и Android SDK API 37; Android-код компилируется с совместимостью Java 17. Для Go нужны Go 1.22 или новее, C-компилятор и CGO: SQLite подключён через `github.com/mattn/go-sqlite3`.

Текущая версия проекта хранится только в корневом файле `VERSION`; Android-конфигурация считывает его при сборке. При AGP 9.x Kotlin поддерживается самим Android Gradle Plugin, поэтому отдельный плагин `org.jetbrains.kotlin.android` не подключается. Room пока использует `com.android.legacy-kapt`, совместимый со встроенным Kotlin AGP 9.x.

Доступные после установки инструментов проверки:

- `./gradlew :androidApp:assembleDebug` — сборка debug APK и генерация кода Room через legacy KAPT;
- `cd server && go test ./...` — тесты HTTP-маршрута и SQLite-миграций;
- `cd server && RESERVE_SIN_API_TOKEN=... go run ./cmd/reserve-server` — локальный запуск сервера на `127.0.0.1:8080`; создаёт `server/reserve.db`, если не задан `RESERVE_SIN_DATABASE_PATH`. Токен обязателен и не должен попадать в историю команд на общей машине.
- `cd server && RESERVE_SIN_DATABASE_PATH=... go run ./cmd/reserve-import -input /путь/к/истории.csv -source-id имя-источника` — однократный импорт истории в SQLite. Перед запуском для рабочей базы следует создать резервную копию; повторяйте ту же команду только с тем же `source-id`.

При первом запуске сервер автоматически применяет SQL-миграции из `server/internal/database/migrations`. Отдельной команды миграции пока нет: миграции выполняются перед началом обработки HTTP-запросов.

## Локальный просмотр веб-интерфейса

Для разработки страницы `/app/` используется Caddy, а не Node.js или отдельный веб-сервер. Локальный конфиг [deploy/caddy/Caddyfile.local](../deploy/caddy/Caddyfile.local) слушает только `127.0.0.1:8443`, повторяет Basic Auth и внутренний Bearer proxy production-схемы, а статические файлы берёт из `web/` при запуске из корня репозитория.

1. Скопируйте [deploy/caddy/local.env.example](../deploy/caddy/local.env.example) в `deploy/caddy/local.env`. Этот файл игнорируется Git. Укажите один случайный локальный `RESERVE_SIN_API_TOKEN`, имя Basic Auth и bcrypt-хеш пароля. Хеш можно получить штатной интерактивной командой `caddy hash-password`: введите пароль, скопируйте единственную строку результата и вставьте её в `RESERVE_SIN_WEB_BASIC_AUTH_HASH` внутри одинарных кавычек. Токен Caddy и Go API должен совпадать. Не используйте production-токен или production-хеш.
2. В первом терминале из корня репозитория экспортируйте значения из `deploy/caddy/local.env` и запустите API:

```bash
set -a
. deploy/caddy/local.env
set +a
cd server && go run ./cmd/reserve-server
```

`RESERVE_SIN_DATABASE_PATH` по умолчанию указывает на отдельную базу в `/tmp`; чтобы увидеть историю, укажите путь к безопасной копии SQLite-базы.

3. Во втором терминале из корня репозитория загрузите env-файл в shell, проверьте и запустите Caddy:

```bash
set -a
. deploy/caddy/local.env
set +a
caddy validate --config deploy/caddy/Caddyfile.local
caddy run --config deploy/caddy/Caddyfile.local --envfile deploy/caddy/local.env
```
4. Откройте `http://127.0.0.1:8443/app/` и введите локальные Basic Auth credentials. Браузер должен получать данные через `/app-api/api/v1/web-history`; токен не должен появляться в HTML, JavaScript или Network.

Локальный HTTP допустим только потому, что listener привязан к loopback. Production-конфигурация продолжает работать по HTTPS. Не открывайте этот listener в сеть и не добавляйте `deploy/caddy/local.env` в Git.

## Сборка и установка на подключённый Android-телефон

Подтверждённая локальная проверка выполняется в Linux из корня репозитория. Телефон подключается по USB с включённой отладкой; перед установкой нужно подтвердить подключение на устройстве. Для управления экраном используется:

```bash
scrcpy --max-size 720 --max-fps 30 --bit-rate 4M
```

Для пересборки, установки debug APK без удаления данных и перезапуска приложения выполните команды по очереди:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am force-stop ru.reserve.sin
adb shell monkey -p ru.reserve.sin 1
```

Первая команда должна завершиться строкой `BUILD SUCCESSFUL`, а установка — `Success`. `adb install -r` обновляет приложение поверх существующей установки и сохраняет его локальные данные; последние две команды закрывают и вновь открывают приложение. Этот сценарий используется для ручной проверки изменений Android-интерфейса и миграций Room на телефоне.

## Локальное логирование

По умолчанию сервер выводит читаемые текстовые логи в стандартный поток вывода. Для более подробного вывода перед запуском установите `RESERVE_SIN_LOG_LEVEL=debug`; для production-формата — `RESERVE_SIN_ENV=production`. Например: `cd server && RESERVE_SIN_LOG_LEVEL=debug go run ./cmd/reserve-server`.

Сервер генерирует `X-Request-ID` для каждого HTTP-ответа; по нему можно связать ответ с записью лога. В логи попадают метод, путь без query-параметров, статус, длительность и `request_id`. Неожиданные ошибки запуска и panic-запросы дополнительно записываются с контекстом ошибки и стеком вызовов. Заголовки, тела запросов, query-параметры, токены и пароли не логируются.

## Подтверждённые требования к будущей разработке

- Android UI не обращается напрямую к API или базе; данные проходят через `ViewModel` и слой репозитория.
- Денежные значения должны быть целыми рублями, без `float` и `double`.
- Операции с одинаковым `client_operation_id` должны быть идемпотентны на сервере.
- Для SQL нужны параметризованные запросы и проверка входных данных.
- Токены и конфиденциальные данные не включаются в логи, исходники и Git.

После добавления API и Android-функций необходимо дополнить документ командами статического анализа. Перед публикацией следует проверять соответствие API, работу offline-сценария, повторную синхронизацию, отмену операций, права доступа и резервное восстановление.
