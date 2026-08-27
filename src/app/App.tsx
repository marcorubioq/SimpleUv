import { Viewport3D } from '../viewport3d/Viewport3D'

export function App() {
  return (
    <main className="app-shell">
      <header className="titlebar">
        <div className="brand">SimpleUV</div>
        <nav aria-label="Main navigation">
          <button type="button">File</button>
          <button type="button">Edit</button>
          <button type="button">UV</button>
          <button type="button">Pack</button>
        </nav>
        <span className="milestone">Viewport bootstrap</span>
      </header>

      <section className="workspace">
        <div className="panel-heading">
          <span>VIEWPORT 3D</span>
          <span className="viewport-hint">Orbit · Pan · Zoom</span>
        </div>
        <Viewport3D />
      </section>

      <footer className="toolbar">
        <button className="primary-action" type="button" disabled>
          AUTO UV
        </button>
        <span>Bootstrap test scene · UV engine not connected</span>
      </footer>
    </main>
  )
}

