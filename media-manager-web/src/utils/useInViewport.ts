import { useEffect, useRef, useState } from 'react';

interface UseInViewportOptions {
  root?: Element | null;
  rootMargin?: string;
  threshold?: number | number[];
  disabled?: boolean;
}

export function useInViewport({
  root = null,
  rootMargin = '0px',
  threshold = 0,
  disabled = false,
}: UseInViewportOptions = {}) {
  const ref = useRef<HTMLDivElement>(null);
  const [isInViewport, setIsInViewport] = useState(false);
  const [intersectionRatio, setIntersectionRatio] = useState(0);

  useEffect(() => {
    if (disabled) {
      setIsInViewport(false);
      setIntersectionRatio(0);
      return undefined;
    }

    const element = ref.current;
    if (!element) {
      return undefined;
    }

    if (typeof IntersectionObserver === 'undefined') {
      setIsInViewport(true);
      setIntersectionRatio(1);
      return undefined;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry) {
          return;
        }
        setIsInViewport(entry.isIntersecting);
        setIntersectionRatio(entry.intersectionRatio);
      },
      {
        root,
        rootMargin,
        threshold,
      },
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, [disabled, root, rootMargin, threshold]);

  return { ref, isInViewport, intersectionRatio };
}
