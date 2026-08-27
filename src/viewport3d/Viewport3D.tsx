import { Bounds, OrbitControls } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'
import { useEffect, useState } from 'react'
import type { Group } from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { ModelFile } from '../import/modelFile'

export type ViewportStatus =
  | { kind: 'idle' }
  | { kind: 'loading'; message: string }
  | { kind: 'ready'; message: string }
  | { kind: 'error'; message: string }

interface Viewport3DProps {
  model: ModelFile | null
  onStatusChange: (status: ViewportStatus) => void
}

function TestCube() {
  return (
    <mesh castShadow receiveShadow position={[0, 0.5, 0]}>
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial color="#7c9cff" metalness={0.08} roughness={0.32} />
    </mesh>
  )
}

function LoadedModel({ model, onStatusChange }: Viewport3DProps) {
  const [scene, setScene] = useState<Group | null>(null)

  useEffect(() => {
    let active = true
    const loader = new GLTFLoader()

    setScene(null)
    onStatusChange({ kind: 'loading', message: `Parsing ${model?.name ?? 'model'}...` })

    if (!model) return

    loader.parse(
      model.data,
      '',
      (gltf) => {
        if (!active) return
        setScene(gltf.scene)
        onStatusChange({ kind: 'ready', message: `${model.name} loaded successfully` })
      },
      (error) => {
        if (!active) return
        console.error('Model loading failed', error)
        onStatusChange({ kind: 'error', message: `Could not load ${model.name}. The file may be invalid or incomplete.` })
      },
    )

    return () => {
      active = false
    }
  }, [model, onStatusChange])

  if (!scene) return null

  return <primitive object={scene} />
}

export function Viewport3D({ model, onStatusChange }: Viewport3DProps) {
  return (
    <div className="viewport-canvas" data-testid="viewport-3d">
      <Canvas
        camera={{ position: [3.5, 2.8, 4.5], fov: 45, near: 0.1, far: 1000 }}
        dpr={[1, 2]}
        gl={{ antialias: true }}
        shadows
      >
        <color attach="background" args={['#171a20']} />
        <ambientLight intensity={0.7} />
        <directionalLight
          castShadow
          intensity={2.4}
          position={[4, 7, 5]}
          shadow-mapSize={[1024, 1024]}
        />
        <hemisphereLight args={['#dbe7ff', '#20242d', 0.8]} />
        {model ? (
          <Bounds fit clip observe margin={1.25}>
            <LoadedModel model={model} onStatusChange={onStatusChange} />
          </Bounds>
        ) : (
          <TestCube />
        )}
        <gridHelper args={[20, 20, '#555d6d', '#303641']} />
        <OrbitControls makeDefault enableDamping dampingFactor={0.08} target={[0, 0.5, 0]} />
      </Canvas>
    </div>
  )
}
