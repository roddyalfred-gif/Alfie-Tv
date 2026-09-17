import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import './adaptive.css';

export type AlfieViewportMode = 'phone' | 'tablet' | 'desktop' | 'tv';
export type AlfieInputMode = 'touch' | 'pointer' | 'remote';

export type AdaptiveShellProps = {
  children: ReactNode;
  navigation?: ReactNode;
  className?: string;
};

function viewportMode(width: number): AlfieViewportMode {
  if (width < 600) return 'phone';
  if (width < 1024) return 'tablet';
  if (width < 1600) return 'desktop';
  return 'tv';
}

function inputMode(): AlfieInputMode {
  if (typeof window === 'undefined') return 'pointer';
  if (window.matchMedia('(pointer: coarse)').matches) return 'touch';
  if (window.matchMedia('(hover: none)').matches) return 'touch';
  return 'pointer';
}

export function AdaptiveShell({ children, navigation, className = '' }: AdaptiveShellProps) {
  const [mode, setMode] = useState<AlfieViewportMode>(() =>
    typeof window === 'undefined' ? 'desktop' : viewportMode(window.innerWidth),
  );
  const [input, setInput] = useState<AlfieInputMode>(() => inputMode());

  useEffect(() => {
    const update = () => {
      setMode(viewportMode(window.innerWidth));
      setInput(inputMode());
    };
    update();
    window.addEventListener('resize', update, { passive: true });
    window.addEventListener('orientationchange', update, { passive: true });
    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('orientationchange', update);
    };
  }, []);

  return (
    <div
      className={`alfie-shell alfie-shell--${mode} alfie-shell--input-${input} ${className}`.trim()}
      data-alfie-mode={mode}
      data-alfie-input={input}
    >
      {navigation ? <aside className="alfie-shell__navigation">{navigation}</aside> : null}
      <main className="alfie-shell__content">{children}</main>
    </div>
  );
}

export type AdaptiveNavigationItem = {
  id: string;
  label: string;
  icon?: ReactNode;
};

export type AdaptiveNavigationProps = {
  items: AdaptiveNavigationItem[];
  activeId?: string;
  onSelect?: (id: string) => void;
};

export function AdaptiveNavigation({ items, activeId, onSelect }: AdaptiveNavigationProps) {
  return (
    <nav className="alfie-nav" aria-label="Main navigation">
      <div className="alfie-nav__brand" aria-label="Alfie TV">Alfie TV</div>
      <div className="alfie-nav__items">
        {items.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`alfie-nav__item${item.id === activeId ? ' is-active' : ''}`}
            aria-current={item.id === activeId ? 'page' : undefined}
            onClick={() => onSelect?.(item.id)}
          >
            {item.icon ? <span className="alfie-nav__icon" aria-hidden="true">{item.icon}</span> : null}
            <span>{item.label}</span>
          </button>
        ))}
      </div>
    </nav>
  );
}
