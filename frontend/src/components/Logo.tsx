import { Link } from "react-router-dom";

export function Logo({ link = false }: { link?: boolean }) {
  const content = (
    <>
      <svg className="logo-mark" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="9" fill="currentColor" />
        <path d="M9 12.5a2.5 2.5 0 0 1 2.5-2.5h6a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-6A2.5 2.5 0 0 1 9 19.5z" fill="var(--logo-fg)" />
        <path d="m21 14.5 3.2-2.1a.5.5 0 0 1 .8.4v6.4a.5.5 0 0 1-.8.4L21 17.5z" fill="var(--logo-fg)" />
      </svg>
      <span className="logo-text">SelfCall</span>
    </>
  );
  return link ? (
    <Link to="/" className="logo" aria-label="SelfCall — на главную">
      {content}
    </Link>
  ) : (
    <div className="logo">{content}</div>
  );
}
