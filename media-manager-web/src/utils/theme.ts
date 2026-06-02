const STORAGE_KEY = 'mm-theme-preference';

export type ThemePreference = 'dark' | 'light' | 'system';

export function resolveEffectiveTheme(preference: ThemePreference): 'dark' | 'light' {
  if (preference === 'system' && typeof window !== 'undefined') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return preference === 'light' ? 'light' : 'dark';
}

export function applyThemePreference(preference: ThemePreference) {
  if (typeof document === 'undefined') return;
  const effective = resolveEffectiveTheme(preference);
  localStorage.setItem(STORAGE_KEY, preference);
  document.documentElement.setAttribute('data-theme', effective);
  document.documentElement.classList.toggle('mm-light', effective === 'light');
}

export function loadStoredThemePreference(): ThemePreference | null {
  if (typeof localStorage === 'undefined') return null;
  const v = localStorage.getItem(STORAGE_KEY);
  if (v === 'dark' || v === 'light' || v === 'system') return v;
  return null;
}

export async function fetchAndApplyGlobalTheme() {
  try {
    const res = await fetch('/api/v1/system/status');
    const json = await res.json();
    const theme = json?.data?.theme as ThemePreference | undefined;
    if (theme === 'dark' || theme === 'light' || theme === 'system') {
      applyThemePreference(theme);
      return theme;
    }
  } catch {
    // ignore
  }
  const stored = loadStoredThemePreference();
  if (stored) {
    applyThemePreference(stored);
    return stored;
  }
  applyThemePreference('dark');
  return 'dark';
}
