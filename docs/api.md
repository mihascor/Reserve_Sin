# REST API

## Статус контракта

Go API реализован для категорий, меток, операций, сводки и синхронизации. Все маршруты `/api/v1` требуют корректный Bearer-токен; `GET /health` оставлен доступным без него. Время хранится и возвращается в UTC ISO 8601, денежные поля с суффиксом `_rub` — только целые рубли.

Все данные передаются в JSON с UTF-8, даты и время — в ISO 8601. Денежные суммы передаются целыми числами в рублях, без дробной части. Методы `/api/v1` требуют заголовок `Authorization: Bearer <token>`; неверный токен должен давать `401`. API доступен по HTTPS через Caddy.

| Метод | Назначение |
| --- | --- |
| `GET /health` | Проверка состояния приложения, БД и версии сервера без раскрытия чувствительных сведений. |
| `GET /api/v1/categories` | Получить категории. |
| `POST /api/v1/categories` | Создать категорию. |
| `PATCH /api/v1/categories/{id}` | Изменить название, цель, порядок или архивность категории. |
| `GET /api/v1/labels` | Получить метки. |
| `POST /api/v1/labels` | Создать метку. |
| `PATCH /api/v1/labels/{id}` | Изменить или архивировать метку. |
| `GET /api/v1/transactions` | Получить операции; параметры: `after`, `before`, `category_id`, `label_id`, `include_cancelled`, `limit`, `cursor`. |
| `POST /api/v1/transactions` | Создать одиночную операцию. |
| `POST /api/v1/transaction-batches` | Создать групповую операцию. |
| `PATCH /api/v1/transactions/{id}` | Изменить операцию в пределах ещё не определённых правил аудита. |
| `POST /api/v1/transactions/{id}/cancel` | Мягко отменить операцию. |
| `GET /api/v1/summary` | Получить общий и категорийные остатки, цели, прогресс, дату последней операции и revision. |
| `GET /api/v1/changes?after={revision}` | Получить изменённые категории, метки и операции после revision, включая отмены, и текущую revision. |

Повтор `POST` операции с тем же `client_operation_id` не должен создавать дубль: сервер возвращает существующую операцию либо успешный идемпотентный ответ.

`PATCH /api/v1/transactions/{id}` намеренно отвечает `409`: изменять уже принятые сервером операции нельзя. Для исправления используйте отмену и новую операцию, чтобы не терять историю.

## Формат сущностей

Категория содержит `id`, `name`, `currency` (`RUB`), `target_amount_rub` (`null` или целое число), `sort_order`, `is_archived`, `is_visible_on_home`, `created_at`, `updated_at`, `revision`. Метка содержит те же служебные поля без валюты, цели и видимости. Операция содержит поля из [модели БД](database.md), включая nullable `label_id`, `batch_id` и `comment`.

`POST /categories` принимает `name`, `target_amount_rub`, `sort_order` и необязательный `is_visible_on_home`. `PATCH /categories/{id}` принимает любое непустое подмножество этих полей, а также `is_archived`.

`POST /labels` принимает `name` и `sort_order`; `PATCH /labels/{id}` — любое непустое подмножество `name`, `sort_order`, `is_archived`.

`POST /transactions` принимает:

```json
{
  "category_id": "...",
  "label_id": "...",
  "amount_rub": -500,
  "comment": "...",
  "occurred_at": "2026-08-05T10:00:00Z",
  "client_operation_id": "..."
}
```

`amount_rub` не может быть нулём; категория, а при наличии и метка, должны существовать и не быть архивными. Успешный новый запрос возвращает `201`, повтор с тем же `client_operation_id` — `200` с полем `idempotent: true` и ранее созданной операцией.

`POST /transaction-batches` принимает общие `occurred_at`, nullable `label_id` и `comment`, а также массив `transactions`. Каждая строка должна иметь свои `category_id`, ненулевой `amount_rub` и уникальный `client_operation_id`; сервер присваивает общий `batch_id`. Повтор полного набора тех же client ID возвращает ранее созданную группу. Смешение уже существующих и новых client ID даёт `409`.

`GET /transactions` поддерживает `after`, `before` (ISO 8601), `category_id`, `label_id`, `include_cancelled=true`, `limit` от 1 до 200 и курсор из поля `next_cursor`. Результат сортируется от новых к старым.

`POST /transactions/{id}/cancel` выполняет мягкую отмену и безопасен при повторе. `GET /summary` возвращает `total_balance_rub`, активные категории с `balance_rub` и `revision`. `GET /changes?after=N` возвращает изменённые после N категории, метки и операции, включая отменённые, и текущий `revision`.

Ошибки должны иметь форму `{"error":{"code":"…","message":"…","details":{}}}`. ТЗ задаёт коды: `200`, `201`, `400`, `401`, `404`, `409`, `500`. Внутренние сведения не передаются клиенту.
