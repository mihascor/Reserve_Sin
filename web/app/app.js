(() => {
  'use strict';

  const pageSize = 50;
  const numberFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });
  const dateFormat = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' });
  const status = document.querySelector('#status');
  const error = document.querySelector('#error');
  const errorMessage = document.querySelector('#error-message');
  const empty = document.querySelector('#empty');
  const tableWrap = document.querySelector('#table-wrap');
  const transactions = document.querySelector('#transactions');
  const loadMore = document.querySelector('#load-more');
  const retry = document.querySelector('#retry');
  let nextCursor = null;
  let loading = false;

  function text(value) {
    return value || '—';
  }

  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date);
  }

  function formatAmount(value) {
    return value === null ? '—' : `${numberFormat.format(value)} ₽`;
  }

  function cell(row, value, className) {
    const element = document.createElement('td');
    element.textContent = value;
    if (className) element.className = className;
    row.append(element);
  }

  function appendTransactions(items) {
    const fragment = document.createDocumentFragment();
    items.forEach((item) => {
      const row = document.createElement('tr');
      cell(row, formatDate(item.occurred_at));
      cell(row, text(item.category_name));
      cell(row, text(item.label_name));
      cell(row, text(item.comment));
      cell(row, formatAmount(item.income_rub), 'amount income');
      cell(row, formatAmount(item.expense_rub), 'amount expense');
      cell(row, item.is_batch ? 'Да' : 'Нет', 'batch');
      fragment.append(row);
    });
    transactions.append(fragment);
  }

  async function load(reset) {
    if (loading) return;
    loading = true;
    error.hidden = true;
    if (reset) {
      nextCursor = null;
      transactions.replaceChildren();
      tableWrap.hidden = true;
      empty.hidden = true;
    }
    status.hidden = false;
    status.textContent = reset ? 'Загрузка истории…' : 'Загрузка операций…';
    loadMore.disabled = true;

    try {
      const query = new URLSearchParams({ limit: String(pageSize) });
      if (nextCursor) query.set('cursor', nextCursor);
      const response = await fetch(`/app-api/api/v1/web-history?${query}`, { credentials: 'same-origin' });
      if (!response.ok) throw new Error(`request failed: ${response.status}`);
      const data = await response.json();
      if (!Array.isArray(data.transactions)) throw new Error('invalid response');
      appendTransactions(data.transactions);
      nextCursor = typeof data.next_cursor === 'string' ? data.next_cursor : null;
      tableWrap.hidden = transactions.children.length === 0;
      empty.hidden = transactions.children.length !== 0;
      loadMore.hidden = !nextCursor;
      status.hidden = true;
    } catch (reason) {
      status.hidden = true;
      errorMessage.textContent = reason instanceof Error && /^request failed: \d+$/.test(reason.message)
        ? `Не удалось загрузить историю (${reason.message.replace('request failed: ', 'HTTP ')}).`
        : 'Не удалось загрузить историю. Проверьте подключение и повторите попытку.';
      error.hidden = false;
      loadMore.hidden = true;
    } finally {
      loading = false;
      loadMore.disabled = false;
    }
  }

  loadMore.addEventListener('click', () => load(false));
  retry.addEventListener('click', () => load(true));
  load(true);
})();
