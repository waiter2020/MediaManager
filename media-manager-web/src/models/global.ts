import { useState, useCallback, useEffect, useRef } from 'react';
import { message } from 'antd';
import { authHeaders, getAccessToken } from '@/utils/authSession';

export interface ScanProgress {
  libraryId: number;
  libraryName: string;
  status: 'SCANNING' | 'DONE' | 'ERROR' | 'CANCELLED';
  currentPath: string;
  totalFiles: number;
  scannedFiles: number;
  matchedFiles: number;
  skippedFiles?: number;
  newItems: number;
  updatedItems?: number;
  restoredItems?: number;
  failedItems?: number;
  missingItems?: number;
  startedAt: number;
  updatedAt: number;
  recentErrors?: ScanError[];
}

export interface ScanError {
  path: string;
  message: string;
  timestamp: number;
}

export interface ScanEvent {
  time: string;
  type: string;
  message: string;
}

export interface ScrapeTaskEvent {
  taskId: number;
  status: string;
  scraped?: number;
  errors?: number;
  total?: number;
}

const MAX_EVENTS = 30;

const eventData = (event: Event) => (event as MessageEvent<string>).data;

export default () => {
  const [scanStatus, setScanStatus] = useState<Record<number, ScanProgress>>({});
  const [recentEvents, setRecentEvents] = useState<ScanEvent[]>([]);
  const [scrapeTasks, setScrapeTasks] = useState<Record<number, ScrapeTaskEvent>>({});
  const eventSourceRef = useRef<EventSource | null>(null);

  const addEvent = useCallback((type: string, msg: string) => {
    setRecentEvents((prev) =>
      [{ time: new Date().toLocaleTimeString(), type, message: msg }, ...prev].slice(0, MAX_EVENTS),
    );
  }, []);

  const connectSse = useCallback((clientId: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const token = getAccessToken();
    const tokenQ = token ? `&token=${encodeURIComponent(token)}` : '';
    const es = new EventSource(`/api/v1/sse/events?clientId=${clientId}${tokenQ}`);
    eventSourceRef.current = es;

    const handleScanProgressPayload = (raw: string) => {
      try {
        const data: ScanProgress = JSON.parse(raw);
        if (typeof data.libraryId !== 'number') return;
        setScanStatus((prev) => {
          if (data.status === 'DONE' || data.status === 'ERROR' || data.status === 'CANCELLED') {
            const next = { ...prev };
            delete next[data.libraryId];
            return next;
          }
          return { ...prev, [data.libraryId]: data };
        });
      } catch {
        addEvent('PROGRESS', raw);
      }
    };

    es.addEventListener('scan-status', (e) => handleScanProgressPayload(eventData(e)));
    es.addEventListener('scan.progress', (e) => handleScanProgressPayload(eventData(e)));

    es.addEventListener('scan-start', (e) => {
      const data = eventData(e);
      message.info(data);
      addEvent('START', data);
    });

    es.addEventListener('scan-progress', (e) => {
      addEvent('PROGRESS', eventData(e));
    });

    es.addEventListener('scan-end', (e) => {
      const data = eventData(e);
      message.success(data);
      addEvent('END', data);
    });

    es.addEventListener('file-added', (e) => {
      const data = eventData(e);
      message.success(`已加入新文件：${data}`);
      addEvent('FILE', data);
    });

    es.addEventListener('library.updated', (e) => {
      const raw = eventData(e);
      try {
        const data = JSON.parse(raw);
        addEvent('LIBRARY', `媒体库 #${data.libraryId} ${data.action}`);
      } catch {
        addEvent('LIBRARY', raw);
      }
    });

    es.addEventListener('scrape.task.updated', (e) => {
      try {
        const data: ScrapeTaskEvent = JSON.parse(eventData(e));
        const normalized: ScrapeTaskEvent = {
          ...data,
          status: data.status || (data.scraped != null || data.errors != null ? 'RUNNING' : ''),
        };
        setScrapeTasks((prev) => ({ ...prev, [normalized.taskId]: normalized }));
        addEvent('SCRAPE', `任务 #${normalized.taskId} -> ${normalized.status || 'RUNNING'}`);
        if (normalized.status === 'SUCCESS' || normalized.status === 'FAILED') {
          message.info(`刮削任务 #${normalized.taskId} 已结束：${normalized.status}`);
        }
      } catch {
        // ignore malformed payloads
      }
    });

    es.addEventListener('scrape-end', (e) => {
      addEvent('SCRAPE', eventData(e));
    });

    es.onerror = () => {
      es.close();
      eventSourceRef.current = null;
      setTimeout(() => connectSse(clientId), 5000);
    };

    return es;
  }, [addEvent]);

  const fetchScanSnapshot = useCallback(async () => {
    try {
      const res = await fetch('/api/v1/system/scan/status', {
        headers: authHeaders(),
      });
      const json = await res.json();
      if (json.code === 200 && Array.isArray(json.data)) {
        const map: Record<number, ScanProgress> = {};
        json.data.forEach((s: ScanProgress) => {
          if (s.status === 'SCANNING') {
            map[s.libraryId] = s;
          }
        });
        setScanStatus(map);
      }
    } catch {
      // Keep the shell usable when scan snapshot is temporarily unavailable.
    }
  }, []);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      fetchScanSnapshot();
      return undefined;
    }
    const clientId = 'global-' + Math.random().toString(36).substring(2, 9);
    const es = connectSse(clientId);
    fetchScanSnapshot();
    return () => {
      es.close();
      eventSourceRef.current = null;
    };
  }, [connectSse, fetchScanSnapshot]);

  return { scanStatus, recentEvents, scrapeTasks, connectSse, fetchScanSnapshot };
};
