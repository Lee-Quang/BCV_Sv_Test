import { useCallback, useEffect, useRef, useState } from 'react';

// Keep the panel mounted during its CSS exit animation so closing does not jump.
export function useAnimatedDismiss(onDismiss) {
  const [closing, setClosing] = useState(false);
  const timer = useRef(null);
  useEffect(() => () => clearTimeout(timer.current), []);
  const dismiss = useCallback(() => {
    if (timer.current !== null) return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches) {
      onDismiss();
      return;
    }
    setClosing(true);
    timer.current = setTimeout(onDismiss, 180);
  }, [onDismiss]);
  return { closing, dismiss };
}
