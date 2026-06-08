export function applyBackofficeBodyClass(): () => void {
  const prev = document.body.getAttribute('class');
  const themeAttrs = {
    'data-theme-version': 'light',
    'data-layout': 'vertical',
    'data-nav-headerbg': 'color_1',
    'data-headerbg': 'color_1',
    'data-sidebarbg': 'color_1',
    'data-sidebar-style': 'full',
    'data-sidebar-position': 'fixed',
    'data-header-position': 'fixed',
    'data-container': 'wide',
    direction: 'ltr',
  } as const;
  const previousAttrs = Object.fromEntries(
    Object.keys(themeAttrs).map((name) => [name, document.body.getAttribute(name)])
  ) as Record<keyof typeof themeAttrs, string | null>;

  document.body.setAttribute('class', 'backoffice');
  for (const [name, value] of Object.entries(themeAttrs)) {
    document.body.setAttribute(name, value);
  }

  const styleId = 'backoffice-theme-style';
  if (!document.getElementById(styleId)) {
    const link = document.createElement('link');
    link.id = styleId;
    link.rel = 'stylesheet';
    link.href = 'assets/backoffice/css/style.css';
    document.head.appendChild(link);
  }

  const iconsId = 'backoffice-material-icons';
  if (!document.getElementById(iconsId)) {
    const link = document.createElement('link');
    link.id = iconsId;
    link.rel = 'stylesheet';
    link.href = 'https://fonts.googleapis.com/css2?family=Material+Icons';
    document.head.appendChild(link);
  }

  return () => {
    if (prev === null) {
      document.body.removeAttribute('class');
    } else {
      document.body.setAttribute('class', prev);
    }

    for (const [name, value] of Object.entries(previousAttrs)) {
      if (value === null) {
        document.body.removeAttribute(name);
      } else {
        document.body.setAttribute(name, value);
      }
    }
  };
}


