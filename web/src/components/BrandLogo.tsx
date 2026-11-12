import React from 'react';

export type BrandLogoVariant =
  | 'horizontal'
  | 'square'
  | 'sidebar'
  | 'monochrome'
  | 'light'
  | 'dark';

export interface BrandLogoProps {
  variant?: BrandLogoVariant;
  collapsed?: boolean;
  size?: number;
  showTagline?: boolean;
  className?: string;
}

export const BRAND_TAGLINE = 'Move Trade Forward. Think Beyond Borders.';

export const FAVICON_SVG_DATA_URI =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40' fill='none'%3E%3Crect width='40' height='40' rx='9' fill='%23123D43'/%3E%3Cpath d='M10.5 29V11.5L23.5 26.5V14.5' stroke='%23F5F2EA' stroke-width='2.75' stroke-linecap='round' stroke-linejoin='round'/%3E%3Cpath d='M20 14.5H29.5V24' stroke='%23267C66' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3Cpath d='M21.5 22.5L29.5 14.5' stroke='%23267C66' stroke-width='2.5' stroke-linecap='round'/%3E%3Ccircle cx='10.5' cy='11.5' r='2.5' fill='%23D2AE6D'/%3E%3Ccircle cx='29.5' cy='14.5' r='2.5' fill='%23D2AE6D'/%3E%3Ccircle cx='23.5' cy='26.5' r='2' fill='%23F5F2EA'/%3E%3C/svg%3E";

export const NiryatSymbolMark: React.FC<{
  size?: number;
  mode?: 'default' | 'monochrome' | 'light' | 'dark';
  title?: string;
}> = ({ size = 36, mode = 'default', title = 'NiryatFlow AI Symbol' }) => {
  const bgFill =
    mode === 'monochrome'
      ? '#123D43'
      : mode === 'light'
      ? '#123D43'
      : mode === 'dark'
      ? '#194B52'
      : '#123D43';

  const primaryStroke = mode === 'monochrome' ? '#FFFFFF' : '#F5F2EA';
  const routeArrowStroke = mode === 'monochrome' ? '#FFFFFF' : '#267C66';
  const signalNodeFill = mode === 'monochrome' ? '#FFFFFF' : '#D2AE6D';
  const borderStroke =
    mode === 'dark' ? 'rgba(210, 174, 109, 0.35)' : 'rgba(255, 255, 255, 0.14)';

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={title}
      style={{ flexShrink: 0, display: 'block' }}
    >
      <rect
        x="0.75"
        y="0.75"
        width="38.5"
        height="38.5"
        rx="9.25"
        fill={bgFill}
        stroke={borderStroke}
        strokeWidth="1.2"
      />
      <path
        d="M7 31C14 28.5 25 28.5 33 31"
        stroke={primaryStroke}
        strokeOpacity="0.16"
        strokeWidth="1.25"
        strokeLinecap="round"
      />
      <path
        d="M11 28.5V12L23.5 26.5V15.5"
        stroke={primaryStroke}
        strokeWidth="2.65"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M21 12.5H29.5V21"
        stroke={routeArrowStroke}
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M23.5 18.5L29.5 12.5"
        stroke={routeArrowStroke}
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <circle cx="11" cy="12" r="2.4" fill={signalNodeFill} />
      <circle cx="29.5" cy="12.5" r="2.3" fill={signalNodeFill} />
      <circle cx="23.5" cy="26.5" r="1.9" fill={primaryStroke} />
    </svg>
  );
};

export const BrandLogo: React.FC<BrandLogoProps> = ({
  variant = 'horizontal',
  collapsed = false,
  size = 36,
  showTagline = false,
  className = ''
}) => {
  if (variant === 'square') {
    return <NiryatSymbolMark size={size} mode="default" title="NiryatFlow AI Icon" />;
  }

  if (variant === 'monochrome') {
    return (
      <div
        className={`nf-brand-lockup ${className}`}
        style={{ display: 'inline-flex', alignItems: 'center', gap: '0.68rem' }}
      >
        <NiryatSymbolMark size={size} mode="monochrome" title="NiryatFlow AI Monochrome Logo" />
        {!collapsed && (
          <div style={{ lineHeight: 1.15 }}>
            <div
              style={{
                fontFamily: 'var(--font-display)',
                fontWeight: 700,
                fontSize: '1.02rem',
                letterSpacing: '-0.02em',
                color: 'currentColor'
              }}
            >
              NiryatFlow <span style={{ fontWeight: 600, opacity: 0.78 }}>AI</span>
            </div>
            {showTagline && (
              <div style={{ fontSize: '0.68rem', opacity: 0.7, marginTop: '0.12rem' }}>
                {BRAND_TAGLINE}
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  const isDarkSurface = variant === 'dark' || variant === 'sidebar';
  const wordmarkColor = isDarkSurface ? '#F5F2EA' : '#123D43';
  const aiAccentColor = isDarkSurface ? '#D2AE6D' : '#267C66';
  const subtitleColor = isDarkSurface ? 'rgba(245, 242, 234, 0.68)' : '#73817F';

  return (
    <div
      className={`nf-brand-lockup ${className}`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: collapsed ? '0' : '0.72rem',
        justifyContent: collapsed ? 'center' : 'flex-start'
      }}
    >
      <NiryatSymbolMark
        size={size}
        mode={isDarkSurface ? 'dark' : 'light'}
        title="NiryatFlow AI Trade Intelligence"
      />
      {!collapsed && (
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'baseline',
              gap: '0.32rem',
              fontFamily: 'var(--font-display)',
              fontWeight: 700,
              fontSize: variant === 'sidebar' ? '1.04rem' : '1.12rem',
              letterSpacing: '-0.02em',
              color: wordmarkColor,
              lineHeight: 1.15
            }}
          >
            <span>NiryatFlow</span>
            <span
              style={{
                color: aiAccentColor,
                fontSize: '0.82em',
                fontWeight: 700,
                letterSpacing: '0.04em'
              }}
            >
              AI
            </span>
          </div>
          <div
            style={{
              fontSize: '0.66rem',
              fontWeight: 600,
              letterSpacing: '0.03em',
              color: subtitleColor,
              marginTop: '0.14rem',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis'
            }}
          >
            {showTagline ? BRAND_TAGLINE : 'GLOBAL TRADE & TREASURY OS'}
          </div>
        </div>
      )}
    </div>
  );
};

export default BrandLogo;
