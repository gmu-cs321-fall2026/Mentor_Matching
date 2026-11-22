/**
 * Presentation logic for the connections page.
 *
 * Same rules as app.js: no business rules live here. The service decides who may
 * make which lifecycle move and whether a mentor has room; this file offers the
 * buttons that role could plausibly use and shows whatever error comes back.
 * (Contracts v2: /mentors/recommendations and /connections.)
 *
 * Mentor Matching does not provide messaging. An active connection shows an
 * email button for the other person; conversations happen over email or
 * another tool they choose.
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
    card.appendChild(contactLine(connection, isMentor));
    card.appendChild(schedulePanel(connection, isMentor, other));

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
  if (connection.status !== 'ACTIVE' && connection.schedule && connection.schedule.status === 'CONFIRMED') {
    card.appendChild(make('p', 'Last arranged session: ' + when(connection.schedule.startsAt), 'meta'));
  }

  card.appendChild(msg);
  return card;
}

/* ---------- session scheduling ---------- */

/** "Tue, Nov 24, 6:00 PM" in the viewer's own time zone. */
function when(iso) {
  return new Date(iso).toLocaleString([], {
    weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}

/** Value for a datetime-local input, in local time: "2026-11-24T18:00". */
function localInputValue(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
    + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
}

/**
 * The "Next session" panel on an active connection. Four states:
 *   nothing scheduled          -> Propose a time
 *   I proposed, waiting        -> Propose a different time / Withdraw proposal
 *   they proposed              -> Confirm / Suggest another time / Decline
 *   confirmed                  -> Add to calendar / Reschedule / Cancel session
 * The service enforces every rule (only the other side can confirm, the time
 * must be in the future, a stale proposal is refused); this only picks buttons.
 */
function schedulePanel(connection, isMentor, other) {
  const panel = make('div', undefined, 'schedule');
  panel.appendChild(make('h4', 'Next session'));
  const msg = make('p', '', 'msg');
  const myId = isMentor ? connection.mentorId : connection.studentId;
  const proposal = connection.schedule;
  const url = '/connections/' + connection.id + '/schedule';

  async function answer(status, doneText) {
    setMsg(msg, 'Saving…');
    const result = await call('PUT', url, { proposalId: proposal.id, status });
    if (result.ok) {
      setMsg(msg, doneText, 'ok');
      loadConnections();
    } else {
      setMsg(msg, failureText(result), 'bad');
    }
  }

  const summary = make('p', '', 'schedule-summary');
  const actions = make('div', undefined, 'row');
  const form = proposeForm(url, msg, proposal ? 'Send new time' : 'Send proposal');
  form.hidden = true;
  const openForm = (label) => button(label, () => {
    form.hidden = false;
    form.querySelector('input[type=datetime-local]').focus();
  });

  if (!proposal) {
    summary.textContent = 'No session scheduled yet.';
    actions.appendChild(openForm('Propose a time'));
  } else if (proposal.status === 'PROPOSED' && proposal.proposedBy === myId) {
    summary.append('You proposed ', make('strong', when(proposal.startsAt)),
      '. Waiting for ' + other + ' to confirm.');
    actions.appendChild(openForm('Propose a different time'));
    actions.appendChild(button('Withdraw proposal', () => answer('CANCELLED', 'Proposal withdrawn.')));
  } else if (proposal.status === 'PROPOSED') {
    summary.append(other + ' proposed ', make('strong', when(proposal.startsAt)), '.');
    const confirm = button('Confirm', () => answer('CONFIRMED', 'Confirmed. Both of you can see it here.'));
    confirm.className = 'primary';
    actions.appendChild(confirm);
    actions.appendChild(openForm('Suggest another time'));
    actions.appendChild(button('Decline', () => answer('CANCELLED', 'Declined. Either of you can propose a new time.')));
  } else {
    summary.append('Confirmed for ', make('strong', when(proposal.startsAt)), '.');
    summary.classList.add('confirmed');
    actions.appendChild(button('Add to calendar', () => downloadInvite(connection, proposal, other)));
    actions.appendChild(openForm('Reschedule'));
    actions.appendChild(button('Cancel session', () => answer('CANCELLED', 'Session cancelled.')));
  }

  panel.appendChild(summary);
  if (proposal && proposal.note) {
    panel.appendChild(make('p', 'Where / how: ' + proposal.note, 'meta'));
  }
  panel.appendChild(actions);
  panel.appendChild(form);
  panel.appendChild(msg);
  return panel;
}

/** Date, time and an optional note; posts a new proposal (which replaces any old one). */
function proposeForm(url, msg, submitLabel) {
  const form = make('div', undefined, 'row propose');
  const time = document.createElement('input');
  time.type = 'datetime-local';
  time.min = localInputValue(new Date());
  time.setAttribute('aria-label', 'Session date and time');
  const note = document.createElement('input');
  note.type = 'text';
  note.maxLength = 500;
  note.placeholder = 'Where or how (Zoom link, Fenwick Library…)';
  note.setAttribute('aria-label', 'Where or how to meet');

  form.appendChild(time);
  form.appendChild(note);
  form.appendChild(button(submitLabel, async () => {
    if (!time.value) {
      setMsg(msg, 'Pick a date and time first.', 'bad');
      return;
    }
    setMsg(msg, 'Sending…');
    // datetime-local is the viewer's local time; send it as an exact instant.
    const body = { startsAt: new Date(time.value).toISOString() };
    if (note.value.trim()) {
      body.note = note.value.trim();
    }
    const result = await call('POST', url, body);
    if (result.ok) {
      setMsg(msg, 'Proposal sent. It needs the other person to confirm.', 'ok');
      loadConnections();
    } else {
      setMsg(msg, failureText(result), 'bad');
    }
  }));
  form.appendChild(button('Cancel', () => {
    form.hidden = true;
    setMsg(msg, '');
  }));
  return form;
}

/** A one-hour .ics file for a confirmed session, so it lands in any calendar app. */
function downloadInvite(connection, proposal, other) {
  const stamp = (d) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  const start = new Date(proposal.startsAt);
  const end = new Date(start.getTime() + 60 * 60 * 1000);
  const escape = (t) => t.replace(/[\\,;]/g, (c) => '\\' + c).replace(/\n/g, '\\n');
  const lines = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Mason CareerLaunch//Mentor Matching//EN',
    'BEGIN:VEVENT',
    'UID:' + proposal.id + '@mentor-matching',
    'DTSTAMP:' + stamp(new Date()),
    'DTSTART:' + stamp(start),
    'DTEND:' + stamp(end),
    'SUMMARY:' + escape('Mentoring session with ' + other),
  ];
  if (proposal.note) {
    lines.push('LOCATION:' + escape(proposal.note));
  }
  lines.push('END:VEVENT', 'END:VCALENDAR');
  const blob = new Blob([lines.join('\r\n') + '\r\n'], { type: 'text/calendar' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'mentoring-session.ics';
  link.click();
  URL.revokeObjectURL(link.href);
}

/**
 * Email button for the other person on an active connection. The service only
 * sends studentEmail and mentorEmail while the connection is ACTIVE.
 */
function contactLine(connection, isMentor) {
  const email = isMentor ? connection.studentEmail : connection.mentorEmail;
  const line = make('p', '', 'contact');
  if (email) {
    const link = make('a', 'Email ' + (isMentor ? 'your mentee' : 'your mentor'), 'email-button');
    link.href = 'mailto:' + encodeURIComponent(email).replace('%40', '@')
      + '?subject=' + encodeURIComponent('Mentor Matching');
    link.title = email;
    line.appendChild(link);
    line.appendChild(make('span', ' ' + email, 'meta'));
  }
  line.appendChild(make('span',
    ' Mentor Matching does not provide messaging; use email or a tool you both prefer.', 'meta'));
  return line;
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
