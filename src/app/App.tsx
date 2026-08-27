import { useRef, useState } from 'react'
import { readModelFile, type ModelFile } from '../import/modelFile'
import { Viewport3D, type ViewportStatus } from '../viewport3d/Viewport3D'

export function App() {
  const fileInput = useRef<HTMLInputElement>(null)
  const [model, setModel] = useState<ModelFile | null>(null)
  const [status, setStatus] = useState<ViewportStatus>({ kind: 'idle' })
  const [isDragging, setIsDragging] = useState(false)

  async function openFile(file: File | undefined) {
    if (!file) return

    setStatus({ kind: 'loading', message: `Reading ${file.name}...` })

    try {
      const nextModel = await readModelFile(file)
      setModel(nextModel)
      setStatus({ kind: 'loading', message: `Loading ${file.name}...` })
    } catch (error) {
      setStatus({
        kind: 'error',
        message: error instanceof Error ? error.message : 'The model could not be opened.',
      })
    }
  }

  return (
    <main
      className={`app-shell${isDragging ? ' is-dragging' : ''}`}
      onDragEnter={(event) => {
        event.preventDefault()
        setIsDragging(true)
      }}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setIsDragging(false)
        }
      }}
      onDrop={(event) => {
        event.preventDefault()
        setIsDragging(false)
        void openFile(event.dataTransfer.files[0])
      }}
    >
      <header className="titlebar">
        <div className="brand">SimpleUV</div>
        <nav aria-label="Main navigation">
          <button type="button" onClick={() => fileInput.current?.click()}>
            Open
          </button>
          <button type="button">Edit</button>
          <button type="button">UV</button>
          <button type="button">Pack</button>
        </nav>
        <span className="milestone">{model?.name ?? 'No model loaded'}</span>
        <input
          ref={fileInput}
          className="visually-hidden"
          type="file"
          accept=".glb,.gltf,model/gltf-binary,model/gltf+json"
          onChange={(event) => {
            void openFile(event.target.files?.[0])
            event.currentTarget.value = ''
          }}
        />
      </header>

      <section className="workspace">
        <div className="panel-heading">
          <span>VIEWPORT 3D</span>
          <span className="viewport-hint">Orbit · Pan · Zoom</span>
        </div>
        <Viewport3D model={model} onStatusChange={setStatus} />
      </section>

      <footer className="toolbar">
        <button className="primary-action" type="button" disabled>
          AUTO UV
        </button>
        <span className={`status-message status-${status.kind}`}>
          {status.kind === 'idle' && 'Open or drop a GLB/GLTF model'}
          {status.kind !== 'idle' && status.message}
        </span>
      </footer>

      {isDragging && <div className="drop-overlay">Drop GLB or GLTF to open</div>}
    </main>
  )
}
