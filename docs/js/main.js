document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initLangToggle();
  initCopyButtons();
  initSidebar();
  initTabs();
  initSidebarHighlight();
});

/* ===== Theme Toggle ===== */
function initThemeToggle() {
  const toggle = document.querySelector('.theme-toggle');
  if (!toggle) return;
  // Restore saved theme or respect system preference
  const saved = localStorage.getItem('bq-theme');
  if (saved) {
    setTheme(saved);
  } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    setTheme('dark');
  }
  toggle.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    const next = current === 'light' ? 'dark' : 'light';
    setTheme(next);
    localStorage.setItem('bq-theme', next);
  });
}

function setTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
}

/* ===== Language Toggle ===== */
function initLangToggle() {
  const toggle = document.querySelector('.lang-toggle');
  if (!toggle) return;
  const saved = localStorage.getItem('bq-lang') || 'en';
  setLang(saved);
  toggle.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-lang') || 'en';
    const next = current === 'en' ? 'zh' : 'en';
    setLang(next);
    localStorage.setItem('bq-lang', next);
  });
}

function setLang(lang) {
  document.documentElement.setAttribute('data-lang', lang);
  const toggle = document.querySelector('.lang-toggle');
  if (toggle) toggle.textContent = lang === 'en' ? '中文' : 'EN';
}

/* ===== Copy Buttons ===== */
function initCopyButtons() {
  // Copy code blocks
  document.querySelectorAll('pre').forEach(pre => {
    if (pre.querySelector('.copy-code')) return;
    const btn = document.createElement('button');
    btn.className = 'copy-code';
    btn.textContent = 'Copy';
    btn.addEventListener('click', () => {
      const code = pre.textContent.replace(/^Copy/, '').trim();
      navigator.clipboard.writeText(code).then(() => {
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(() => { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 2000);
      });
    });
    pre.appendChild(btn);
  });

  // Maven coordinate copy
  document.querySelectorAll('.copy-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const text = btn.getAttribute('data-copy');
      if (!text) return;
      navigator.clipboard.writeText(text).then(() => {
        btn.classList.add('copied');
        const svg = btn.innerHTML;
        btn.innerHTML = '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        setTimeout(() => { btn.innerHTML = svg; btn.classList.remove('copied'); }, 2000);
      });
    });
  });
}

/* ===== Mobile Sidebar ===== */
function initSidebar() {
  const toggle = document.querySelector('.menu-toggle');
  const sidebar = document.querySelector('.sidebar');
  const overlay = document.querySelector('.sidebar-overlay');
  if (!toggle || !sidebar) return;

  toggle.addEventListener('click', () => {
    sidebar.classList.toggle('open');
  });
  if (overlay) {
    overlay.addEventListener('click', () => {
      sidebar.classList.remove('open');
    });
  }
  // Close sidebar on link click (mobile)
  sidebar.querySelectorAll('a').forEach(a => {
    a.addEventListener('click', () => {
      if (window.innerWidth <= 960) sidebar.classList.remove('open');
    });
  });
}

/* ===== Tab Switcher ===== */
function initTabs() {
  document.querySelectorAll('.tab-group').forEach(group => {
    const buttons = group.querySelectorAll('.tab-btn');
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-tab');
        const container = group.parentElement;
        // Deactivate all
        container.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        container.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        // Activate target
        btn.classList.add('active');
        const content = container.querySelector(`.tab-content[data-tab="${target}"]`);
        if (content) content.classList.add('active');
      });
    });
  });
}

/* ===== Sidebar Active Highlight on Scroll ===== */
function initSidebarHighlight() {
  const links = document.querySelectorAll('.sidebar-group a[href^="#"]');
  if (!links.length) return;

  const sections = [];
  links.forEach(link => {
    const id = link.getAttribute('href').slice(1);
    const el = document.getElementById(id);
    if (el) sections.push({ el, link });
  });

  function update() {
    const scrollY = window.scrollY + 100;
    let current = sections[0];
    for (const s of sections) {
      if (s.el.offsetTop <= scrollY) current = s;
    }
    links.forEach(l => l.classList.remove('active'));
    if (current) current.link.classList.add('active');
  }

  window.addEventListener('scroll', update, { passive: true });
  update();
}
