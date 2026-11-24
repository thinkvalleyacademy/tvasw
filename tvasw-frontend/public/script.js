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

const contactForm = document.getElementById('contactForm');
if (contactForm) {
  contactForm.addEventListener('submit', function(e) {
    e.preventDefault();
    handleFormSubmit(this, 'statusMessage', 'submitBtn');
  });
}
