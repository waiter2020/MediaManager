import { createContext, useContext } from 'react';

export const MediaCardPreviewVisibilityContext = createContext<Element | null>(null);

export function useMediaCardPreviewVisibilityRoot() {
  return useContext(MediaCardPreviewVisibilityContext);
}

export const MediaCardPreviewVisibilityProvider = MediaCardPreviewVisibilityContext.Provider;
