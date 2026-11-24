const API_BASE = (window.location.protocol === 'file:' || ((window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') && window.location.port === '4080')) ? 'http://localhost:8080' : '';

// Mobile nav toggle
const navToggle = document.getElementById('navToggle');
const navLinks = document.getElementById('navLinks');

if (navToggle && navLinks) {
  navToggle.addEventListener('click', () => {
    navLinks.classList.toggle('active');
    navToggle.classList.toggle('active');
  });

  navLinks.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      navLinks.classList.remove('active');
      navToggle.classList.remove('active');
    });
  });
}

// Navbar scroll effect
window.addEventListener('scroll', () => {
  const navbar = document.getElementById('navbar');
  if (navbar) {
    navbar.classList.toggle('scrolled', window.scrollY > 20);
  }
});

// Scroll reveal animation
const observerOptions = {
  threshold: 0.1,
  rootMargin: '0px 0px -50px 0px'
};

const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, observerOptions);

document.querySelectorAll('.service-card, .process-step, .client-card').forEach(el => {
  observer.observe(el);
});

// Helper: handle standard form submission by id and endpoint
async function handleFormSubmit(formEl, statusElId, submitBtnId, endpoint = '/send') {
  const btn = document.getElementById(submitBtnId);
  const btnText = btn && btn.querySelector('.btn-text');
  const btnLoader = btn && btn.querySelector('.btn-loader');
  const status = document.getElementById(statusElId);

  if (btn) btn.disabled = true;
  if (btnText) btnText.style.display = 'none';
  if (btnLoader) btnLoader.style.display = 'inline';
  if (status) status.textContent = '';

  const formData = new FormData(formEl);
  const data = new URLSearchParams();
  for (const pair of formData.entries()) data.append(pair[0], pair[1]);

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: data
    });

    const result = await res.text();

    if (res.ok) {
      if (status) {
        status.textContent = '✅ Message sent successfully! We\'ll be in touch within 24 hours.';
        status.style.color = '#16a34a';
      }
      formEl.reset();
    } else {
      if (status) {
        status.textContent = '⚠️ ' + result;
        status.style.color = '#dc2626';
      }
    }
  } catch (err) {
    if (status) {
      status.textContent = '❌ Error submitting form. Please try again.';
      status.style.color = '#dc2626';
    }
  } finally {
    if (btn) btn.disabled = false;
    if (btnText) btnText.style.display = 'inline';
    if (btnLoader) btnLoader.style.display = 'none';
  }
}

function setAssistantStatus(message, isError = false) {
  const status = document.getElementById('assistantStatus');
  if (!status) return;
  status.textContent = message;
  status.style.color = isError ? '#dc2626' : '#16a34a';
}

function showAssistantPanel(show) {
  const panel = document.getElementById('assistantPanel');
  if (!panel) return;
  panel.classList.toggle('hidden', !show);
}

function escapeHtml(text) {
  const el = document.createElement('div');
  el.textContent = text;
  return el.innerHTML;
}

function appendAssistantMessage(role, text) {
  const container = document.getElementById('assistantMessages');
  if (!container) return;

  const messageEl = document.createElement('div');
  messageEl.className = `assistant-message ${role}`;
  messageEl.innerHTML = `
    <span class="assistant-role">${role === 'user' ? 'You' : 'AI Assistant'}</span>
    <p>${escapeHtml(text).replace(/\n/g, '<br>')}</p>
  `;
  container.appendChild(messageEl);
  container.scrollTop = container.scrollHeight;
}

// Login state for this browser tab
function getAuth() {
  try {
    return JSON.parse(sessionStorage.getItem('tvaswAuth'));
  } catch (err) {
    return null;
  }
}

function setAuth(auth) {
  if (auth) sessionStorage.setItem('tvaswAuth', JSON.stringify(auth));
  else sessionStorage.removeItem('tvaswAuth');
  renderAuth();
}

async function apiFetch(path, options = {}) {
  const auth = getAuth();
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (auth) headers.Authorization = `Bearer ${auth.token}`;

  const resp = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (resp.ok) return await resp.json();

  const body = await resp.json().catch(() => ({}));
  if (resp.status === 401 && auth) {
    setAuth(null);
    throw new Error('Your login has expired. Please log in again.');
  }
  throw new Error(body.error || `Request failed (${resp.status})`);
}

async function startAiAssistant(projectId, ideaText) {
  return await apiFetch('/api/ai/start', {
    method: 'POST',
    body: JSON.stringify({ projectId, ideaText })
  });
}

async function sendAssistantMessage(projectId, sessionId, message) {
  return await apiFetch('/api/ai/message', {
    method: 'POST',
    body: JSON.stringify({ projectId, sessionId, message })
  });
}

async function fetchRequirementDraft(projectId) {
  return await apiFetch(`/api/ai/draft/${projectId}`);
}

let aiProjectId = null;
let aiSessionId = null;

async function handleGetStartedSubmit(formEl, statusElId, submitBtnId) {
  const btn = document.getElementById(submitBtnId);
  const btnText = btn && btn.querySelector('.btn-text');
  const btnLoader = btn && btn.querySelector('.btn-loader');
  const status = document.getElementById(statusElId);
  const assistantDraft = document.getElementById('assistantDraft');

  if (btn) btn.disabled = true;
  if (btnText) btnText.style.display = 'none';
  if (btnLoader) btnLoader.style.display = 'inline';
  if (status) status.textContent = '';

  try {
    const formData = new FormData(formEl);
    const name = formData.get('name')?.toString().trim();
    const email = formData.get('email')?.toString().trim();
    const requirementType = formData.get('requirementType')?.toString().trim();
    const summary = formData.get('summary')?.toString().trim();
    const timeline = formData.get('timeline')?.toString().trim();
    const budget = formData.get('budget')?.toString().trim();
    const tech = formData.get('tech')?.toString().trim();

    if (!name || !email || !requirementType || !summary) {
      throw new Error('Please complete all required fields before submitting.');
    }

    const projectName = `${requirementType} request from ${name}`;
    const projectDescription = [`Summary: ${summary}`,
      timeline ? `Timeline: ${timeline}` : 'Timeline: Not specified',
      budget ? `Budget: ${budget}` : 'Budget: Not specified',
      tech ? `Preferred Technologies: ${tech}` : 'Preferred Technologies: Not specified'
    ].join('\n');

    const project = await apiFetch('/api/ai/projects', {
      method: 'POST',
      body: JSON.stringify({ name: projectName, industry: requirementType, description: projectDescription })
    });
    aiProjectId = project.id;

    const assistantResponse = await startAiAssistant(aiProjectId, summary);
    aiSessionId = assistantResponse.sessionId;

    const draftContainer = document.getElementById('assistantDraft');
    if (draftContainer) draftContainer.classList.add('hidden');

    showAssistantPanel(true);
    appendAssistantMessage('assistant', assistantResponse.message || 'AI assistant is ready to help you refine requirements.');
    setAssistantStatus('✅ AI assistant connected. Ask a question or generate a draft.', false);

    if (status) {
      status.textContent = 'Project created successfully and AI assistant is ready to help refine your requirements.';
      status.style.color = '#16a34a';
    }
  } catch (err) {
    const message = err?.message || 'Unexpected error submitting your requirements.';
    if (status) {
      status.textContent = `⚠️ ${message}`;
      status.style.color = '#dc2626';
    }
    setAssistantStatus(message, true);
  } finally {
    if (btn) btn.disabled = false;
    if (btnText) btnText.style.display = 'inline';
    if (btnLoader) btnLoader.style.display = 'none';
  }
}

const contactForm = document.getElementById('contactForm');
if (contactForm) {
  contactForm.addEventListener('submit', function(e) {
    e.preventDefault();
    handleFormSubmit(this, 'statusMessage', 'submitBtn');
  });
}

const getStartedForm = document.getElementById('getStartedForm');
if (getStartedForm) {
  getStartedForm.addEventListener('submit', function(e) {
    e.preventDefault();
    handleGetStartedSubmit(this, 'getStartedStatus', 'getStartedSubmit');
  });
}

const assistantSend = document.getElementById('assistantSend');
if (assistantSend) {
  assistantSend.addEventListener('click', async () => {
    const input = document.getElementById('assistantInput');
    const message = input?.value?.trim();
    if (!message) return;
    if (!aiProjectId || !aiSessionId) {
      setAssistantStatus('Please submit the requirement form first to start the AI assistant.', true);
      return;
    }

    appendAssistantMessage('user', message);
    input.value = '';
    setAssistantStatus('Waiting for the AI assistant...', false);

    try {
      const response = await sendAssistantMessage(aiProjectId, aiSessionId, message);
      appendAssistantMessage('assistant', response.reply || 'No response returned.');
      setAssistantStatus('AI assistant responded. Continue the conversation or generate a draft.', false);
    } catch (err) {
      setAssistantStatus(err?.message || 'AI assistant request failed.', true);
    }
  });
}

const generateDraftBtn = document.getElementById('generateDraftBtn');
if (generateDraftBtn) {
  generateDraftBtn.addEventListener('click', async () => {
    if (!aiProjectId) {
      setAssistantStatus('Submit your requirement form first to generate a draft.', true);
      return;
    }

    setAssistantStatus('Generating a draft from your requirements...', false);

    try {
      const draft = await fetchRequirementDraft(aiProjectId);
      const draftOutput = document.getElementById('draftOutput');
      const draftContainer = document.getElementById('assistantDraft');
      if (draftOutput) {
        draftOutput.textContent = `Product Requirements Document:\n${draft.prd}\n\nSystem Requirements Summary:\n${draft.srs}`;
      }
      if (draftContainer) draftContainer.classList.remove('hidden');
      setAssistantStatus('Draft generated successfully. Review and refine it with the assistant.', false);
    } catch (err) {
      setAssistantStatus(err?.message || 'Draft generation failed.', true);
    }
  });
}

// Login and registration (Get Started page)
function renderAuth() {
  const authForm = document.getElementById('authForm');
  if (!authForm) return;

  const auth = getAuth();
  authForm.hidden = !!auth;
  document.getElementById('authUser').hidden = !auth;
  document.getElementById('getStartedForm').hidden = !auth;
  if (!auth) {
    showAssistantPanel(false);
    return;
  }

  document.getElementById('authUserEmail').textContent = `Logged in as ${auth.email}`;
  const name = document.getElementById('name');
  const email = document.getElementById('email');
  if (!name.value) name.value = auth.name;
  if (!email.value) email.value = auth.email;
  if (aiSessionId) showAssistantPanel(true);
}

const authForm = document.getElementById('authForm');
if (authForm) {
  let registering = true;

  function setAuthMode(register) {
    registering = register;
    [['authTabRegister', register], ['authTabLogin', !register]].forEach(([id, active]) => {
      const tab = document.getElementById(id);
      tab.classList.toggle('btn-primary', active);
      tab.classList.toggle('btn-outline', !active);
      tab.setAttribute('aria-pressed', active);
    });
    document.getElementById('authSubmit').textContent = register ? 'Create account' : 'Log in';
    document.getElementById('authNameGroup').hidden = !register;
    document.getElementById('authName').required = register;
    document.getElementById('authPassword').autocomplete = register ? 'new-password' : 'current-password';
    document.getElementById('authStatus').textContent = '';
  }

  document.getElementById('authTabRegister').addEventListener('click', () => setAuthMode(true));
  document.getElementById('authTabLogin').addEventListener('click', () => setAuthMode(false));

  authForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const status = document.getElementById('authStatus');
    const btn = document.getElementById('authSubmit');
    const password = document.getElementById('authPassword');
    const payload = { email: document.getElementById('authEmail').value.trim(), password: password.value };
    if (registering) payload.name = document.getElementById('authName').value.trim();

    status.textContent = '';
    btn.disabled = true;
    try {
      const auth = await apiFetch(registering ? '/api/auth/register' : '/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      password.value = '';
      setAuth(auth);
    } catch (err) {
      status.textContent = err.message;
      status.style.color = '#dc2626';
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('logoutBtn').addEventListener('click', () => {
    sessionStorage.removeItem('tvaswAuth');
    window.location.reload(); // also clears the form and conversation
  });

  renderAuth();
}
