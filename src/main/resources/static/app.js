// Access token sirf memory mein rehta hai (localStorage mein nahi: XSS se bachav).
// Refresh token HttpOnly cookie mein hai, JS use nahi padh sakta.
let accessToken = null;
let editingId = null;
let currentDraft = '';

const $ = (id) => document.getElementById(id);
const appForm = $('appForm');
const val = (form, name) => form.elements[name].value;

function log(label, status, data) {
  // textContent use karte hain innerHTML nahi (XSS se bachav)
  $('log').textContent = label + ' -> ' + status + '\n' +
    (data === null || data === undefined ? '' : JSON.stringify(data, null, 2));
}

function setLoggedIn(on) {
  $('authBadge').textContent = on ? 'Logged in' : 'Logged out';
  $('authBadge').classList.toggle('on', on);
}

// Refresh cookie se naya access token lo
async function refresh() {
  const res = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' });
  if (!res.ok) {
    accessToken = null;
    setLoggedIn(false);
    return false;
  }
  accessToken = (await res.json()).accessToken;
  setLoggedIn(true);
  return true;
}

const NO_RETRY = ['/api/auth/login', '/api/auth/register', '/api/auth/verify-otp', '/api/auth/refresh', '/api/auth/logout'];

// Ek hi jagah se saari API calls. 401 aaye to refresh karke ek baar retry.
async function api(method, path, body, retry = true) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers['Authorization'] = 'Bearer ' + accessToken;

  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  if (res.status === 401 && retry && !NO_RETRY.includes(path)) {
    if (await refresh()) return api(method, path, body, false);
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (_) { data = text; }
  }
  return { status: res.status, data };
}

// ---------- Account ----------

$('registerForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const r = await api('POST', '/api/auth/register', { name: val(f, 'name'), email: val(f, 'email'), password: val(f, 'password') }, false);
  log('Register', r.status, r.data);
});

$('verifyForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const r = await api('POST', '/api/auth/verify-otp', { email: val(f, 'email'), code: val(f, 'code') }, false);
  log('Verify OTP', r.status, r.data);
});

$('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const r = await api('POST', '/api/auth/login', { email: val(f, 'email'), password: val(f, 'password') }, false);
  if (r.status === 200) {
    accessToken = r.data.accessToken;
    setLoggedIn(true);
    log('Login', 200, { email: r.data.email, role: r.data.role, accessToken: '(hidden)' });
    await loadAll();
  } else {
    log('Login', r.status, r.data);
  }
});

$('logoutBtn').addEventListener('click', async () => {
  const r = await api('POST', '/api/auth/logout', undefined, false);
  accessToken = null;
  setLoggedIn(false);
  clearLists();
  log('Logout', r.status, r.data);
});

function clearLists() {
  $('tiles').replaceChildren();
  $('apps').replaceChildren();
  $('reminders').replaceChildren();
  $('dueCount').textContent = '0';
}

// ---------- Load data ----------

async function loadAll() {
  await Promise.all([loadDashboard(), loadApps(), loadReminders(), loadResume()]);
}

async function loadDashboard() {
  const r = await api('GET', '/api/dashboard');
  if (r.status !== 200) return;
  const tiles = $('tiles');
  tiles.replaceChildren();
  addTile(tiles, 'Total', r.data.total);
  for (const [status, n] of Object.entries(r.data.byStatus)) {
    addTile(tiles, status, n);
  }
  $('dueCount').textContent = r.data.dueReminders;
}

function addTile(parent, label, value) {
  const d = document.createElement('div');
  d.className = 'tile';
  const v = document.createElement('strong');
  v.textContent = value;
  const l = document.createElement('span');
  l.textContent = label;
  d.append(v, l);
  parent.append(d);
}

async function loadApps() {
  const status = $('filterStatus').value;
  const r = await api('GET', '/api/applications' + (status ? '?status=' + encodeURIComponent(status) : ''));
  if (r.status !== 200) {
    log('Applications', r.status, r.data);
    return;
  }
  renderApps($('apps'), r.data, true);
}

async function loadReminders() {
  const r = await api('GET', '/api/reminders');
  if (r.status === 200) renderApps($('reminders'), r.data, false);
}

// ---------- Render (createElement se, innerHTML se nahi) ----------

function renderApps(container, list, withActions) {
  container.replaceChildren();
  if (!list.length) {
    const p = document.createElement('p');
    p.className = 'muted';
    p.textContent = 'Kuch nahi mila';
    container.append(p);
    return;
  }

  for (const a of list) {
    const item = document.createElement('div');
    item.className = 'item';

    const top = document.createElement('div');
    top.className = 'item-top';
    const title = document.createElement('strong');
    title.textContent = a.jobTitle + ' @ ' + a.company;
    const badge = document.createElement('span');
    badge.className = 'badge ' + a.status.toLowerCase();
    badge.textContent = a.status;
    top.append(title, badge);

    const meta = document.createElement('small');
    meta.className = 'muted';
    meta.textContent = 'Applied: ' + a.appliedOn + (a.followUpOn ? '  |  Follow-up: ' + a.followUpOn : '');

    item.append(top, meta);

    // Sirf http/https links, javascript: links allowed nahi
    if (a.jobUrl && /^https?:\/\//.test(a.jobUrl)) {
      const link = document.createElement('a');
      link.href = a.jobUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = 'Job link kholo';
      item.append(link);
    }

    if (a.notes) {
      const notes = document.createElement('p');
      notes.textContent = a.notes;
      item.append(notes);
    }

    if (withActions) item.append(actionsFor(a));
    container.append(item);
  }
}

function actionsFor(a) {
  const row = document.createElement('div');
  row.className = 'row';
  row.append(
    button('Edit', () => startEdit(a)),
    button('AI email', () => aiEmail(a.id)),
    button('Delete', () => removeApp(a.id), 'alt')
  );
  return row;
}

function button(label, onClick, cls) {
  const b = document.createElement('button');
  b.type = 'button';
  b.textContent = label;
  if (cls) b.className = cls;
  b.addEventListener('click', onClick);
  return b;
}

// ---------- Application form (create / update) ----------

$('appForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const body = {
    company: val(f, 'company'),
    jobTitle: val(f, 'jobTitle'),
    status: val(f, 'status'),
    appliedOn: val(f, 'appliedOn') || null,
    followUpOn: val(f, 'followUpOn'),
    jobUrl: val(f, 'jobUrl'),
    notes: val(f, 'notes')
  };
  const r = editingId
    ? await api('PATCH', '/api/applications/' + editingId, body)
    : await api('POST', '/api/applications', body);
  log(editingId ? 'Update' : 'Create', r.status, r.data);
  if (r.status === 200 || r.status === 201) {
    resetForm();
    await loadAll();
  }
});

function startEdit(a) {
  editingId = a.id;
  appForm.elements.company.value = a.company;
  appForm.elements.jobTitle.value = a.jobTitle;
  appForm.elements.status.value = a.status;
  appForm.elements.appliedOn.value = a.appliedOn || '';
  appForm.elements.followUpOn.value = a.followUpOn || '';
  appForm.elements.jobUrl.value = a.jobUrl || '';
  appForm.elements.notes.value = a.notes || '';
  $('formTitle').textContent = 'Edit: ' + a.jobTitle + ' @ ' + a.company;
  $('saveBtn').textContent = 'Update';
  appForm.scrollIntoView({ behavior: 'smooth' });
}

function resetForm() {
  appForm.reset();
  editingId = null;
  $('formTitle').textContent = 'Nayi application';
  $('saveBtn').textContent = 'Save';
}

$('cancelEdit').addEventListener('click', resetForm);

async function removeApp(id) {
  if (!confirm('Ye application delete karna hai?')) return;
  const r = await api('DELETE', '/api/applications/' + id);
  log('Delete', r.status, r.data);
  if (r.status === 204) await loadAll();
}

async function aiEmail(id) {
  const r = await api('POST', '/api/ai/followup-email', { applicationId: id });
  if (r.status === 200) {
    currentDraft = r.data.text;
    $('aiBox').textContent = currentDraft;
  } else {
    log('AI email', r.status, r.data);
  }
}

$('filterStatus').addEventListener('change', loadApps);

// Draft ki pehli line "Subject: ..." hoti hai, use alag karo
function splitDraft(text) {
  const lines = text.split('\n');
  if (lines[0].toLowerCase().startsWith('subject:')) {
    return { subject: lines[0].slice(8).trim(), body: lines.slice(1).join('\n').trim() };
  }
  return { subject: '', body: text };
}

$('copyEmailBtn').addEventListener('click', async () => {
  if (!currentDraft) {
    $('log').textContent = 'Pehle "AI email" dabao';
    return;
  }
  await navigator.clipboard.writeText(currentDraft);
  $('log').textContent = 'Email clipboard par copy ho gayi. Ab Gmail/Outlook mein paste karo.';
});

// mailto: link tumhare default mail app (Gmail, Outlook) mein subject aur body bhar deta hai
$('mailEmailBtn').addEventListener('click', () => {
  if (!currentDraft) {
    $('log').textContent = 'Pehle "AI email" dabao';
    return;
  }
  const { subject, body } = splitDraft(currentDraft);
  const to = $('recruiterEmail').value.trim();
  window.location.href = 'mailto:' + encodeURIComponent(to)
    + '?subject=' + encodeURIComponent(subject)
    + '&body=' + encodeURIComponent(body);
});

// Page reload par refresh cookie se session wapas laao
window.addEventListener('load', async () => {
  if (await refresh()) await loadAll();
});

// ---------- Resume ----------

// Binary (file) requests ke liye fetch wrapper. 401 par refresh karke ek baar retry.
async function authedFetch(path, opts = {}, retry = true) {
  const headers = { ...(opts.headers || {}) };
  if (accessToken) headers['Authorization'] = 'Bearer ' + accessToken;
  const res = await fetch(path, { ...opts, headers, credentials: 'same-origin' });
  if (res.status === 401 && retry && await refresh()) {
    return authedFetch(path, opts, false);
  }
  return res;
}

async function loadResume() {
  const r = await api('GET', '/api/resume');
  $('resumeInfo').textContent = r.status === 200
    ? 'Uploaded: ' + r.data.filename + ' (' + Math.round(r.data.size / 1024) + ' KB)'
    : 'Koi resume upload nahi hua';
}

$('uploadResumeBtn').addEventListener('click', async () => {
  const file = $('resumeFile').files[0];
  if (!file) {
    $('log').textContent = 'Pehle PDF ya DOCX file chuno';
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    $('log').textContent = 'File 5 MB se badi hai';
    return;
  }
  const res = await authedFetch('/api/resume?filename=' + encodeURIComponent(file.name), {
    method: 'POST',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
  log('Resume upload', res.status, data);
  if (res.status === 201) {
    $('resumeFile').value = '';
    await loadResume();
  }
});

$('downloadResumeBtn').addEventListener('click', async () => {
  const res = await authedFetch('/api/resume/download');
  if (!res.ok) {
    const text = await res.text();
    log('Download', res.status, text);
    return;
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const m = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') || '');
  const a = document.createElement('a');
  a.href = url;
  a.download = m ? m[1] : 'resume';
  document.body.append(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
});

$('deleteResumeBtn').addEventListener('click', async () => {
  if (!confirm('Resume delete karna hai?')) return;
  const r = await api('DELETE', '/api/resume');
  log('Resume delete', r.status, r.data);
  if (r.status === 204) await loadResume();
});
