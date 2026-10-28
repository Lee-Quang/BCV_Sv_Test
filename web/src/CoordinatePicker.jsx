import React, { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';

export default function CoordinatePicker({ latitude, longitude, change }) {
  const container = useRef(null), map = useRef(null), marker = useRef(null);
  const latest = useRef(change);
  latest.current = change;
  const [error, setError] = useState('');
  const valid = latitude !== '' && latitude != null && longitude !== '' && longitude != null &&
    Number.isFinite(Number(latitude)) && Number.isFinite(Number(longitude)) &&
    Math.abs(Number(latitude)) <= 90 && Math.abs(Number(longitude)) <= 180;
  useEffect(() => {
    if (!__GOONG_MAP_KEY__) { setError('Chưa cấu hình khóa bản đồ Goong. Bạn vẫn có thể nhập tọa độ bên dưới.'); return; }
    let instance, observer;
    try {
      instance = new maplibregl.Map({ container: container.current,
        style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${__GOONG_MAP_KEY__}`,
        center: valid ? [Number(longitude), Number(latitude)] : [105.8355, 21.0272], zoom: valid ? 14 : 12 });
      map.current = instance;
      instance.addControl(new maplibregl.NavigationControl(), 'top-right');
      const select = point => latest.current({ latitude: Number(point.lat.toFixed(6)), longitude: Number(point.lng.toFixed(6)) });
      marker.current = new maplibregl.Marker({ color: '#07836d', draggable: true });
      marker.current.on('dragend', () => select(marker.current.getLngLat()));
      instance.on('click', event => select(event.lngLat));
      instance.on('error', () => setError('Không tải được bản đồ. Kiểm tra kết nối hoặc nhập tọa độ bên dưới.'));
      instance.on('load', () => setError(''));
      observer = new ResizeObserver(() => instance.resize());
      observer.observe(container.current);
    } catch { setError('Không mở được bản đồ. Bạn vẫn có thể nhập tọa độ bên dưới.'); }
    return () => { observer?.disconnect(); marker.current?.remove(); instance?.remove(); map.current = null; };
  }, []);
  useEffect(() => {
    if (!map.current || !marker.current) return;
    if (!valid) { marker.current.remove(); return; }
    const point = [Number(longitude), Number(latitude)];
    marker.current.setLngLat(point).addTo(map.current);
    map.current.easeTo({ center: point, duration: 250 });
  }, [latitude, longitude, valid]);
  return <section className="coordinate-picker" aria-label="Chọn vị trí địa danh trên bản đồ">
    <strong>Vị trí trên bản đồ</strong>
    <p>Bấm trên bản đồ hoặc kéo ghim để chọn vị trí. Vĩ độ và kinh độ sẽ tự cập nhật.</p>
    <div ref={container} className="coordinate-map" />
    <small aria-live="polite">{valid ? `Đã chọn: ${latitude}, ${longitude}` : 'Chưa chọn vị trí.'}</small>
    {error && <p className="field-error" role="alert">{error}</p>}
  </section>;
}
