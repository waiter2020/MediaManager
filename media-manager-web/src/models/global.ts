import { message } from 'antd';

export default () => {
    const connectSse = (clientId: string) => {
      const eventSource = new EventSource(`/api/v1/sse/events?clientId=${clientId}`);
      
      eventSource.addEventListener('scan-start', (e: any) => {
        message.info(e.data);
      });

      eventSource.addEventListener('scan-progress', (e: any) => {
         // Could dispatch to a global state to update a progress bar
         console.log('Progress:', e.data);
      });

      eventSource.addEventListener('scan-end', (e: any) => {
        message.success(e.data);
      });

      eventSource.addEventListener('file-added', (e: any) => {
        message.success(`New file added: ${e.data}`);
      });

      eventSource.onerror = (e) => {
        console.error('SSE Error:', e);
        eventSource.close();
        setTimeout(() => {
          // simple retry, caller can decide whether to reconnect
          connectSse(clientId);
        }, 5000);
      };

      return eventSource;
    };

    return { connectSse };
};
