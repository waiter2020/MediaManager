import React from 'react';
import { Button } from 'antd';
import { ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import './index.css';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  /** Optional fallback UI to render instead of the default error page */
  fallback?: React.ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
  showDetails: boolean;
}

/**
 * React 错误边界组件。
 * 捕获子组件树中的 JavaScript 错误，展示友好的回退界面，
 * 并提供"重试"按钮和可展开的错误详情。
 */
class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      showDetails: false,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    this.setState({ errorInfo });
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary] 捕获到渲染错误:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      showDetails: false,
    });
  };

  toggleDetails = () => {
    this.setState((prev) => ({ showDetails: !prev.showDetails }));
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      const { error, errorInfo, showDetails } = this.state;

      return (
        <div className="error-boundary-container">
          <div className="error-boundary-card">
            <div className="error-boundary-icon">
              <WarningOutlined />
            </div>
            <h2 className="error-boundary-title">页面出现了问题</h2>
            <p className="error-boundary-message">
              很抱歉，当前页面遇到了意外错误。您可以尝试重新加载此页面。
            </p>
            <div className="error-boundary-actions">
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                size="large"
                onClick={this.handleRetry}
              >
                重试
              </Button>
              <Button size="large" onClick={() => window.location.reload()}>
                刷新页面
              </Button>
            </div>
            {error && (
              <div className="error-boundary-details-section">
                <button
                  className="error-boundary-details-toggle"
                  onClick={this.toggleDetails}
                  type="button"
                >
                  {showDetails ? '收起错误详情 ▲' : '查看错误详情 ▼'}
                </button>
                {showDetails && (
                  <div className="error-boundary-details">
                    <div className="error-boundary-error-name">
                      {error.name}: {error.message}
                    </div>
                    {errorInfo?.componentStack && (
                      <pre className="error-boundary-stack">
                        {errorInfo.componentStack}
                      </pre>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
