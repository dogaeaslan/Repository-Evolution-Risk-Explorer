const foundations = [
  {
    label: "Local by default",
    detail: "Repository history stays on the machine running the analysis.",
  },
  {
    label: "Evidence before scores",
    detail: "Every risk contribution will remain traceable to raw observations.",
  },
  {
    label: "Recommendations, qualified",
    detail: "Findings prioritize investigation; they do not declare code defective.",
  },
] as const;

export function App() {
  return (
    <main>
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Foundation milestone</p>
        <h1 id="page-title">Repository Evolution Risk Explorer</h1>
        <p className="intro">
          Find the files that deserve attention, understand the evidence, and decide
          what to investigate next.
        </p>
        <div className="status" role="status">
          <span aria-hidden="true" />
          Application foundation ready
        </div>
      </section>

      <section className="principles" aria-labelledby="principles-title">
        <div>
          <p className="eyebrow">Analysis principles</p>
          <h2 id="principles-title">Designed for defensible conclusions</h2>
        </div>
        <ul>
          {foundations.map((foundation) => (
            <li key={foundation.label}>
              <h3>{foundation.label}</h3>
              <p>{foundation.detail}</p>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
