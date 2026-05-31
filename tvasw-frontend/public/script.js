// Mobile nav toggle
const navToggle = document.getElementById('navToggle');
const navLinks = document.getElementById('navLinks');

navToggle.addEventListener('click', () => {
  navLinks.classList.toggle('active');
});

// Close mobile nav on link click
navLinks.querySelectorAll('a').forEach(link => {
  link.addEventListener('click', () => {
    navLinks.classList.remove('active');
  });
});

// Navbar scroll effect
window.addEventListener('scroll', () => {
  const navbar = document.getElementById('navbar');
  navbar.classList.toggle('scrolled', window.scrollY > 20);
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

// Contact form
document.getElementById('contactForm').addEventListener('submit', async function(e) {
  e.preventDefault();

  const btn = document.getElementById('submitBtn');
  const btnText = btn.querySelector('.btn-text');
  const btnLoader = btn.querySelector('.btn-loader');
  const status = document.getElementById('statusMessage');

  btn.disabled = true;
  btnText.style.display = 'none';
  btnLoader.style.display = 'inline';
  status.textContent = '';

  const formData = new FormData(this);
  const data = new URLSearchParams(formData);

  try {
    const res = await fetch('/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: data
    });

    const result = await res.text();

    if (res.ok) {
      status.textContent = '✅ Message sent successfully! We\'ll be in touch within 24 hours.';
      status.style.color = '#16a34a';
      this.reset();
    } else {
      status.textContent = '⚠️ ' + result;
      status.style.color = '#dc2626';
    }
  } catch (err) {
    status.textContent = '❌ Error submitting form. Please try again.';
    status.style.color = '#dc2626';
  } finally {
    btn.disabled = false;
    btnText.style.display = 'inline';
    btnLoader.style.display = 'none';
  }
});
