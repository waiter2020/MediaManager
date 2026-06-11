import { appendAuthToken } from '@/services/stream';

/** Umi dev: optional direct Spring port for extra connection pool. */
const DEV_STREAM_DIRECT_PORT = 8080;

const PROBE_TIMEOUT_MS = 2000;

declare global {
  interface Window {
    __MM_STREAM_AUX_PORTS__?: number[];
  }
}

let initPromise: Promise<void> | null = null;

function pageOrigin(): string {
  const { protocol, hostname, port } = window.location;
  return `${protocol}//${hostname}${port ? `:${port}` : ''}`;
}

function buildOriginsFromAuxPorts(auxPorts: number[]): string[] {
  const { protocol, hostname, port } = window.location;
  const origins = [pageOrigin()];

  for (const auxPort of auxPorts) {
    if (`${auxPort}` === port) {
      continue;
    }
    origins.push(`${protocol}//${hostname}:${auxPort}`);
  }

  return [...new Set(origins)];
}

async function probeAuxPort(auxPort: number): Promise<boolean> {
  const { protocol, hostname, port: pagePort } = window.location;
  if (`${auxPort}` === pagePort) {
    return false;
  }

  const origin = `${protocol}//${hostname}:${auxPort}`;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);

  try {
    const response = await fetch(`${origin}/api/v1/system/status`, {
      method: 'GET',
      mode: 'cors',
      signal: controller.signal,
    });
    return response.ok || response.status === 401;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function probeStreamAuxPorts(candidatePorts: number[]): Promise<number[]> {
  const uniquePorts = [...new Set(candidatePorts.filter((port) => Number.isFinite(port) && port > 0))];
  const results = await Promise.all(
    uniquePorts.map(async (port) => ((await probeAuxPort(port)) ? port : null)),
  );
  return results.filter((port): port is number => port != null).sort((a, b) => a - b);
}

export async function initStreamOrigins(streamAuxPorts?: number[]): Promise<void> {
  if (initPromise) {
    return initPromise;
  }

  initPromise = (async () => {
    const advertised = Array.isArray(streamAuxPorts) ? streamAuxPorts : [];
    const devCandidates =
      process.env.NODE_ENV === 'development' ? [DEV_STREAM_DIRECT_PORT] : [];
    const reachable = await probeStreamAuxPorts([...devCandidates, ...advertised]);
    window.__MM_STREAM_AUX_PORTS__ = reachable;
  })();

  return initPromise;
}

export function listStreamRequestOrigins(): string[] {
  const auxPorts = window.__MM_STREAM_AUX_PORTS__ ?? [];
  return buildOriginsFromAuxPorts(auxPorts);
}

export function listOrderedOriginsForFile(fileId: number): string[] {
  const all = listStreamRequestOrigins();
  const preferred = resolveStreamRequestOrigin(fileId);
  return [preferred, ...all.filter((origin) => origin !== preferred)];
}

export function resolveStreamRequestOrigin(fileId: number): string {
  const origins = listStreamRequestOrigins();
  if (!origins.length) {
    return pageOrigin();
  }
  const index = Math.abs(fileId) % origins.length;
  return origins[index] ?? origins[0];
}

export function resolveMediaPlaybackUrl(
  path: string,
  fileId: number,
  originIndex = 0,
): string {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return appendAuthToken(path);
  }
  const origins = listOrderedOriginsForFile(fileId);
  const origin = origins[originIndex] ?? origins[0] ?? pageOrigin();
  const relativePath = path.startsWith('/') ? path : `/${path}`;
  return appendAuthToken(`${origin}${relativePath}`);
}

/** Test helper: bypass async probe and set origins synchronously. */
export function setStreamAuxPortsForTest(auxPorts: number[]): void {
  window.__MM_STREAM_AUX_PORTS__ = auxPorts;
  initPromise = Promise.resolve();
}
