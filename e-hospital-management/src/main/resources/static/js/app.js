'use strict';
const toggle = document.querySelector('.menu-toggle');
const sidebar = document.querySelector('#sidebar');
const mobile = window.matchMedia('(max-width: 700px)');
const updateNavigation = () => {
  if (sidebar) sidebar.inert = mobile.matches && toggle?.getAttribute('aria-expanded') !== 'true';
};
updateNavigation();
mobile.addEventListener('change', updateNavigation);
toggle?.addEventListener('click', () => {
  const expanded = toggle.getAttribute('aria-expanded') !== 'true';
  toggle.setAttribute('aria-expanded', String(expanded));
  document.body.classList.toggle('nav-open', expanded);
  updateNavigation();
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') {
    document.body.classList.remove('nav-open');
    toggle?.setAttribute('aria-expanded', 'false');
    updateNavigation();
    document.querySelectorAll('details[open]').forEach(menu => menu.removeAttribute('open'));
  }
});
document.querySelectorAll('form').forEach(form => {
  let dirty = false;
  if (form.hasAttribute('data-unsaved-warning')) {
    form.addEventListener('input', () => { dirty = true; });
    window.addEventListener('beforeunload', event => {
      if (dirty) { event.preventDefault(); event.returnValue = ''; }
    });
  }
  form.addEventListener('submit', event => {
    if (form.hasAttribute('data-confirm-inactive') && !form.querySelector('#active')?.checked
        && !window.confirm('Deactivate this account? The staff member will lose access.')) {
      event.preventDefault(); return;
    }
    dirty = false;
    const submit = form.querySelector('button[type="submit"]');
    if (submit) {
      submit.dataset.originalText = submit.textContent;
      submit.disabled = true; submit.setAttribute('aria-busy', 'true'); submit.textContent = 'Please wait…';
    }
  });
});
window.addEventListener('pageshow', () => {
  document.querySelectorAll('button[aria-busy="true"]').forEach(button => {
    button.disabled = false; button.removeAttribute('aria-busy');
    if (button.dataset.originalText) button.textContent = button.dataset.originalText;
  });
});
const tokenInput = document.querySelector('#resetToken');
if (tokenInput) {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
  if (token && /^[A-Za-z0-9_-]{43}$/.test(token)) tokenInput.value = token;
  if (window.location.hash) window.history.replaceState(null, '', window.location.pathname);
  const hint = document.querySelector('#resetLinkHint');
  if (hint && tokenInput.value) hint.textContent = 'Your recovery link is loaded. Enter your new password below.';
}
