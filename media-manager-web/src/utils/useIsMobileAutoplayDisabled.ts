import { useEffect, useState } from 'react';

const MOBILE_AUTOPLAY_DISABLED_QUERY = '(max-width: 768px), (hover: none) and (pointer: coarse)';

function getIsMobileAutoplayDisabled() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }

  return window.matchMedia(MOBILE_AUTOPLAY_DISABLED_QUERY).matches;
}

export function useIsMobileAutoplayDisabled() {
  const [disabled, setDisabled] = useState(getIsMobileAutoplayDisabled);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined;
    }

    const mediaQuery = window.matchMedia(MOBILE_AUTOPLAY_DISABLED_QUERY);
    const handleChange = () => setDisabled(mediaQuery.matches);

    handleChange();

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }

    mediaQuery.addListener(handleChange);
    return () => mediaQuery.removeListener(handleChange);
  }, []);

  return disabled;
}
