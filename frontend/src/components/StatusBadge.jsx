const STATUS_CLASS = {
  SUCCESS:      "ok",
  RUNNING:      "info",
  PENDING:      "warn",
  FAILED:       "err",
  COMPATIBLE:   "ok",
  BREAKING:     "err",
  INCOMPATIBLE: "err"
};

const STATUS_LABEL = {
  SUCCESS:      "Success",
  RUNNING:      "Running",
  PENDING:      "Pending",
  FAILED:       "Failed",
  COMPATIBLE:   "Compatible",
  BREAKING:     "Breaking",
  INCOMPATIBLE: "Incompatible"
};

export default function StatusBadge({ value }) {
  if (!value) {
    return <span className="badge">N/A</span>;
  }
  const normalized = String(value).toUpperCase();
  const cls = STATUS_CLASS[normalized] ?? "";
  const label = STATUS_LABEL[normalized] ?? value;
  return <span className={`badge${cls ? ` ${cls}` : ""}`}>{label}</span>;
}
