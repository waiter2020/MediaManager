import { useEffect, useState } from 'react';

function getIsPageVisible() {
  if (typeof document === 'undefined') {
    return true;
  }
  return !document.hidden;
}

export function usePageVisibility() {
  const [isPageVisible, setIsPageVisible] = useState(getIsPageVisible);

  useEffect(() => {
    const handleChange = () => setIsPageVisible(!document.hidden);
    document.addEventListener('visibilitychange', handleChange);
    return () => document.removeEventListener('visibilitychange', handleChange);
  }, []);

  return isPageVisible;
}
