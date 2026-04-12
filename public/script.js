document.getElementById('contactForm').addEventListener('submit', async function(e) {
  e.preventDefault();

  const formData = new FormData(this);
  const data = new URLSearchParams(formData);

  const status = document.getElementById('statusMessage');
  status.innerText = "Submitting...";

  try {
    const res = await fetch('/send', {
      method: 'POST',
      body: data
    });

    const result = await res.text();
    status.innerText = result;
    status.style.color = "green";

    this.reset();

  } catch (err) {
    status.innerText = "Error submitting form";
    status.style.color = "red";
  }
});