// Client area (Get Started page): login, my projects, AI chat. Uses helpers from script.js

const $ = (id) => document.getElementById(id);
const STATUS_LABELS = {
  GATHERING: 'Gathering requirements',
  READY: 'Ready for document',
  DOCUMENT_READY: 'Document ready',
  DOCUMENT_OUTDATED: 'Document needs update',
  SUBMITTED: 'Sent to Thinkvalley',
  IN_REVIEW: 'In review',
  QUOTED: 'Quoted',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined'
};

let current = null; // open project detail
let busy = false;
let viewId = 0; // increases on every navigation, so late replies don't draw on another view

function getAuth() {
  try {
    return JSON.parse(sessionStorage.getItem('tvaswAuth'));
  } catch (err) {
    return null;
  }
}

async function apiFetch(path, options = {}) {
  const auth = getAuth();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (auth) headers.Authorization = `Bearer ${auth.token}`;

  const resp = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (resp.ok) return await resp.json();

  const body = await resp.json().catch(() => ({}));
  if (resp.status === 401 && auth) {
    sessionStorage.removeItem('tvaswAuth');
    busy = false;
    route();
    throw new Error('Your login has expired. Please log in again.');
  }
  throw new Error(body.error || `Request failed (${resp.status})`);
}

function post(path, body) {
  return apiFetch(path, { method: 'POST', body: body && JSON.stringify(body) });
}

function setStatus(id, message, isError = false) {
  $(id).textContent = message;
  $(id).style.color = isError ? '#dc2626' : '#16a34a';
}

function projectPath(action = '') {
  return `/api/ai/projects/${current.project.id}${action}`;
}

// ---- Views ----

function showView(name) {
  ['authForm', 'projectsView', 'newProjectView', 'chatView'].forEach(id => { $(id).hidden = id !== name; });
}

async function route() {
  const view = ++viewId;
  const auth = getAuth();
  $('authUser').hidden = !auth;
  $('authStatus').textContent = '';
  if (!auth) return showView('authForm');

  $('authUserEmail').textContent = auth.email;
  const match = location.hash.match(/^#project-(\d+)$/);
  if (location.hash === '#new') {
    current = null;
    showView('newProjectView');
  } else if (match) {
    await openProject(match[1], view);
  } else {
    await showProjects(view);
  }
}

async function showProjects(view) {
  current = null;
  try {
    const projects = await apiFetch('/api/ai/projects');
    if (view !== viewId) return;
    if (projects.length === 0) {
      location.replace('#new');
      return;
    }
    $('projectList').innerHTML = projects.map(p => `
      <a class="project-card" href="#project-${p.id}">
        <span><strong>${escapeHtml(p.name)}</strong><small>${new Date(p.createdAt).toLocaleDateString()} · ${p.progress}% complete</small></span>
        <span class="badge badge-${p.stage.toLowerCase()}">${STATUS_LABELS[p.stage] || escapeHtml(p.stage)}</span>
      </a>
    `).join('');
    setStatus('projectsStatus', '');
  } catch (err) {
    if (view !== viewId) return;
    setStatus('projectsStatus', err.message, true);
  }
  showView('projectsView');
}

async function openProject(id, view) {
  try {
    const detail = await apiFetch(`/api/ai/projects/${id}`);
    if (view !== viewId) return;
    current = detail;
    showView('chatView');
    setStatus('chatStatus', '');
    $('assistantInput').value = '';
    renderProject();
    if (detail.messages.length === 0 && !detail.project.submittedAt) await startChat();
    else if (!detail.project.submittedAt) $('assistantInput').focus();
  } catch (err) {
    if (view !== viewId) return;
    showView('projectsView');
    $('projectList').innerHTML = '';
    setStatus('projectsStatus', err.message, true);
  }
}

// ---- Chat ----

function bubble(author, html) {
  const mine = author === 'Client';
  return `<div class="chat-row ${mine ? 'mine' : ''}">
    ${mine ? '' : '<span class="chat-avatar" aria-hidden="true">AI</span>'}
    <div class="chat-bubble">${html}</div>
  </div>`;
}

function renderProject() {
  const { project, messages } = current;
  const sent = !!project.submittedAt;

  $('chatTitle').textContent = project.name;
  $('chatBadge').textContent = STATUS_LABELS[project.stage] || project.stage;
  $('chatBadge').className = `badge badge-${project.stage.toLowerCase()}`;
  $('chatProgress').value = project.progress;
  $('chatProgressText').textContent = `Requirements ${project.progress}% complete`;
  $('sentNotice').hidden = !sent;

  $('assistantMessages').innerHTML = messages.map(m =>
    bubble(m.author, m.author === 'Client' ? escapeHtml(m.content).replace(/\n/g, '<br>') : renderMarkdown(m.content))
  ).join('');
  scrollChat();

  const last = messages[messages.length - 1];
  renderOptions(!sent && last && last.author !== 'Client' ? last.options : []);

  $('chatComposer').hidden = sent;
  $('generateDraftBtn').hidden = sent;
  const label = current.prd ? 'Update document' : 'Create document';
  $('generateDraftBtn').textContent = label;
  $('readyCreateBtn').textContent = label;
  $('readyNotice').hidden = !['READY', 'DOCUMENT_OUTDATED'].includes(project.stage);
  if (current.draftOutdated) $('readyNotice').firstElementChild.textContent = 'Your chat has new details. Update your document to include them.';
  else $('readyNotice').firstElementChild.textContent = 'Your requirements are ready. Create your document now, or keep chatting to add details.';
  renderDraft(sent);
}

function renderDraft(sent) {
  $('assistantDraft').hidden = !current.prd;
  if (!current.prd) return;
  $('draftOutput').innerHTML = `<h2>Product Requirements</h2>${renderMarkdown(current.prd)}<h2>System Requirements</h2>${renderMarkdown(current.srs || '')}`;
  $('draftOutdated').hidden = sent || !current.draftOutdated;
  $('submitProjectBtn').hidden = sent;
  $('submitProjectBtn').disabled = busy || current.draftOutdated;
}

function renderOptions(options) {
  const box = $('quickReplies');
  box.innerHTML = '';
  options.forEach(text => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'quick-reply';
    btn.textContent = text;
    btn.addEventListener('click', () => sendMessage(text));
    box.appendChild(btn);
  });
}

function scrollChat() {
  const box = $('assistantMessages');
  box.scrollTop = box.scrollHeight;
}

function showTyping(text) {
  if (text) $('assistantMessages').insertAdjacentHTML('beforeend', bubble('Client', escapeHtml(text).replace(/\n/g, '<br>')));
  $('assistantMessages').insertAdjacentHTML('beforeend',
    bubble('Assistant', '<span class="typing" role="status" aria-label="Assistant is typing"><i></i><i></i><i></i></span>'));
  renderOptions([]);
  scrollChat();
}

function setBusy(value) {
  busy = value;
  ['assistantSend', 'generateDraftBtn', 'readyCreateBtn'].forEach(id => { $(id).disabled = value; });
  $('submitProjectBtn').disabled = value || !!current?.draftOutdated;
}

// Runs one project request at a time; the result is dropped if the user has left the project
async function runAction(request, { onError, done = '' } = {}) {
  if (busy) return;
  const view = viewId;
  setBusy(true);
  try {
    const detail = await request();
    if (view !== viewId) return;
    current = detail;
    renderProject();
    setStatus('chatStatus', done);
  } catch (err) {
    if (view !== viewId) return;
    renderProject();
    if (onError) onError();
    setStatus('chatStatus', err.message, true);
  } finally {
    setBusy(false);
  }
}

async function startChat() {
  if (busy) return showRetry('Start the chat', startChat);
  await runAction(() => {
    showTyping();
    return post(projectPath('/start'));
  }, { onError: () => showRetry('Start the chat', startChat) });
}

async function sendMessage(text) {
  text = text.trim();
  if (!text || busy) return;
  $('assistantInput').value = '';
  setStatus('chatStatus', '');
  await runAction(() => {
    showTyping(text);
    return post(projectPath('/messages'), { message: text });
  }, { onError: () => { $('assistantInput').value = text; } }); // keep the text so it can be resent
  if (current && !$('chatView').hidden) $('assistantInput').focus();
}

function showRetry(label, action) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'quick-reply';
  btn.textContent = label;
  btn.addEventListener('click', action);
  $('quickReplies').replaceChildren(btn);
}

$('chatComposer').addEventListener('submit', (e) => {
  e.preventDefault();
  sendMessage($('assistantInput').value);
});

$('assistantInput').addEventListener('keydown', (e) => {
  // Enter sends, Shift+Enter adds a line
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    sendMessage($('assistantInput').value);
  }
});

async function createDocument() {
  if (busy) return;
  setStatus('chatStatus', 'Writing your document. This can take up to a minute...');
  await runAction(() => post(projectPath('/draft')), { done: 'Document ready. Download it below or send it to Thinkvalley.' });
  if (current?.prd && !current.draftOutdated) $('assistantDraft').scrollIntoView({ behavior: 'smooth' });
}

$('generateDraftBtn').addEventListener('click', createDocument);
$('readyCreateBtn').addEventListener('click', createDocument);

$('downloadWordBtn').addEventListener('click', async () => {
  try {
    await downloadWord(current);
  } catch (err) {
    setStatus('chatStatus', err.message, true);
  }
});

$('downloadPdfBtn').addEventListener('click', () => printPdf(current));

$('submitProjectBtn').addEventListener('click', () => {
  if (!confirm('Send these requirements to Thinkvalley? You can\'t change them afterwards.')) return;
  runAction(() => post(projectPath('/submit')));
});

// ---- New project ----

$('getStartedForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const data = new FormData(form);
  const value = (name) => data.get(name).toString().trim();
  const btn = $('getStartedSubmit');

  btn.disabled = true;
  setStatus('getStartedStatus', '');
  try {
    const project = await post('/api/ai/projects', {
      name: value('projectName'),
      industry: value('requirementType'),
      description: [`Summary: ${value('summary')}`,
        `Timeline: ${value('timeline') || 'Not specified'}`,
        `Budget: ${value('budget') || 'Not specified'}`,
        `Preferred Technologies: ${value('tech') || 'Not specified'}`
      ].join('\n')
    });
    form.reset();
    location.hash = `project-${project.id}`;
  } catch (err) {
    setStatus('getStartedStatus', err.message, true);
  } finally {
    btn.disabled = false;
  }
});

// ---- Login and registration ----

let registering = true;

function setAuthMode(register) {
  registering = register;
  [['authTabRegister', register], ['authTabLogin', !register]].forEach(([id, active]) => {
    $(id).classList.toggle('btn-primary', active);
    $(id).classList.toggle('btn-outline', !active);
    $(id).setAttribute('aria-pressed', active);
  });
  $('authSubmit').textContent = register ? 'Create account' : 'Log in';
  $('authNameGroup').hidden = !register;
  $('authTermsNote').hidden = !register;
  $('authName').required = register;
  $('authPassword').autocomplete = register ? 'new-password' : 'current-password';
  $('authStatus').textContent = '';
}

$('authTabRegister').addEventListener('click', () => setAuthMode(true));
$('authTabLogin').addEventListener('click', () => setAuthMode(false));

$('authForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = { email: $('authEmail').value.trim(), password: $('authPassword').value };
  if (registering) payload.name = $('authName').value.trim();

  setStatus('authStatus', '');
  $('authSubmit').disabled = true;
  try {
    const auth = await post(registering ? '/api/auth/register' : '/api/auth/login', payload);
    $('authPassword').value = '';
    sessionStorage.setItem('tvaswAuth', JSON.stringify(auth));
    route();
  } catch (err) {
    setStatus('authStatus', err.message, true);
  } finally {
    $('authSubmit').disabled = false;
  }
});

$('logoutBtn').addEventListener('click', () => {
  sessionStorage.removeItem('tvaswAuth');
  history.replaceState(null, '', location.pathname);
  location.reload(); // also clears forms and the open chat
});

window.addEventListener('hashchange', route);
route();
