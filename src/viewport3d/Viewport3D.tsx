import { Bounds, OrbitControls } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'
import { useEffect, useMemo, useState } from 'react'
import type { Group, Material, Mesh } from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { ModelFile } from '../import/modelFile'
import { extractMeshDocument } from '../mesh/extractMeshData'
import type { MeshDocument } from '../mesh/MeshData'
import { applyUvResultToScene } from '../mesh/applyUvResult'
import type { NativeUvResult } from '../native/uvTypes'
import { createCheckerMaterial } from './checkerMaterial'

export type ViewportStatus =
  | { kind: 'idle' }
  | { kind: 'loading'; message: string }
  | { kind: 'ready'; message: string }
  | { kind: 'error'; message: string }

interface Viewport3DProps {
  model: ModelFile | null
  onStatusChange: (status: ViewportStatus) => void
  onMeshDataChange: (document: MeshDocument | null) => void
  uvResult: NativeUvResult | null
  checkerEnabled: boolean
}

function TestCube() {
  return (
    <mesh castShadow receiveShadow position={[0, 0.5, 0]}>
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial color="#7c9cff" metalness={0.08} roughness={0.32} />
    </mesh>
  )
}

function LoadedModel({ model, onStatusChange, onMeshDataChange, uvResult, checkerEnabled }: Viewport3DProps) {
  const [scene, setScene] = useState<Group | null>(null)
  const [document, setDocument] = useState<MeshDocument | null>(null)
  const checkerMaterial = useMemo(() => createCheckerMaterial(), [])

  useEffect(() => {
    let active = true
    const loader = new GLTFLoader()

    setScene(null)
    setDocument(null)
    onMeshDataChange(null)
    onStatusChange({ kind: 'loading', message: `Parsing ${model?.name ?? 'model'}...` })

    if (!model) return

    loader.parse(
      model.data,
      '',
      (gltf) => {
        if (!active) return
        try {
          const document = extractMeshDocument(gltf.scene)
          setScene(gltf.scene)
          setDocument(document)
          onMeshDataChange(document)
          onStatusChange({
            kind: 'ready',
            message: `${model.name}: ${document.summary.vertices.toLocaleString()} vertices, ${document.summary.triangles.toLocaleString()} triangles`,
          })
        } catch (error) {
          onStatusChange({
            kind: 'error',
            message: error instanceof Error ? error.message : `Could not analyze ${model.name}.`,
          })
        }
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
  }, [model, onMeshDataChange, onStatusChange])

  useEffect(() => {
    if (!scene || !document || !uvResult) return
    try {
      return applyUvResultToScene(scene, document, uvResult)
    } catch (error) {
      onStatusChange({
        kind: 'error',
        message: error instanceof Error ? error.message : 'Could not apply generated UVs to the model.',
      })
    }
  }, [document, onStatusChange, scene, uvResult])

  useEffect(() => {
    if (!scene) return
    const originals = new Map<Mesh, Material | Material[]>()
    scene.traverse((object) => {
      if (!('isMesh' in object) || object.isMesh !== true) return
      const mesh = object as Mesh
      originals.set(mesh, mesh.material)
      if (checkerEnabled) mesh.material = checkerMaterial
    })
    return () => {
      for (const [mesh, material] of originals) mesh.material = material
    }
  }, [checkerEnabled, checkerMaterial, scene])

  useEffect(() => () => {
    checkerMaterial.map?.dispose()
    checkerMaterial.dispose()
  }, [checkerMaterial])

  if (!scene) return null

  return <primitive object={scene} />
}

export function Viewport3D({ model, onStatusChange, onMeshDataChange, uvResult, checkerEnabled }: Viewport3DProps) {
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
            <LoadedModel
              model={model}
              onStatusChange={onStatusChange}
              onMeshDataChange={onMeshDataChange}
              uvResult={uvResult}
              checkerEnabled={checkerEnabled}
            />
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
