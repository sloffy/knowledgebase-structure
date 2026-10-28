const $ = (selector, scope = document) => scope.querySelector(selector);
const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];

const labels = {
  SPORT: 'Вид спорта', EQUIPMENT: 'Снаряжение', ENVIRONMENT: 'Окружающая среда',
  GETTING_STARTED: 'Начало занятий', SAFETY: 'Безопасность', CONDITIONS: 'Условия', GENERAL: 'Общее',
  CANDIDATE: 'На проверке', VERIFIED: 'Проверено', REJECTED: 'Отклонено', DEMO: 'Демонстрация',
  USES: 'Использует', DEPENDS_ON: 'Зависит от', PRACTICED_ON: 'Практикуется на',
  BEGINNER: 'Начинающий', EXPERIENCED: 'Опытный', CALM_WATER: 'Спокойная вода', WAVES: 'Волны',
  LIGHT_WIND: 'Слабый ветер', STRONG_WIND: 'Сильный ветер',
};

const icons = {
  search: '<circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/>',
  book: '<path d="M12 5.5c-3.5-2-7-1.5-9-1v15c2-.5 5.5-1 9 1 3.5-2 7-1.5 9-1v-15c-2-.5-5.5-1-9 1Z"/><path d="M12 5.5v15"/>',
  nodes: '<circle cx="12" cy="5" r="3"/><circle cx="5" cy="19" r="3"/><circle cx="19" cy="19" r="3"/><path d="m10.5 8-4 8m7-8 4 8M8 19h8"/>',
  file: '<path d="M14 3H5v18h14V8l-5-5Z"/><path d="M14 3v5h5M8 12h8M8 16h6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="m8 12 3 3 5-6"/><circle cx="12" cy="12" r="9"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  arrow: '<path d="M4 12h15m-5-5 5 5-5 5"/>',
  sliders: '<path d="M4 7h6m4 0h6M4 17h10m4 0h2"/><circle cx="12" cy="7" r="2"/><circle cx="16" cy="17" r="2"/>',
  compass: '<circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5L16 8Z"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  edit: '<path d="m15 4 5 5M4 20l4-1 12-12a2 2 0 0 0-4-4L4 15v5Z"/>',
};

const views = {
  search: ['ПОИСК', 'Знания для уверенного старта', 'Найдите ответ, проверьте условия и посмотрите, на чём он основан.'],
  knowledge: ['УТВЕРЖДЕНИЯ', 'Знания, которым можно доверять', 'Добавляйте утверждения, указывайте источники и фиксируйте результаты проверки.'],
  topics: ['ТЕМЫ И СВЯЗИ', 'Карта вашей предметной области', 'Виды спорта, снаряжение и окружающая среда связаны между собой.'],
  sources: ['ИСТОЧНИКИ', 'У каждого знания есть основание', 'Собирайте материалы и сохраняйте сведения об их происхождении.'],
};

const state = {
  catalog: { topics: [], sources: [], categories: [], statuses: [], conditionKinds: [], relationTypes: [] },
  view: 'search', knowledgePage: 0, knowledgeSize: 15, knowledgeRequest: 0,
  searchPage: 0, searchSize: 10, searchRequest: 0, lastSearch: null, toastTimer: null, editorRequest: 0,
  catalogRequest: 0, dashboardRequest: 0, relationRequest: 0,
};

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[character]);
}

function icon(name) {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.file}</svg>`;
}

function refreshIcons(scope = document) {
  $$('[data-icon]', scope).forEach(element => { element.innerHTML = icon(element.dataset.icon); });
}

function label(value) { return labels[value] || value || 'Не указано'; }
function conditionInfo(kind) { return state.catalog.conditionKinds.find(condition => condition.kind === kind); }
function conditionLabel(kind) { return conditionInfo(kind)?.label || label(kind); }

function date(value, withTime = false) {
  if (!value) return 'не указана';
  const parsed = new Date(value.length === 10 ? `${value}T12:00:00Z` : value);
  if (Number.isNaN(parsed.getTime())) return escapeHtml(value);

  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Europe/Moscow',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  }).format(parsed);
}

function statusPill(status) {
  return `<span class="pill ${escapeHtml(String(status || '').toLowerCase())}">${escapeHtml(label(status))}</span>`;
}

function options(items, selected = '', emptyLabel = null) {
  return `${emptyLabel !== null ? `<option value="">${escapeHtml(emptyLabel)}</option>` : ''}${items.map(item => {
    const value = typeof item === 'string' ? item : item.id ?? item.kind;
    const text = typeof item === 'string' ? label(item) : item.name ?? item.label ?? label(value);
    return `<option value="${escapeHtml(value)}"${String(value) === String(selected) ? ' selected' : ''}>${escapeHtml(text)}</option>`;
  }).join('')}`;
}

function setOptions(selector, items, emptyLabel = null) {
  const element = $(selector);
  const selected = element.value;
  element.innerHTML = options(items, selected, emptyLabel);
}

function safeLink(location, title = location) {
  try {
    const url = new URL(location);
    if (['http:', 'https:'].includes(url.protocol)) {
      return `<a href="${escapeHtml(url.href)}" target="_blank" rel="noopener noreferrer">${escapeHtml(title)}</a>`;
    }
  } catch { /* A document location can also be a page number or local identifier. */ }
  return escapeHtml(title);
}

async function api(path, config = {}) {
  const response = await fetch(path, {
    ...config,
    headers: { Accept: 'application/json', ...(config.body ? { 'Content-Type': 'application/json' } : {}), ...config.headers },
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const errors = Object.values(data?.fieldErrors || {}).filter(Boolean);
    throw new Error([data?.message || `Запрос не выполнен (${response.status})`, ...errors].join('\n'));
  }
  return data;
}

function toast(message, error = false) {
  const element = $('#toast');
  clearTimeout(state.toastTimer);
  element.textContent = message;
  element.classList.toggle('error', error);
  element.hidden = false;
  state.toastTimer = setTimeout(() => { element.hidden = true; }, 5000);
}

function showError(element, error) {
  element.textContent = error.message || 'Не удалось выполнить действие. Попробуйте ещё раз.';
  element.hidden = false;
}

function emptyState(title, text, symbol = 'book') {
  return `<div class="empty-state"><div class="empty-symbol">${icon(symbol)}</div><h3>${escapeHtml(title)}</h3><p>${escapeHtml(text)}</p></div>`;
}

function loadingState() {
  return '<div class="empty-state"><p role="status">Загружаем данные…</p></div>';
}

async function loadCatalog() {
  const requestId = ++state.catalogRequest;
  const catalog = await api('/api/catalog');
  if (requestId !== state.catalogRequest) return;
  state.catalog = catalog;
  for (const key of ['topics', 'sources', 'categories', 'statuses', 'conditionKinds', 'relationTypes']) {
    state.catalog[key] ||= [];
  }

  setOptions('#search-topic', state.catalog.topics, 'Все темы');
  setOptions('#search-category', state.catalog.categories, 'Все категории');
  setOptions('#knowledge-topic', state.catalog.topics, 'Все темы');
  setOptions('#knowledge-status', state.catalog.statuses, 'Все статусы');
  setOptions('#relation-from', state.catalog.topics, 'Выберите тему');
  setOptions('#relation-to', state.catalog.topics, 'Выберите тему');
  setOptions('#relation-type', state.catalog.relationTypes, 'Выберите связь');

  renderSearchFilters();

  renderTopics();
  renderSources();
}

async function loadDashboard() {
  const requestId = ++state.dashboardRequest;
  const counts = await api('/api/dashboard');
  if (requestId !== state.dashboardRequest) return;
  for (const key of ['statements', 'verified', 'candidates']) {
    $(`#stat-${key}`).textContent = counts[key] ?? 0;
  }
  $('#nav-statements').textContent = counts.statements ?? 0;
}

async function refreshData() {
  const results = await Promise.allSettled([loadCatalog(), loadDashboard()]);
  const failed = results.find(result => result.status === 'rejected');
  if (failed) throw failed.reason;

  if (state.view === 'knowledge') await loadKnowledge();
  if (state.view === 'topics') await loadRelations();
}

function switchView(view) {
  if (!views[view]) return;
  state.view = view;
  const [breadcrumb, title, description] = views[view];
  $('#view-breadcrumb').textContent = breadcrumb;
  $('#view-title').textContent = title;
  $('#view-description').textContent = description;

  $$('.view-section').forEach(section => { section.hidden = section.id !== `view-${view}`; });
  $$('.nav-button').forEach(button => {
    const active = button.dataset.view === view;
    button.classList.toggle('active', active);
    if (active) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });

  if (view === 'knowledge') loadKnowledge();
  if (view === 'topics') loadRelations();
  window.scrollTo({ top: 0, behavior: 'instant' });
}

function renderSearchFilters() {
  const selected = new Map($$('[data-condition-dimension]').map(select => [select.dataset.conditionDimension, select.value]));
  const numbers = new Map($$('[data-numeric-kind]').map(input => [input.dataset.numericKind, input.value]));
  const dimensionLabels = {
    experience: 'Опыт', water: 'Состояние воды', wind: 'Ветер',
    supervision: 'Сопровождение', salinity: 'Тип воды', location: 'Акватория',
  };
  const dimensions = new Map();
  state.catalog.conditionKinds.filter(item => !item.numeric).forEach(item => {
    if (!dimensions.has(item.dimension)) dimensions.set(item.dimension, []);
    dimensions.get(item.dimension).push(item);
  });

  $('#search-categorical-filters').innerHTML = [...dimensions].map(([dimension, kinds]) => `<label>${escapeHtml(dimensionLabels[dimension] || dimension)}<select data-condition-dimension="${escapeHtml(dimension)}">${options(kinds, selected.get(dimension), 'Не указано')}</select></label>`).join('');
  $('#search-numeric-filters').innerHTML = state.catalog.conditionKinds.filter(item => item.numeric).map(kind => `<label>${escapeHtml(kind.label)}${kind.unit ? ` (${escapeHtml(kind.unit)})` : ''}<input data-numeric-kind="${escapeHtml(kind.kind)}" type="number" step="any"${['WIND_SPEED', 'WAVE_HEIGHT'].includes(kind.kind) ? ' min="0"' : ''} value="${escapeHtml(numbers.get(kind.kind) || '')}" placeholder="Не указано"></label>`).join('');
}

function buildSearchRequest() {
  const numericConditions = Object.fromEntries($$('[data-numeric-kind]').filter(input => input.value !== '').map(input => [input.dataset.numericKind, Number(input.value)]));
  const conditions = $$('[data-condition-dimension]').map(select => select.value).filter(Boolean);
  const category = $('#search-category').value;

  return {
    question: $('#search-question').value.trim(),
    topicId: $('#search-topic').value || null,
    categories: category ? [category] : [],
    conditions,
    numericConditions,
    includeDemo: $('#search-demo').checked,
    page: 0, size: state.searchSize,
  };
}

async function runSearch(page = 0, newRequest = false) {
  if (newRequest) state.lastSearch = buildSearchRequest();
  if (!state.lastSearch) return;

  const requestId = ++state.searchRequest;
  const body = { ...state.lastSearch, page };
  state.searchPage = page;
  $('#search-loading').hidden = false;
  $('#search-notice').hidden = true;
  $('#search-pagination').hidden = true;
  $('#search-results').innerHTML = loadingState();
  $('#search-results').setAttribute('aria-busy', 'true');
  $('#search-total').textContent = '';

  try {
    const data = await api('/api/search', { method: 'POST', body: JSON.stringify(body) });
    if (requestId !== state.searchRequest) return;
    renderSearch(data);
  } catch (error) {
    if (requestId !== state.searchRequest) return;
    $('#search-results').innerHTML = emptyState('Поиск не выполнен', 'Проверьте соединение и повторите запрос.', 'search');
    $('#search-notice').className = 'notice error';
    showError($('#search-notice'), error);
  } finally {
    if (requestId === state.searchRequest) {
      $('#search-loading').hidden = true;
      $('#search-results').setAttribute('aria-busy', 'false');
    }
  }
}

function renderSearch(data) {
  const statements = data.statements || [];
  $('#search-total').textContent = data.total ? `· ${data.total}` : '';
  const recognized = (data.recognizedConditions || []).map(item => label(item)).join(', ');
  const topics = (data.topics || []).map(item => item.name).join(', ');
  $('#search-context').textContent = [topics && `Темы: ${topics}`, recognized && `Условия: ${recognized}`].filter(Boolean).join(' · ') || 'Ответы с условиями применимости и подтверждающими источниками.';

  const messages = [data.message, ...(data.suggestions || [])].filter(Boolean);
  $('#search-notice').className = 'notice';
  $('#search-notice').textContent = messages.join(' ');
  $('#search-notice').hidden = !messages.length;
  $('#search-results').innerHTML = statements.length ? statements.map(renderSearchCard).join('') : emptyState('Подходящих знаний пока нет', 'Попробуйте другой запрос, измените условия или добавьте утверждение в базу.', 'search');
  renderPagination('#search-pagination', data.total || 0, data.page ?? state.searchPage, data.size || state.searchSize, page => runSearch(page));
}

function conditionText(condition) {
  if (typeof condition === 'string') return label(condition);
  const name = conditionLabel(condition.kind);
  const unit = condition.unit || conditionInfo(condition.kind)?.unit || '';
  const minimum = condition.minValue;
  const maximum = condition.maxValue;
  let range = '';
  if (minimum != null && maximum != null) range = `${minimum}–${maximum}`;
  else if (minimum != null) range = `от ${minimum}`;
  else if (maximum != null) range = `до ${maximum}`;

  return [name, range && `${range}${unit ? ` ${unit}` : ''}`, condition.text && condition.text !== name ? condition.text : ''].filter(Boolean).join(' · ');
}

function renderEvidence(sources) {
  if (!sources.length) return '<p class="applicability">Подтверждающий источник ещё не добавлен.</p>';
  return `
    <details class="evidence-section">
      <summary>Источники и подтверждения · ${sources.length}</summary>
      ${sources.map(source => `
        <div class="source-evidence">
          <blockquote>${escapeHtml(source.fragment)}</blockquote>
          <p>${safeLink(source.location, source.title || 'Источник')}${source.locator ? ` · ${escapeHtml(source.locator)}` : ''}${source.demonstration ? ' · демонстрационный материал' : ''}</p>
        </div>
      `).join('')}
    </details>
  `;
}

function renderSearchCard(statement) {
  const conditions = statement.structuredConditions?.length ? statement.structuredConditions : statement.conditions || [];
  const applicabilityLabels = {
    MATCH: 'Условия соответствуют запросу', MATCHED: 'Условия соответствуют запросу',
    UNKNOWN: 'Условия применимости в запросе указаны не полностью', UNSPECIFIED: 'Условия применимости не указаны',
    PARTIAL: 'Некоторые условия требуют уточнения', GENERAL: 'Общее утверждение без ограничений по условиям',
  };
  const applicability = applicabilityLabels[statement.applicability] || statement.applicability;
  const review = statement.review;

  return `
    <article class="result-card">
      <div class="card-topline">
        ${statusPill(statement.status)}
        ${statement.topic ? `<span class="pill topic">${escapeHtml(statement.topic.name)}</span>` : ''}
        <span class="pill category">${escapeHtml(label(statement.category))}</span>
      </div>
      <h3>${escapeHtml(statement.text)}</h3>
      ${conditions.length ? `<div class="conditions-bar">${conditions.map(condition => `<span class="condition-chip">${escapeHtml(conditionText(condition))}</span>`).join('')}</div>` : ''}
      ${applicability ? `<p class="applicability ${['UNKNOWN', 'UNSPECIFIED', 'PARTIAL'].includes(statement.applicability) ? 'uncertain' : ''}">${escapeHtml(applicability)}</p>` : ''}
      ${statement.matchReason ? `<p class="match-reason">${escapeHtml(statement.matchReason)}</p>` : ''}
      ${renderEvidence(statement.sources || [])}
      ${review?.reviewer ? `
        <div class="review-info">
          ${icon('check')}
          <span>Проверка: ${escapeHtml(review.reviewer)} · ${date(review.reviewedAt, true)}</span>
          ${review.note ? `<span>· ${escapeHtml(review.note)}</span>` : ''}
        </div>
      ` : ''}
    </article>
  `;
}

function renderPagination(selector, total, page, size, onPage) {
  const element = $(selector);
  const pages = Math.ceil(total / size);
  element.hidden = pages <= 1;
  if (pages <= 1) return;

  element.innerHTML = `<span>Страница ${page + 1} из ${pages} · всего ${total}</span><button class="button secondary small" type="button" data-page="${page - 1}"${page === 0 ? ' disabled' : ''} aria-label="Предыдущая страница">←</button><button class="button secondary small" type="button" data-page="${page + 1}"${page + 1 >= pages ? ' disabled' : ''} aria-label="Следующая страница">→</button>`;
  $$('[data-page]', element).forEach(button => { button.addEventListener('click', () => onPage(Number(button.dataset.page))); });
}

async function loadKnowledge(page = state.knowledgePage) {
  state.knowledgePage = page;
  const requestId = ++state.knowledgeRequest;
  const params = new URLSearchParams({ page, size: state.knowledgeSize });
  const filters = { status: $('#knowledge-status').value, topicId: $('#knowledge-topic').value, q: $('#knowledge-q').value.trim() };
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  $('#knowledge-list').innerHTML = loadingState();
  $('#knowledge-list').setAttribute('aria-busy', 'true');
  $('#knowledge-pagination').hidden = true;

  try {
    const data = await api(`/api/statements?${params}`);
    if (requestId !== state.knowledgeRequest) return;
    const statements = data.items || [];
    $('#knowledge-list').innerHTML = statements.length
      ? statements.map(renderKnowledgeItem).join('')
      : emptyState('Утверждений пока нет', 'Добавьте знание или измените фильтры.');
    renderPagination('#knowledge-pagination', data.total || 0, data.page ?? page, data.size || state.knowledgeSize, loadKnowledge);
    $$('[data-edit-statement]', $('#knowledge-list')).forEach(button => { button.addEventListener('click', () => openStatement(button.dataset.editStatement)); });
    $$('[data-review-statement]', $('#knowledge-list')).forEach(button => { button.addEventListener('click', () => openReview(button.dataset.reviewStatement)); });
  } catch (error) {
    if (requestId !== state.knowledgeRequest) return;
    $('#knowledge-list').innerHTML = emptyState('Не удалось загрузить утверждения', error.message);
  } finally {
    if (requestId === state.knowledgeRequest) $('#knowledge-list').setAttribute('aria-busy', 'false');
  }
}

function renderKnowledgeItem(statement) {
  return `
    <article class="knowledge-item">
      <div class="card-topline">
        ${statusPill(statement.status)}
        <span class="pill topic">${escapeHtml(statement.topic?.name)}</span>
        <span class="pill category">${escapeHtml(label(statement.category))}</span>
      </div>
      <p>${escapeHtml(statement.text)}</p>
      <div class="item-footer">
        <span class="item-metadata">${(statement.conditions || []).length} усл. · ${(statement.evidence || []).length} подтв.${statement.reviewedBy ? ` · ${escapeHtml(statement.reviewedBy)} · ${date(statement.reviewedAt)}` : ''}</span>
        <div class="item-actions">
          <button class="button quiet small" type="button" data-edit-statement="${escapeHtml(statement.id)}">Изменить</button>
          ${statement.status === 'DEMO' ? '<span class="item-metadata">Учебная запись</span>' : `<button class="button secondary small" type="button" data-review-statement="${escapeHtml(statement.id)}">Проверить</button>`}
        </div>
      </div>
    </article>
  `;
}

function renderTopics() {
  const topics = state.catalog.topics;
  $('#topics-list').innerHTML = topics.length ? topics.map(topic => `<article class="catalog-card">
      <div class="catalog-card-heading">
      <div>
      <h3>${escapeHtml(topic.name)}</h3>
      <span class="pill category">${escapeHtml(label(topic.type))}</span>
      </div>
      <button class="icon-button" type="button" data-edit-topic="${escapeHtml(topic.id)}" aria-label="Изменить тему ${escapeHtml(topic.name)}">${icon('edit')}</button>
      </div>
      <div class="synonyms">${(topic.synonyms || []).map(synonym => `<span>${escapeHtml(synonym)}</span>`).join('') || '<span>Синонимы ещё не добавлены</span>'}</div>
      </article>`).join('') : emptyState('Создайте первую тему', 'Начните с вида спорта, снаряжения или окружающей среды.', 'nodes');
  $$('[data-edit-topic]').forEach(button => { button.addEventListener('click', () => openTopic(button.dataset.editTopic)); });
}

function renderSources() {
  const sources = state.catalog.sources;
  $('#sources-list').innerHTML = sources.length ? sources.map(source => `<article class="catalog-card">
      <div class="catalog-card-heading">
      <div>
      <h3>${escapeHtml(source.title)}</h3>
      <span class="pill ${source.demonstration ? 'demo' : 'topic'}">${source.demonstration ? 'Демонстрационный материал' : 'Источник знаний'}</span>
      </div>
      <button class="icon-button" type="button" data-edit-source="${escapeHtml(source.id)}" aria-label="Изменить источник ${escapeHtml(source.title)}">${icon('edit')}</button>
      </div>
      <p>${safeLink(source.location)}</p>${source.author ? `<p>Автор / организация: ${escapeHtml(source.author)}</p>` : ''}<p>Опубликован: ${date(source.publishedOn)}<br>Дата обращения: ${date(source.accessedOn)}</p>
      </article>`).join('') : emptyState('Добавьте первый источник', 'Сохраните документ или публикацию, чтобы подтверждать утверждения.', 'file');
  $$('[data-edit-source]').forEach(button => { button.addEventListener('click', () => openSource(button.dataset.editSource)); });
}

async function loadRelations() {
  const requestId = ++state.relationRequest;
  $('#relations-list').innerHTML = '<p class="form-hint">Загружаем связи…</p>';
  try {
    const relations = await api('/api/relations');
    if (requestId !== state.relationRequest) return;
    $('#relations-list').innerHTML = relations.length ? relations.map(relation => `<div class="relation-row"><strong>${escapeHtml(relation.fromTopic.name)}</strong><span>${escapeHtml(label(relation.type).toLowerCase())} →</span><strong>${escapeHtml(relation.toTopic.name)}</strong></div>`).join('') : '<p class="form-hint">Связей пока нет. Создайте первую с помощью формы выше.</p>';
  } catch (error) {
    if (requestId === state.relationRequest) $('#relations-list').innerHTML = `<p class="form-hint">${escapeHtml(error.message)}</p>`;
  }
}

function openEditor(title, content, eyebrow = 'БАЗА ЗНАНИЙ') {
  state.editorRequest++;
  $('#editor-title').textContent = title;
  $('#editor-eyebrow').textContent = eyebrow;
  $('#editor-content').innerHTML = content;
  refreshIcons($('#editor-dialog'));
  if (!$('#editor-dialog').open) $('#editor-dialog').showModal();
  $('#editor-content [autofocus]')?.focus();
  $$('[data-cancel]', $('#editor-content')).forEach(button => { button.addEventListener('click', closeEditor); });
}

function closeEditor() {
  state.editorRequest++;
  $('#editor-dialog').close();
}

function formFooter(action = 'Сохранить') {
  return `<div class="form-error" id="editor-error" role="alert" hidden></div><div class="form-actions"><button class="button secondary" type="button" data-cancel>Отмена</button><button class="button primary" type="submit">${escapeHtml(action)}</button></div>`;
}

async function saveForm(form, operation, successMessage) {
  const submit = $('button[type="submit"]', form);
  const errorElement = $('#editor-error', form);
  errorElement.hidden = true;
  submit.disabled = true;
  let saved = false;
  try {
    await operation();
    saved = true;
    if (document.contains(form)) closeEditor();
    toast(successMessage);
    await refreshData();
    if (state.lastSearch) await runSearch(state.searchPage);
  } catch (error) {
    if (document.contains(form) && $('#editor-dialog').open && !saved) showError(errorElement, error);
    else toast(`${saved ? 'Сохранено, но обновление данных не удалось' : 'Не удалось сохранить'}: ${error.message}`, true);
  } finally { submit.disabled = false; }
}

async function openStatement(id = null) {
  const requestId = ++state.editorRequest;
  try {
    if (!state.catalog.topics.length) {
      toast('Сначала добавьте тему для утверждения.');
      switchView('topics');
      openTopic();
      return;
    }

    const statement = id ? await api(`/api/statements/${encodeURIComponent(id)}`) : null;
    if (requestId !== state.editorRequest) return;
    const referencedIds = (statement?.referencedTopics || []).map(topic => String(topic.id));
    openEditor(id ? 'Изменить утверждение' : 'Добавить утверждение', `<form id="statement-form" class="editor-form">
      <div class="form-grid">
      <label>Основная тема<select id="statement-topic" required>${options(state.catalog.topics, statement?.topic?.id, 'Выберите тему')}</select>
      </label>
      <label>Категория<select id="statement-category" required>${options(state.catalog.categories, statement?.category || 'GENERAL')}</select>
      </label>
      <label class="full">Текст утверждения<textarea id="statement-text" rows="4" maxlength="4000" required autofocus placeholder="Одно конкретное знание, которое можно проверить">${escapeHtml(statement?.text || '')}</textarea>
      </label>
      <label class="full">Дополнительные темы<select id="statement-referenced" class="multi-select" multiple>${state.catalog.topics.map(topic => `<option value="${escapeHtml(topic.id)}"${referencedIds.includes(String(topic.id)) ? ' selected' : ''}>${escapeHtml(topic.name)}</option>`).join('')}</select>
      <span class="form-hint">Для нескольких тем удерживайте Ctrl или ⌘. Основную тему повторять не нужно.</span>
      </label>
      <label class="check-label full">
      <input id="statement-demo" type="checkbox"${id ? ' disabled' : ''}${statement?.status === 'DEMO' || statement?.demonstration ? ' checked' : ''}>Демонстрационное утверждение</label>
      </div>
      <p class="form-hint">${id ? 'Изменение проверенного знания отправляет его на повторную проверку.' : 'Новое знание попадёт в очередь на проверку.'}</p>
      <section class="form-section">
      <div class="form-section-heading">
      <h3>Условия применимости</h3>
      <button class="button secondary small" id="add-condition" type="button">+ Условие</button>
      </div>
      <p class="form-hint">Укажите опыт, состояние воды или числовой диапазон. Все условия должны выполняться одновременно.</p>
      <div id="statement-conditions">
      </div>
      </section>
      <section class="form-section">
      <div class="form-section-heading">
      <h3>Подтверждения</h3>
      <button class="button secondary small" id="add-evidence" type="button"${state.catalog.sources.length ? '' : ' disabled'}>+ Источник</button>
      </div>
      <p class="form-hint">${state.catalog.sources.length ? 'Добавьте точный фрагмент и место в источнике: страницу, раздел или ссылку.' : 'Сначала создайте источник в библиотеке. Утверждение можно сохранить без подтверждения.'}</p>
      <div id="statement-evidence">
      </div>
      </section>${formFooter(id ? 'Сохранить изменения' : 'Добавить в базу')}</form>`);

    (statement?.conditions || []).forEach(addConditionRow);
    (statement?.evidence || []).forEach(addEvidenceRow);
    $('#add-condition').addEventListener('click', () => addConditionRow());
    $('#add-evidence').addEventListener('click', () => addEvidenceRow());
    $('#statement-form').addEventListener('submit', event => {
      event.preventDefault();
      const body = {
        topicId: $('#statement-topic').value,
        text: $('#statement-text').value.trim(),
        category: $('#statement-category').value,
        referencedTopicIds: $$('#statement-referenced option:checked').map(option => option.value),
        conditions: $$('.condition-row').map(row => ({
          kind: $('.condition-kind', row).value,
          text: $('.condition-text', row).value.trim(),
          minValue: numericInput($('.condition-min', row)),
          maxValue: numericInput($('.condition-max', row)),
        })),
        evidence: $$('.evidence-row').map(row => ({
          sourceId: $('.evidence-source', row).value,
          fragment: $('.evidence-fragment', row).value.trim(),
          locator: $('.evidence-locator', row).value.trim(),
        })),
        demonstration: $('#statement-demo').checked,
        ...(id ? { version: statement.version } : {}),
      };
      saveForm(event.currentTarget, () => api(id ? `/api/statements/${encodeURIComponent(id)}` : '/api/statements', { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) }), id ? 'Утверждение обновлено' : 'Утверждение добавлено');
    });
  } catch (error) { toast(error.message, true); }
}

function numericInput(element) {
  return element && !element.disabled && element.value !== '' ? Number(element.value) : null;
}

function addConditionRow(condition = {}) {
  const row = document.createElement('div');
  row.className = 'form-row-card condition-row';
  row.innerHTML = `<div class="row-card-top">
      <span>Условие применимости</span>
      <button class="icon-button remove-row" type="button" aria-label="Удалить условие">${icon('close')}</button>
      </div>
      <div class="form-grid">
      <label class="full">Параметр<select class="condition-kind" required>${options(state.catalog.conditionKinds, condition.kind, 'Выберите условие')}</select>
      </label>
      <label class="full">Описание<input class="condition-text" maxlength="500" value="${escapeHtml(condition.text || '')}" placeholder="Например: для первого занятия">
      </label>
      <label class="numeric-condition-field">От<input class="condition-min" type="number" step="any" value="${escapeHtml(condition.minValue ?? '')}">
      </label>
      <label class="numeric-condition-field">До<input class="condition-max" type="number" step="any" value="${escapeHtml(condition.maxValue ?? '')}">
      </label>
      </div>
      <p class="form-hint condition-unit" hidden>
      </p>`;
  $('#statement-conditions').append(row);
  $('.remove-row', row).addEventListener('click', () => row.remove());

  const validateRange = () => {
    const kind = conditionInfo($('.condition-kind', row).value);
    const minimum = $('.condition-min', row);
    const maximum = $('.condition-max', row);
    minimum.setCustomValidity('');
    maximum.setCustomValidity('');
    if (!kind?.numeric) return;

    if (minimum.value === '' && maximum.value === '') {
      minimum.setCustomValidity('Укажите хотя бы одну границу диапазона.');
    } else if (minimum.value !== '' && maximum.value !== '' && Number(minimum.value) > Number(maximum.value)) {
      maximum.setCustomValidity('Верхняя граница должна быть не меньше нижней.');
    }
  };

  const update = () => {
    const kind = conditionInfo($('.condition-kind', row).value);
    $$('.numeric-condition-field', row).forEach(field => { field.hidden = !kind?.numeric; });
    $$('.numeric-condition-field input', row).forEach(input => { input.disabled = !kind?.numeric; });
    $$('.numeric-condition-field input', row).forEach(input => {
      if (['WIND_SPEED', 'WAVE_HEIGHT'].includes(kind?.kind)) input.min = '0';
      else input.removeAttribute('min');
    });
    const hint = $('.condition-unit', row);
    hint.hidden = !kind?.numeric;
    hint.textContent = `Единица: ${kind?.unit || 'без единицы'}. Можно указать одну или обе границы.`;
    $('.condition-text', row).required = true;
    $('.condition-text', row).placeholder = kind?.numeric ? 'Например: допустимая скорость ветра' : 'Кратко опишите условие';
    validateRange();
  };
  $('.condition-kind', row).addEventListener('change', update);
  $$('.numeric-condition-field input', row).forEach(input => { input.addEventListener('input', validateRange); });
  update();
}

function addEvidenceRow(evidence = {}) {
  const row = document.createElement('div');
  row.className = 'form-row-card evidence-row';
  row.innerHTML = `<div class="row-card-top">
      <span>Подтверждающий фрагмент</span>
      <button class="icon-button remove-row" type="button" aria-label="Удалить подтверждение">${icon('close')}</button>
      </div>
      <div class="form-grid">
      <label class="full">Источник<select class="evidence-source" required>${options(state.catalog.sources, evidence.sourceId, 'Выберите источник')}</select>
      </label>
      <label class="full">Фрагмент источника<textarea class="evidence-fragment" maxlength="4000" rows="3" required>${escapeHtml(evidence.fragment || '')}</textarea>
      </label>
      <label class="full">Место в источнике<input class="evidence-locator" maxlength="300" required value="${escapeHtml(evidence.locator || '')}" placeholder="Например: раздел «Снаряжение», стр. 12">
      </label>
      </div>`;
  $('#statement-evidence').append(row);
  $('.remove-row', row).addEventListener('click', () => row.remove());
}

async function openReview(id) {
  const requestId = ++state.editorRequest;
  try {
    const statement = await api(`/api/statements/${encodeURIComponent(id)}`);
    if (requestId !== state.editorRequest) return;
    if (statement.status === 'DEMO') {
      toast('Учебные записи не проверяются. Создайте отдельное утверждение с обычным источником.');
      return;
    }
    let reviewer = '';
    try { reviewer = localStorage.getItem('aquatoria-reviewer') || ''; } catch { /* Storage may be disabled by the browser. */ }
    openEditor('Проверить утверждение', `<form id="review-form" class="editor-form">
      <div class="review-statement">${escapeHtml(statement.text)}${renderEvidence(statement.evidence || [])}</div>
      <div class="form-grid">
      <label>Результат проверки<select id="review-status" required>${options(['VERIFIED', 'REJECTED', 'CANDIDATE'], statement.status === 'REJECTED' ? 'REJECTED' : 'VERIFIED')}</select>
      </label>
      <label>Проверяющий<input id="reviewer" maxlength="200" required autofocus value="${escapeHtml(reviewer)}" placeholder="Имя и фамилия">
      </label>
      <label class="full">Комментарий к проверке<textarea id="review-note" maxlength="2000" rows="3" required placeholder="На что опирается решение? Что требуется уточнить?">
      </textarea>
      </label>
      </div>
      <p class="form-hint">Для подтверждения нужен хотя бы один обычный источник. Демонстрационные материалы не подтверждают достоверность.</p>${statement.reviews?.length ? `<section class="form-section">
      <h3>История проверки</h3>
      <div class="history-list">${statement.reviews.map(review => `<div class="history-item">${statusPill(review.status)} · ${escapeHtml(review.reviewer)} · ${date(review.reviewedAt, true)}${review.note ? `<p>${escapeHtml(review.note)}</p>` : ''}</div>`).join('')}</div>
      </section>` : ''}${formFooter('Сохранить проверку')}</form>`, 'КОНТРОЛЬ КАЧЕСТВА');

    $('#review-form').addEventListener('submit', event => {
      event.preventDefault();
      const body = {
        status: $('#review-status').value,
        reviewer: $('#reviewer').value.trim(),
        note: $('#review-note').value.trim(),
        version: statement.version,
      };
      saveForm(event.currentTarget, async () => {
        await api(`/api/statements/${encodeURIComponent(id)}/review`, { method: 'POST', body: JSON.stringify(body) });
        try { localStorage.setItem('aquatoria-reviewer', body.reviewer); } catch { /* Saving the review does not depend on local storage. */ }
      }, 'Результат проверки сохранён');
    });
  } catch (error) { toast(error.message, true); }
}

function openTopic(id = null) {
  const topic = state.catalog.topics.find(item => String(item.id) === String(id));
  openEditor(id ? 'Изменить тему' : 'Добавить тему', `<form id="topic-form" class="editor-form">
      <div class="form-grid">
      <label>Название<input id="topic-name" maxlength="200" required autofocus value="${escapeHtml(topic?.name || '')}" placeholder="Например: Сапбординг">
      </label>
      <label>Тип темы<select id="topic-type" required>${options(state.catalog.topicTypes || ['SPORT', 'EQUIPMENT', 'ENVIRONMENT'], topic?.type || 'SPORT')}</select>
      </label>
      <label class="full">Синонимы<textarea id="topic-synonyms" rows="3" placeholder="Каждый синоним с новой строки">${escapeHtml((topic?.synonyms || []).join('\n'))}</textarea>
      <span class="form-hint">Добавьте разговорные названия, сокращения и варианты написания.</span>
      </label>
      </div>${formFooter()}</form>`, 'ТЕМЫ И СВЯЗИ');
  $('#topic-form').addEventListener('submit', event => {
    event.preventDefault();
    const body = {
      name: $('#topic-name').value.trim(),
      type: $('#topic-type').value,
      synonyms: [...new Set($('#topic-synonyms').value.split('\n').map(value => value.trim()).filter(Boolean))],
    };
    saveForm(event.currentTarget, () => api(id ? `/api/topics/${encodeURIComponent(id)}` : '/api/topics', { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) }), id ? 'Тема обновлена' : 'Тема добавлена');
  });
}

function openSource(id = null) {
  const source = state.catalog.sources.find(item => String(item.id) === String(id));
  openEditor(id ? 'Изменить источник' : 'Добавить источник', `<form id="source-form" class="editor-form">
      <div class="form-grid">
      <label class="full">Название<input id="source-title" maxlength="300" required autofocus value="${escapeHtml(source?.title || '')}" placeholder="Название документа или публикации">
      </label>
      <label class="full">Ссылка / расположение<input id="source-location" maxlength="1000" required value="${escapeHtml(source?.location || '')}" placeholder="https://… или описание расположения документа">
      </label>
      <label class="full">Автор / организация<input id="source-author" maxlength="300" value="${escapeHtml(source?.author || '')}" placeholder="Кто опубликовал материал">
      </label>
      <label>Дата публикации<input id="source-published" type="date" value="${escapeHtml(source?.publishedOn || '')}">
      </label>
      <label>Дата обращения<input id="source-accessed" type="date" value="${escapeHtml(source?.accessedOn || '')}">
      </label>
      <label class="check-label full">
      <input id="source-demo" type="checkbox"${id ? ' disabled' : ''}${source?.demonstration ? ' checked' : ''}>Демонстрационный источник</label>
      </div>
      <p class="form-hint">Демонстрационные источники подходят для примеров, но не для подтверждения реальных знаний.</p>${formFooter()}</form>`, 'БИБЛИОТЕКА ИСТОЧНИКОВ');
  $('#source-form').addEventListener('submit', event => {
    event.preventDefault();
    const body = {
      title: $('#source-title').value.trim(), location: $('#source-location').value.trim(),
      author: $('#source-author').value.trim() || null,
      publishedOn: $('#source-published').value || null,
      accessedOn: $('#source-accessed').value || null,
      demonstration: $('#source-demo').checked,
    };
    saveForm(event.currentTarget, () => api(id ? `/api/sources/${encodeURIComponent(id)}` : '/api/sources', { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) }), id ? 'Источник обновлён' : 'Источник добавлен');
  });
}

function bindEvents() {
  $$('.nav-button').forEach(button => { button.addEventListener('click', () => switchView(button.dataset.view)); });
  $('#global-add').addEventListener('click', () => openStatement());
  $('#knowledge-add').addEventListener('click', () => openStatement());
  $('#topic-add').addEventListener('click', () => openTopic());
  $('#source-add').addEventListener('click', () => openSource());
  $('#editor-close').addEventListener('click', closeEditor);
  $('#editor-dialog').addEventListener('cancel', () => { state.editorRequest++; });
  $('#editor-dialog').addEventListener('click', event => {
    if (event.target === $('#editor-dialog')) {
      const bounds = event.target.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeEditor();
    }
  });

  $('#open-candidates').addEventListener('click', () => {
    $('#knowledge-status').value = 'CANDIDATE';
    $('#knowledge-topic').value = '';
    $('#knowledge-q').value = '';
    state.knowledgePage = 0;
    switchView('knowledge');
  });

  $('#search-form').addEventListener('submit', event => {
    event.preventDefault();
    const request = buildSearchRequest();
    if (!request.question && !request.topicId) {
      toast('Введите вопрос или выберите тему.');
      $('#search-question').focus();
      return;
    }
    runSearch(0, true);
  });
  $$('[data-query]').forEach(button => {
    button.addEventListener('click', () => {
      $('#search-question').value = button.dataset.query;
      $('#search-form').requestSubmit();
    });
  });
  $('#search-form').addEventListener('change', () => {
    const body = buildSearchRequest();
    const count = body.conditions.length + body.categories.length + Object.keys(body.numericConditions).length + (body.topicId ? 1 : 0);
    $('#filter-summary').textContent = count ? `Задано фильтров: ${count}` : 'Уточните ваш контекст';
  });

  let filterTimer;
  $('#knowledge-q').addEventListener('input', () => {
    clearTimeout(filterTimer);
    filterTimer = setTimeout(() => loadKnowledge(0), 300);
  });
  ['#knowledge-status', '#knowledge-topic'].forEach(selector => {
    $(selector).addEventListener('change', () => loadKnowledge(0));
  });

  $('#relation-form').addEventListener('submit', async event => {
    event.preventDefault();
    $('#relation-error').hidden = true;
    const submit = $('button[type="submit"]', event.currentTarget);
    submit.disabled = true;
    try {
      await api('/api/relations', {
        method: 'POST', body: JSON.stringify({ fromTopicId: $('#relation-from').value, toTopicId: $('#relation-to').value, type: $('#relation-type').value }),
      });
      toast('Связь добавлена');
      await loadRelations();
      if (state.lastSearch) await runSearch(state.searchPage);
    } catch (error) { showError($('#relation-error'), error); }
    finally { submit.disabled = false; }
  });
}

refreshIcons();
bindEvents();
refreshData().catch(error => {
  toast(`Не удалось загрузить базу: ${error.message}`, true);
  $('#search-notice').className = 'notice error';
  $('#search-notice').textContent = 'Не удалось подключиться к базе знаний. Обновите страницу после запуска сервера.';
  $('#search-notice').hidden = false;
});
