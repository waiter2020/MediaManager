import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  CloseOutlined,
  RotateLeftOutlined,
  RotateRightOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import './index.css';

interface ImageViewerProps {
  /** 图片 URL */
  src: string;
  /** 图片标题（用于辅助信息展示） */
  title?: string;
  /** 是否可见 */
  visible: boolean;
  /** 关闭回调 */
  onClose: () => void;
}

const ZOOM_STEP = 0.25;
const ZOOM_MIN = 0.1;
const ZOOM_MAX = 5;
const ROTATE_STEP = 90;

const ImageViewer: React.FC<ImageViewerProps> = ({ src, title, visible, onClose }) => {
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [dimensions, setDimensions] = useState<{ width: number; height: number } | null>(null);
  const imgRef = useRef<HTMLImageElement>(null);

  // 重置状态
  useEffect(() => {
    if (visible) {
      setZoom(1);
      setRotation(0);
      setDimensions(null);
    }
  }, [visible, src]);

  // Escape 键关闭
  useEffect(() => {
    if (!visible) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      } else if (e.key === '+' || e.key === '=') {
        setZoom((z) => Math.min(z + ZOOM_STEP, ZOOM_MAX));
      } else if (e.key === '-') {
        setZoom((z) => Math.max(z - ZOOM_STEP, ZOOM_MIN));
      } else if (e.key === '0') {
        setZoom(1);
        setRotation(0);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [visible, onClose]);

  const handleImageLoad = useCallback(() => {
    if (imgRef.current) {
      setDimensions({
        width: imgRef.current.naturalWidth,
        height: imgRef.current.naturalHeight,
      });
    }
  }, []);

  const handleZoomIn = useCallback(() => {
    setZoom((z) => Math.min(z + ZOOM_STEP, ZOOM_MAX));
  }, []);

  const handleZoomOut = useCallback(() => {
    setZoom((z) => Math.max(z - ZOOM_STEP, ZOOM_MIN));
  }, []);

  const handleZoomReset = useCallback(() => {
    setZoom(1);
    setRotation(0);
  }, []);

  const handleRotateLeft = useCallback(() => {
    setRotation((r) => r - ROTATE_STEP);
  }, []);

  const handleRotateRight = useCallback(() => {
    setRotation((r) => r + ROTATE_STEP);
  }, []);

  const handleBackdropClick = useCallback(
    (e: React.MouseEvent) => {
      if (e.target === e.currentTarget) {
        onClose();
      }
    },
    [onClose],
  );

  // 鼠标滚轮缩放
  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    setZoom((z) => {
      const delta = e.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP;
      return Math.min(Math.max(z + delta, ZOOM_MIN), ZOOM_MAX);
    });
  }, []);

  if (!visible) return null;

  const zoomPercent = Math.round(zoom * 100);

  return (
    <div className="image-viewer-backdrop" onClick={handleBackdropClick}>
      <div className="image-viewer-toolbar">
        <div className="image-viewer-toolbar-group">
          <button
            className="image-viewer-btn"
            onClick={handleZoomOut}
            title="缩小"
            aria-label="缩小"
          >
            <ZoomOutOutlined />
          </button>
          <span className="image-viewer-zoom-text" onClick={handleZoomReset} title="重置缩放">
            {zoomPercent}%
          </span>
          <button
            className="image-viewer-btn"
            onClick={handleZoomIn}
            title="放大"
            aria-label="放大"
          >
            <ZoomInOutlined />
          </button>
        </div>
        <div className="image-viewer-toolbar-divider" />
        <div className="image-viewer-toolbar-group">
          <button
            className="image-viewer-btn"
            onClick={handleRotateLeft}
            title="向左旋转"
            aria-label="向左旋转"
          >
            <RotateLeftOutlined />
          </button>
          <button
            className="image-viewer-btn"
            onClick={handleRotateRight}
            title="向右旋转"
            aria-label="向右旋转"
          >
            <RotateRightOutlined />
          </button>
        </div>
        <div className="image-viewer-toolbar-divider" />
        {dimensions && (
          <span className="image-viewer-dimensions">
            {dimensions.width} × {dimensions.height}
          </span>
        )}
        {title && <span className="image-viewer-title">{title}</span>}
        <button
          className="image-viewer-btn image-viewer-close"
          onClick={onClose}
          title="关闭 (Esc)"
          aria-label="关闭"
        >
          <CloseOutlined />
        </button>
      </div>

      <div className="image-viewer-content" onWheel={handleWheel}>
        <img
          ref={imgRef}
          src={src}
          alt={title || '图片预览'}
          className="image-viewer-image"
          style={{
            transform: `scale(${zoom}) rotate(${rotation}deg)`,
          }}
          onLoad={handleImageLoad}
          draggable={false}
        />
      </div>
    </div>
  );
};

export default ImageViewer;
