export default function ErrorNotice({ message, clear }) {
  if (!message) return null;

  return (
    <div className="notice error" role="alert">
      {message}
      <button type="button" onClick={clear} aria-label="Dismiss error">
        ×
      </button>
    </div>
  );
}
