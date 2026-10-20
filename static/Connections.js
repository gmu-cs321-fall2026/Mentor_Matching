/**
 * Presentation logic for the connections page.
 *
 * Same rules as app.js: no business rules live here. The service decides who may
 * make which lifecycle move and whether a mentor has room; this file offers the
 * buttons that role could plausibly use and shows whatever error comes back.
 * (Contracts v2: /mentors/recommendations and /connections.)
 *
 * app.js is wired to the profile forms and would fail on this page, so the small
 * helpers (call, pretty, el) are repeated here rather than imported.
 */

const el = (id) => document.getElementById(id);
const token = () => el('token').value.trim();
const role = () => el('role').value; // 'Student' or 'Mentor'

/** RESUME_REVIEW -> "Resume review" */
const pretty = (name) => name.charAt(0) + name.slice(1).toLowerCase().replaceAll('_', ' ');

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

/** Shared error format {error, code, detail} as one line of text. */
function failureText(result) {
  const payload = result.payload || {};
  let text = (payload.error || 'Request failed') + ' (' + result.status;
  if (payload.code) {
    text += ' ' + payload.code;
  }
  text += ')';
  const detail = payload.detail;
  if (detail && typeof detail === 'object') {
    text += ' — ' + Object.entries(detail).map(([f, m]) => f + ': ' + m).join('; ');
  } else if (typeof detail === 'string' && detail) {
    text += ' — ' + detail;
  }
  return text;
}

function setStatus(message, kind) {
  const status = el('status');
  status.textContent = message;
  status.className = kind || '';
}

function setMsg(node, message, kind) {
  node.textContent = message;
  node.className = 'msg' + (kind ? ' ' + kind : '');
}

function make(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) {
    node.textContent = text;
  }
  if (className) {
    node.className = className;
  }
  return node;
}

function button(label, onClick) {
  const b = make('button', label);
  b.type = 'button';
  b.addEventListener('click', onClick);
  return b;
}

/* ---------- recommendations (student) ---------- */

async function loadRecommendations() {
  setStatus('Loading recommendations…');
  const result = await call('GET', '/mentors/recommendations');
  const box = el('recs');
  box.innerHTML = '';

  if (!result.ok) {
    setStatus(failureText(result), 'bad');
    return;
  }
  setStatus('', '');

  const items = result.payload || [];
  if (items.length === 0) {
    box.appendChild(make('p', 'No mentors match your profile yet. Check back after more mentors sign up.', 'empty'));
    return;
  }
  items.forEach((mentor) => box.appendChild(recommendationCard(mentor)));
}

function recommendationCard(mentor) {
  const card = make('div', undefined, 'item');

  const who = (mentor.name || mentor.userId)
    + (mentor.title ? ' — ' + mentor.title : '')
    + (mentor.employer ? ', ' + mentor.employer : '');
  card.appendChild(make('h3', '#' + mentor.rank + '  ' + who));

  const score = Math.round((mentor.compatibilityScore || 0) * 100);
  const areas = (mentor.guidanceAreas || []).map(pretty).join(', ') || '—';
  card.appendChild(make('p',
    (mentor.industry ? pretty(mentor.industry) : '—') + ' · ' + areas + ' · ' + score + '% match',
    'meta'));

  const row = make('div', undefined, 'row');
  const capacity = make('span',
    mentor.activeMentees + ' / ' + mentor.maxMentees + (mentor.hasCapacity ? '' : ' full'),
    'badge' + (mentor.hasCapacity ? '' : ' full'));
  row.appendChild(capacity);

  const message = document.createElement('input');
  message.type = 'text';
  message.maxLength = 2000;
  message.placeholder = 'Optional note to the mentor';
  message.setAttribute('aria-label', 'Note to ' + (mentor.name || 'the mentor'));
  row.appendChild(message);

  const msg = make('p', '', 'msg');
  row.appendChild(button('Send request', async () => {
    setMsg(msg, 'Sending…');
    const body = { mentorId: mentor.userId };
    if (message.value.trim()) {
      body.message = message.value.trim();
    }
    const result = await call('POST', '/connections', body);
    if (result.ok) {
      setMsg(msg, 'Request sent. It is pending until the mentor responds.', 'ok');
      loadConnections();
    } else {
      setMsg(msg, failureText(result), 'bad');
    }
  }));

  card.appendChild(row);
  card.appendChild(msg);
  return card;
}

/* ---------- my connections ---------- */

async function loadConnections() {
  setStatus('Loading connections…');
  const result = await call('GET', '/connections');
  const box = el('connections');
  box.innerHTML = '';

  if (!result.ok) {
    setStatus(failureText(result), 'bad');
    return;
  }
  setStatus('', '');

  const items = Array.isArray(result.payload) ? result.payload : (result.payload.items || []);
  if (items.length === 0) {
    box.appendChild(make('p', role() === 'Student'
      ? 'No connections yet. Load recommendations and send a request to get started.'
      : 'No connection requests yet.', 'empty'));
    return;
  }
  items.forEach((connection) => box.appendChild(connectionCard(connection)));
}

/**
 * Which lifecycle moves to offer. This mirrors the contract table only so we do
 * not show buttons that are certain to be refused; the service still enforces it.
 */
function actionsFor(status) {
  const isMentor = role() === 'Mentor';
  if (status === 'PENDING') {
    return isMentor
      ? [['Accept', 'ACTIVE'], ['Decline', 'DECLINED']]
      : [['Withdraw request', 'WITHDRAWN']];
  }
  if (status === 'ACTIVE') {
    return [['Mark complete', 'COMPLETED'], ['End connection', 'WITHDRAWN']];
  }
  return [];
}

function connectionCard(connection) {
  const card = make('div', undefined, 'item');
  const isMentor = role() === 'Mentor';
  const other = isMentor
    ? (connection.studentName || connection.studentId)
    : (connection.mentorName || connection.mentorId);

  const head = make('h3', other + '  ');
  head.appendChild(make('span', pretty(connection.status), 'badge ' + connection.status));
  card.appendChild(head);

  const created = connection.createdAt ? new Date(connection.createdAt).toLocaleString() : '';
  card.appendChild(make('p', 'Requested ' + created, 'meta'));
  if (connection.message) {
    card.appendChild(make('p', 'Message: ' + connection.message));
  }

  const msg = make('p', '', 'msg');

  async function move(status) {
    setMsg(msg, 'Saving…');
    const result = await call('PUT', '/connections/' + connection.id, { status });
    if (result.ok) {
      loadConnections();
    } else {
      setMsg(msg, failureText(result), 'bad');
    }
  }

  const actions = actionsFor(connection.status);
  if (actions.length > 0) {
    const row = make('div', undefined, 'row');
    actions.forEach(([label, status]) => row.appendChild(button(label, () => move(status))));
    card.appendChild(row);
  }

  if (connection.status === 'ACTIVE') {
    const row = make('div', undefined, 'row');
    const notes = document.createElement('textarea');
    notes.rows = 3;
    notes.maxLength = 2000;
    notes.value = connection.notes || '';
    notes.setAttribute('aria-label', 'Shared notes');
    row.appendChild(notes);
    row.appendChild(button('Save notes', async () => {
      setMsg(msg, 'Saving notes…');
      const result = await call('PUT', '/connections/' + connection.id, { notes: notes.value });
      if (result.ok) {
        setMsg(msg, 'Notes saved. Both of you can see them.', 'ok');
      } else {
        setMsg(msg, failureText(result), 'bad');
      }
    }));
    card.appendChild(row);
  } else if (connection.notes) {
    card.appendChild(make('p', 'Notes: ' + connection.notes, 'meta'));
  }

  card.appendChild(msg);
  return card;
}

/* ---------- wiring ---------- */

/** Guess the role from the demo tokens so the common case needs no extra click. */
function syncRoleFromToken() {
  const t = token().toLowerCase();
  if (t.includes('mentor')) {
    el('role').value = 'Mentor';
  } else if (t.includes('student')) {
    el('role').value = 'Student';
  }
  el('recommended-section').hidden = role() !== 'Student';
}

el('token').addEventListener('change', () => {
  syncRoleFromToken();
  loadConnections();
});
el('role').addEventListener('change', () => {
  el('recommended-section').hidden = role() !== 'Student';
  loadConnections();
});
el('refresh').addEventListener('click', loadConnections);
el('load-recs').addEventListener('click', loadRecommendations);

syncRoleFromToken();
