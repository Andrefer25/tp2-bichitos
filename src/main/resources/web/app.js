(() => {
  const views = [...document.querySelectorAll('.view')];
  const links = [...document.querySelectorAll('[data-view]')];
  const title = document.querySelector('#page-title');
  const subtitle = document.querySelector('#page-subtitle');
  const crumb = document.querySelector('#crumb-current');
  const flash = document.querySelector('#flash');
  function ensureSelectedRecord() {
    const query = new URLSearchParams(location.search).get('postulacion') || '';
    if (document.body.dataset.postulacion !== query) location.reload();
  }
  window.addEventListener('pageshow', ensureSelectedRecord);
  window.addEventListener('popstate', ensureSelectedRecord);

  function show(id, updateHash = true) {
    const view = views.find(item => item.id === 'view-' + id) || views[0];
    views.forEach(item => item.classList.toggle('active', item === view));
    links.forEach(link => link.classList.toggle('active', link.dataset.view === view.id.slice(5)));
    title.textContent = view.dataset.heading;
    subtitle.textContent = view.dataset.subtitle;
    crumb.textContent = view.dataset.title;
    if (updateHash) history.replaceState(null, '', '#' + view.id.slice(5));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  links.forEach(link => link.addEventListener('click', () => show(link.dataset.view)));
  document.querySelectorAll('[data-go]').forEach(button =>
    button.addEventListener('click', () => show(button.dataset.go)));
  show(location.hash.slice(1) || 'overview', false);
  window.addEventListener('hashchange', () => show(location.hash.slice(1) || 'overview', false));

  const savedMessage = sessionStorage.getItem('bichitos-message');
  if (savedMessage) {
    flash.textContent = savedMessage;
    flash.hidden = false;
    flash.classList.add('notice-success');
    sessionStorage.removeItem('bichitos-message');
  }

  document.querySelectorAll('form').forEach(form => form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    const original = button?.textContent;
    if (button) { button.disabled = true; button.textContent = 'Guardando…'; }
    form.querySelector('.form-error')?.remove();
    try {
      let options;
      if (form.hasAttribute('data-upload')) {
        const file = form.querySelector('input[type="file"]').files[0];
        if (!file) throw new Error('Seleccioná un CSV.');
        if (file.size > 2000000) throw new Error('El CSV no puede superar 2 MB.');
        options = { method: 'POST', headers: {
          'Content-Type': 'text/csv; charset=utf-8',
          'X-Filename': encodeURIComponent(file.name)
        }, body: file };
      } else {
        options = { method: 'POST', headers: {
          'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8'
        }, body: new URLSearchParams(new FormData(form)) };
      }
      const response = await fetch(form.action, options);
      const result = await response.json();
      if (!response.ok || !result.ok) throw new Error(result.message || 'No se pudo guardar.');
      sessionStorage.setItem('bichitos-message', result.message);
      location.assign(result.next);
    } catch (error) {
      const message = document.createElement('p');
      message.className = 'form-error';
      message.setAttribute('role', 'alert');
      message.textContent = error.message || 'No se pudo completar la acción.';
      form.prepend(message);
      if (button) { button.disabled = false; button.textContent = original; }
    }
  }));

  document.querySelectorAll('[data-candidate-search]').forEach(input => input.addEventListener('input', () => {
    const term = input.value.trim().toLowerCase();
    document.querySelectorAll('[data-candidate-name]').forEach(card => {
      card.hidden = term && !card.dataset.candidateName.includes(term);
    });
  }));
})();
