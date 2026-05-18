export default function Panel({ title, description, children, actions }) {
  return (
    <section className="panel">
      {(title || actions) && (
        <div className="panel-header">
          <div className="panel-header-text">
            {title && <h2 className="panel-title">{title}</h2>}
            {description && <p className="panel-desc">{description}</p>}
          </div>
          {actions && <div className="panel-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}
