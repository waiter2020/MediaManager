import { useState, useCallback, useEffect, useRef } from 'react';
import { message } from 'antd';

export interface ScanProgress {
  libraryId: number;
  libraryName: string;
  status: 'SCANNING' | 'DONE' | 'ERROR';
  currentPath: string;
  totalFiles: number;
  scannedFiles: number;
  matchedFiles: number;
  newItems: number;
  startedAt: number;
  updatedAt: number;
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
}

const MAX_EVENTS = 30;

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

    const token = localStorage.getItem('accessToken');
    const tokenQ = token ? `&token=${encodeURIComponent(token)}` : '';
    const es = new EventSource(`/api/v1/sse/events?clientId=${clientId}${tokenQ}`);
    eventSourceRef.current = es;

    es.addEventListener('scan-status', (e: any) => {
      try {
        const data: ScanProgress = JSON.parse(e.data);
        setScanStatus((prev) => {
          if (data.status === 'DONE' || data.status === 'ERROR') {
            const next = { ...prev };
            delete next[data.libraryId];
            return next;
          }
          return { ...prev, [data.libraryId]: data };
        });
      } catch {
        // ignore malformed payloads
      }
    });

    es.addEventListener('scan-start', (e: any) => {
      message.info(e.data);
      addEvent('START', e.data);
    });

    es.addEventListener('scan-progress', (e: any) => {
      addEvent('PROGRESS', e.data);
    });

    es.addEventListener('scan-end', (e: any) => {
      message.success(e.data);
      addEvent('END', e.data);
    });

    es.addEventListener('file-added', (e: any) => {
      message.success(`New file added: ${e.data}`);
      addEvent('FILE', e.data);
    });

    es.addEventListener('scrape.task.updated', (e: any) => {
      try {
        const data: ScrapeTaskEvent = JSON.parse(e.data);
        setScrapeTasks((prev) => ({ ...prev, [data.taskId]: data }));
        addEvent('SCRAPE', `任务 #${data.taskId} → ${data.status}`);
        if (data.status === 'SUCCESS' || data.status === 'FAILED') {
          message.info(`刮削任务 #${data.taskId} 已结束：${data.status}`);
        }
      } catch {
        // ignore malformed payloads
      }
    });

    es.addEventListener('scrape-end', (e: any) => {
      addEvent('SCRAPE', e.data);
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
      const token = localStorage.getItem('accessToken');
      const res = await fetch('/api/v1/system/scan/status', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
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
      // silently fail
    }
  }, []);

  useEffect(() => {
    const clientId = 'global-' + Math.random().toString(36).substring(2, 9);
    const es = connectSse(clientId);
    fetchScanSnapshot();
    return () => {
      es.close();
      eventSourceRef.current = null;
    };
  }, []);

  return { scanStatus, recentEvents, scrapeTasks, connectSse, fetchScanSnapshot };
};
