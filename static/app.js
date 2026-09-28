/**
 * Presentation logic for both profile forms.
 *
 * Reads form values, calls our REST endpoints with the auth token, and renders
 * results and per-field errors. No business rules live here: the service is the
 * only thing that decides whether a profile is valid, and this file just shows
 * what it said.
 *
 * Option lists come from GET /enums rather than being hardcoded, so the
 * Industry and GuidanceArea values ADR-002 requires to match on both sides
 * cannot drift between the form and the domain.
 */

const MENTOR_PATH = '/mentors/profile';
const STUDENT_PATH = '/students/match-profile';

const page = document.body.dataset.page;
const path = page === 'mentor' ? MENTOR_PATH : STUDENT_PATH;

const el = (id) => document.getElementById(id);
const token = () => el('token').value.trim();

/** RESUME_REVIEW -> "Resume review" */
const pretty = (name) => name.charAt(0) + name.slice(1).toLowerCase().replaceAll('_', ' ');

/** "a, b ,," -> ["a", "b"] */
const splitList = (value) =>
  value.split(',').map((part) => part.trim()).filter((part) => part.length > 0);

async function call(method, url, body) {
  const headers = { Authorization: 'Bearer ' + token() };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (networkError) {
    return { ok: false, status: 0, payload: { error: 'Could not reach the service', detail: String(networkError) } };
  }
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch (parseError) {
      payload = { error: text };
    }
  }
  return { ok: response.ok, status: response.status, payload };
}

function clearErrors() {
  document.querySelectorAll('.error').forEach((node) => {
    node.textContent = '';
  });
}

function setStatus(message, kind) {
  const status = el('status');
  status.textContent = message;
  status.className = kind || '';
}

/**
 * Shows the shared error format: `error` and `code` as the headline, and a
 * `detail` object as one message per field.
 */
function showFailure(result) {
  const payload = result.payload || {};
  const detail = payload.detail;
  let fieldCount = 0;

  if (detail && typeof detail === 'object') {
    Object.entries(detail).forEach(([field, message]) => {
      const node = document.querySelector('.error[data-field="' + field + '"]');
      if (node) {
        node.textContent = message;
        fieldCount++;
      }
    });
  }

  let headline = (payload.error || 'Request failed') + ' (' + result.status;
  if (payload.code) {
    headline += ' ' + payload.code;
  }
  headline += ')';

  if (detail && typeof detail === 'object') {
    const unshown = Object.entries(detail).filter(
      ([field]) => !document.querySelector('.error[data-field="' + field + '"]'),
    );
    if (unshown.length > 0) {
      headline += ' — ' + unshown.map(([f, m]) => f + ': ' + m).join('; ');
    }
  } else if (typeof detail === 'string' && detail) {
    headline += ' — ' + detail;
  }

  setStatus(headline, 'bad');
  el('result').hidden = fieldCount > 0;
}

function showProfile(profile, message) {
  setStatus(message, 'ok');
  const result = el('result');
  result.textContent = JSON.stringify(profile, null, 2);
  result.hidden = false;
}

/* ---------- option lists ---------- */

function renderChecks(containerId, values) {
  const container = el(containerId);
  if (!container) {
    return;
  }
  container.innerHTML = '';
  values.forEach((value) => {
    const label = document.createElement('label');
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.value = value;
    input.dataset.group = containerId;
    label.appendChild(input);
    label.appendChild(document.createTextNode(pretty(value)));
    container.appendChild(label);
  });
}

function renderOptions(selectId, values) {
  const select = el(selectId);
  if (!select) {
    return;
  }
  values.forEach((value) => {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = pretty(value);
    select.appendChild(option);
  });
}

async function loadEnums() {
  const result = await call('GET', '/enums');
  if (!result.ok) {
    setStatus('Could not load the option lists from /enums', 'bad');
    return;
  }
  const { industries, guidanceAreas, contactMethods } = result.payload;

  if (page === 'mentor') {
    renderOptions('industry', industries);
    renderOptions('contactMethod', contactMethods);
    renderChecks('guidanceAreas', guidanceAreas);
    renderOptions('filter-industry', industries);
    renderOptions('filter-guidance', guidanceAreas);
  } else {
    renderChecks('targetIndustries', industries);
    renderChecks('guidanceWanted', guidanceAreas);
  }
}

/* ---------- form <-> JSON ---------- */

function checked(groupId) {
  return Array.from(document.querySelectorAll('input[data-group="' + groupId + '"]:checked'))
    .map((input) => input.value);
}

function setChecked(groupId, values) {
  const wanted = new Set(values || []);
  document.querySelectorAll('input[data-group="' + groupId + '"]').forEach((input) => {
    input.checked = wanted.has(input.value);
  });
}

function readForm() {
  if (page === 'mentor') {
    return {
      industry: el('industry').value || null,
      guidanceAreas: checked('guidanceAreas'),
      technicalDomains: splitList(el('technicalDomains').value),
      hoursPerMonth: Number(el('hoursPerMonth').value),
      contactMethod: el('contactMethod').value || null,
      maxMentees: Number(el('maxMentees').value),
      acceptingMentees: el('acceptingMentees').checked,
    };
  }
  return {
    targetIndustries: checked('targetIndustries'),
    targetRoles: splitList(el('targetRoles').value),
    guidanceWanted: checked('guidanceWanted'),
    targetCompanies: splitList(el('targetCompanies').value),
    preferSameProgram: el('preferSameProgram').checked,
  };
}

function fillForm(profile) {
  if (page === 'mentor') {
    el('industry').value = profile.industry || '';
    setChecked('guidanceAreas', profile.guidanceAreas);
    el('technicalDomains').value = (profile.technicalDomains || []).join(', ');
    el('hoursPerMonth').value = profile.hoursPerMonth || '';
    el('contactMethod').value = profile.contactMethod || '';
    el('maxMentees').value = profile.maxMentees ?? '';
    el('acceptingMentees').checked = Boolean(profile.acceptingMentees);
    return;
  }
  setChecked('targetIndustries', profile.targetIndustries);
  setChecked('guidanceWanted', profile.guidanceWanted);
  el('targetRoles').value = (profile.targetRoles || []).join(', ');
  el('targetCompanies').value = (profile.targetCompanies || []).join(', ');
  el('preferSameProgram').checked = Boolean(profile.preferSameProgram);
}

/* ---------- actions ---------- */

async function submit(method, successMessage) {
  clearErrors();
  setStatus('Saving…');
  const result = await call(method, path, readForm());
  if (result.ok) {
    fillForm(result.payload);
    showProfile(result.payload, successMessage);
  } else {
    showFailure(result);
  }
}

async function loadProfile() {
  clearErrors();
  setStatus('Loading…');
  const result = await call('GET', path);
  if (result.ok) {
    fillForm(result.payload);
    showProfile(result.payload, 'Loaded your saved profile.');
  } else if (result.status === 404) {
    setStatus('No profile saved yet — fill the form in and choose Create profile.', 'bad');
    el('result').hidden = true;
  } else {
    showFailure(result);
  }
}

/* ---------- mentor directory ---------- */

let directoryPage = 1;

async function browse(toPage) {
  directoryPage = Math.max(1, toPage);
  const params = new URLSearchParams();
  const industry = el('filter-industry').value;
  const guidance = el('filter-guidance').value;
  if (industry) {
    params.set('industry', industry);
  }
  if (guidance) {
    params.set('guidance', guidance);
  }
  if (el('filter-capacity').checked) {
    params.set('withCapacityOnly', 'true');
  }
  params.set('page', String(directoryPage));
  params.set('size', el('filter-size').value || '20');

  const status = el('directory-status');
  status.textContent = 'Loading the directory…';
  const result = await call('GET', '/mentors?' + params.toString());

  if (!result.ok) {
    const payload = result.payload || {};
    status.textContent = (payload.error || 'Could not load the directory')
      + ' (' + result.status + (payload.code ? ' ' + payload.code : '') + ')';
    el('directory').hidden = true;
    el('pager').hidden = true;
    return;
  }

  const { items, total, page: current, totalPages } = result.payload;
  const body = el('directory').querySelector('tbody');
  body.innerHTML = '';

  items.forEach((mentor) => {
    const row = document.createElement('tr');
    row.appendChild(cell((mentor.name || mentor.userId)
      + (mentor.title ? ' — ' + mentor.title : '')
      + (mentor.employer ? ', ' + mentor.employer : '')));
    row.appendChild(cell(mentor.industry ? pretty(mentor.industry) : '—'));
    row.appendChild(cell((mentor.guidanceAreas || []).map(pretty).join(', ') || '—'));

    const capacity = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = 'badge' + (mentor.hasCapacity ? '' : ' full');
    badge.textContent = mentor.activeMentees + ' / ' + mentor.maxMentees
      + (mentor.hasCapacity ? '' : ' full');
    capacity.appendChild(badge);
    row.appendChild(capacity);

    body.appendChild(row);
  });

  status.textContent = total === 0
    ? 'No mentors match those filters yet.'
    : 'Showing ' + items.length + ' of ' + total + ' mentors.';
  el('directory').hidden = total === 0;
  el('pager').hidden = total === 0;
  el('page-label').textContent = 'Page ' + current + ' of ' + totalPages;
  el('prev').disabled = current <= 1;
  el('next').disabled = current >= totalPages;
}

function cell(text) {
  const td = document.createElement('td');
  td.textContent = text;
  return td;
}

/* ---------- wiring ---------- */

el('load').addEventListener('click', loadProfile);
el('create').addEventListener('click', () => submit('POST', 'Profile created.'));
el('update').addEventListener('click', () => submit('PUT', 'Changes saved.'));

if (page === 'mentor') {
  el('browse').addEventListener('click', () => browse(1));
  el('prev').addEventListener('click', () => browse(directoryPage - 1));
  el('next').addEventListener('click', () => browse(directoryPage + 1));
}

loadEnums();
