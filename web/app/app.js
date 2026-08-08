(() => {
  'use strict';

  const pageSize = 100;
  const cushionName = 'Подушка';
  const debtLabels = new Set(['Долг', 'Погашен долг']);
  const numberFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 });
  const dateFormat = new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC',
  });
  const status = document.querySelector('#status');
  const error = document.querySelector('#error');
  const errorMessage = document.querySelector('#error-message');
  const empty = document.querySelector('#empty');
  const tableWrap = document.querySelector('#table-wrap');
  const transactionsTable = document.querySelector('#transactions');
  const retry = document.querySelector('#retry');
  let loading = false;

  function amountOf(transaction) {
    return transaction.income_rub ?? -(transaction.expense_rub ?? 0);
  }

  function formatAmount(value, zeroAsDash = true) {
    if (!value && zeroAsDash) return '—';
    return `${value < 0 ? '−' : ''}${numberFormat.format(Math.abs(value))} ₽`;
  }

  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date);
  }

  function requestError(response) {
    return new Error(`request failed: ${response.status}`);
  }

  async function fetchJSON(path) {
    const response = await fetch(path, { credentials: 'same-origin' });
    if (!response.ok) throw requestError(response);
    return response.json();
  }

  async function patchLabel(transactionID, labelID) {
    const response = await fetch(`/app-api/api/v1/transactions/${encodeURIComponent(transactionID)}`, {
      method: 'PATCH',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ label_id: labelID }),
    });
    if (!response.ok) throw requestError(response);
  }

  async function loadAllTransactions() {
    const items = [];
    let cursor = null;
    do {
      const query = new URLSearchParams({ limit: String(pageSize) });
      if (cursor) query.set('cursor', cursor);
      const page = await fetchJSON(`/app-api/api/v1/web-history?${query}`);
      if (!Array.isArray(page.transactions)) throw new Error('invalid response');
      items.push(...page.transactions);
      cursor = typeof page.next_cursor === 'string' ? page.next_cursor : null;
    } while (cursor);
    return items;
  }

  function cell(row, text, className) {
    const element = document.createElement('td');
    element.textContent = text;
    if (className) element.className = className;
    row.append(element);
  }

  function labelCell(row, transaction, labels) {
    const element = document.createElement('td');
    element.className = 'label';
    const select = document.createElement('select');
    select.setAttribute('aria-label', `Метка операции от ${formatDate(transaction.occurred_at)}`);
    select.append(new Option('—', ''));
    labels.forEach((label) => select.append(new Option(label.name, label.id)));
    if (transaction.label_id && !labels.some((label) => label.id === transaction.label_id)) {
      select.append(new Option(transaction.label_name || 'Архивная метка', transaction.label_id));
    }
    select.value = transaction.label_id || '';
    select.addEventListener('change', async () => {
      const previousValue = transaction.label_id || '';
      select.disabled = true;
      try {
        await patchLabel(transaction.id, select.value || null);
        await load();
      } catch (reason) {
        select.value = previousValue;
        errorMessage.textContent = reason instanceof Error && /^request failed: \d+$/.test(reason.message)
          ? `Не удалось изменить метку (${reason.message.replace('request failed: ', 'HTTP ')}).`
          : 'Не удалось изменить метку. Повторите попытку.';
        error.hidden = false;
      } finally {
        select.disabled = false;
      }
    });
    element.append(select);
    row.append(element);
  }

  function headerCell(row, text, className) {
    const element = document.createElement('th');
    element.scope = 'col';
    element.textContent = text;
    if (className) element.className = className;
    row.append(element);
    return element;
  }

  function orderedCategories(categories, transactions) {
    const names = new Set(transactions.map((transaction) => transaction.category_name));
    const active = categories
      .filter((category) => !category.is_archived)
      .sort((left, right) => left.sort_order - right.sort_order || left.name.localeCompare(right.name, 'ru'))
      .map((category) => category.name);
    active.forEach((name) => names.delete(name));
    return [...active, ...[...names].sort((left, right) => left.localeCompare(right, 'ru'))];
  }

  function render(categories, labels, transactions) {
    const categoryNames = orderedCategories(categories, transactions);
    const totals = new Map(categoryNames.map((name) => [name, 0]));
    let debt = 0;
    transactions.forEach((transaction) => {
      const amount = amountOf(transaction);
      totals.set(transaction.category_name, (totals.get(transaction.category_name) ?? 0) + amount);
      if (transaction.category_name === cushionName && debtLabels.has(transaction.label_name)) debt += amount;
    });
    const totalFunds = [...totals.values()].reduce((sum, amount) => sum + amount, 0);

    const fragment = document.createDocumentFragment();
    const thead = document.createElement('thead');
    const debtRow = document.createElement('tr');
    debtRow.className = 'debt-row';
    const debtTitle = headerCell(debtRow, 'Долг');
    debtTitle.colSpan = 2;
    categoryNames.forEach((name) => {
      const cell = headerCell(debtRow, name === cushionName ? formatAmount(debt, false) : '—', 'debt-cell');
      if (name === cushionName) cell.title = 'Сумма операций «Долг» и «Погашен долг» в категории «Подушка»';
    });
    thead.append(debtRow);

    const totalsRow = document.createElement('tr');
    totalsRow.className = 'totals-row';
    const totalFundsCell = headerCell(totalsRow, `Всего: ${formatAmount(totalFunds, false)}`);
    totalFundsCell.colSpan = 2;
    categoryNames.forEach((name) => {
      headerCell(totalsRow, formatAmount(totals.get(name), false), 'total-cell');
    });
    thead.append(totalsRow);

    const namesRow = document.createElement('tr');
    namesRow.className = 'names-row';
    headerCell(namesRow, 'Дата', 'date-column');
    headerCell(namesRow, 'Метка', 'label-column');
    categoryNames.forEach((name) => headerCell(namesRow, name));
    thead.append(namesRow);
    fragment.append(thead);

    const tbody = document.createElement('tbody');
    transactions
      .slice()
      .sort((left, right) => left.occurred_at.localeCompare(right.occurred_at) || left.id.localeCompare(right.id))
      .forEach((transaction) => {
        const row = document.createElement('tr');
        cell(row, formatDate(transaction.occurred_at), 'date-column');
        labelCell(row, transaction, labels);
        categoryNames.forEach((name) => {
          const amount = name === transaction.category_name ? amountOf(transaction) : 0;
          cell(row, formatAmount(amount), amount ? `amount ${amount > 0 ? 'income' : 'expense'}` : 'amount empty-amount');
        });
        tbody.append(row);
      });
    fragment.append(tbody);
    transactionsTable.replaceChildren(fragment);
  }

  async function load() {
    if (loading) return;
    loading = true;
    error.hidden = true;
    empty.hidden = true;
    tableWrap.hidden = true;
    status.hidden = false;
    status.textContent = 'Загрузка движения средств…';
    retry.disabled = true;

    try {
      const [categoriesResponse, labelsResponse, transactions] = await Promise.all([
        fetchJSON('/app-api/api/v1/categories'),
        fetchJSON('/app-api/api/v1/labels'),
        loadAllTransactions(),
      ]);
      if (!Array.isArray(categoriesResponse.categories) || !Array.isArray(labelsResponse.labels)) throw new Error('invalid response');
      if (transactions.length === 0) {
        empty.hidden = false;
      } else {
        const activeLabels = labelsResponse.labels
          .filter((label) => !label.is_archived)
          .sort((left, right) => left.sort_order - right.sort_order || left.name.localeCompare(right.name, 'ru'));
        render(categoriesResponse.categories, activeLabels, transactions);
        tableWrap.hidden = false;
      }
      error.hidden = true;
    } catch (reason) {
      errorMessage.textContent = reason instanceof Error && /^request failed: \d+$/.test(reason.message)
        ? `Не удалось загрузить движение средств (${reason.message.replace('request failed: ', 'HTTP ')}).`
        : 'Не удалось загрузить движение средств. Проверьте подключение и повторите попытку.';
      error.hidden = false;
    } finally {
      status.hidden = true;
      retry.disabled = false;
      loading = false;
    }
  }

  retry.addEventListener('click', load);
  load();
})();
