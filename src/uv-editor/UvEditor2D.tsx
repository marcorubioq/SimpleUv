import { OrbitControls, OrthographicCamera } from '@react-three/drei'
import { Canvas, useThree } from '@react-three/fiber'
import { useEffect, useMemo } from 'react'
import { BufferAttribute, BufferGeometry, Color, DoubleSide } from 'three'
import type { NativeUvResult } from '../native/uvTypes'
import { buildUvIslands, type UvIslandDrawData } from './buildUvDrawData'

interface UvEditor2DProps {
  result: NativeUvResult | null
}

function UvCamera() {
  const { width, height } = useThree((state) => state.size)
  const aspect = width / Math.max(height, 1)
  const verticalSpan = aspect >= 1 ? 1.2 : 1.2 / aspect
  const horizontalSpan = aspect >= 1 ? 1.2 * aspect : 1.2

  return (
    <OrthographicCamera
      makeDefault
      position={[0.5, 0.5, 10]}
      left={-horizontalSpan / 2}
      right={horizontalSpan / 2}
      top={verticalSpan / 2}
      bottom={-verticalSpan / 2}
      near={0.1}
      far={100}
    />
  )
}

function chartColor(chart: number): Color {
  return new Color().setHSL((chart * 0.61803398875) % 1, 0.62, 0.32)
}

function UvIsland({ drawData }: { drawData: UvIslandDrawData }) {
  const geometries = useMemo(() => {
    const faces = new BufferGeometry()
    faces.setAttribute('position', new BufferAttribute(drawData.facePositions, 3))
    const edges = new BufferGeometry()
    edges.setAttribute('position', new BufferAttribute(drawData.edgePositions, 3))
    const points = new BufferGeometry()
    points.setAttribute('position', new BufferAttribute(drawData.pointPositions, 3))
    return { faces, edges, points }
  }, [drawData])

  useEffect(() => () => {
    geometries.faces.dispose()
    geometries.edges.dispose()
    geometries.points.dispose()
  }, [geometries])

  return (
    <>
      <mesh geometry={geometries.faces} renderOrder={1} frustumCulled={false}>
        <meshBasicMaterial
          color={chartColor(drawData.chart)}
          side={DoubleSide}
          depthTest={false}
          depthWrite={false}
        />
      </mesh>
      <lineSegments geometry={geometries.edges} renderOrder={2} frustumCulled={false}>
        <lineBasicMaterial color="#d9deea" transparent opacity={0.82} depthTest={false} depthWrite={false} />
      </lineSegments>
      <points geometry={geometries.points} renderOrder={3} frustumCulled={false}>
        <pointsMaterial color="#ffffff" size={3} sizeAttenuation={false} depthTest={false} depthWrite={false} />
      </points>
    </>
  )
}

function UvGeometry({ result }: { result: NativeUvResult }) {
  const islands = useMemo(() => buildUvIslands(result), [result])
  return <>{islands.map((island) => <UvIsland key={island.chart} drawData={island} />)}</>
}

const unitSquare = new Float32Array([
  0, 0, 0.04, 1, 0, 0.04,
  1, 0, 0.04, 1, 1, 0.04,
  1, 1, 0.04, 0, 1, 0.04,
  0, 1, 0.04, 0, 0, 0.04,
])

export function UvEditor2D({ result }: UvEditor2DProps) {
  return (
    <div className="uv-canvas" data-testid="uv-editor-2d">
      <Canvas dpr={[1, 2]} gl={{ antialias: true }}>
        <color attach="background" args={['#15181e']} />
        <UvCamera />
        <gridHelper args={[10, 10, '#313845', '#242a33']} rotation={[Math.PI / 2, 0, 0]} position={[0.5, 0.5, -0.03]} />
        <lineSegments renderOrder={4}>
          <bufferGeometry>
            <bufferAttribute attach="attributes-position" args={[unitSquare, 3]} />
          </bufferGeometry>
          <lineBasicMaterial color="#829dff" depthTest={false} depthWrite={false} />
        </lineSegments>
        {result && <UvGeometry result={result} />}
        <OrbitControls
          makeDefault
          enableRotate={false}
          enableDamping
          dampingFactor={0.1}
          screenSpacePanning
          target={[0.5, 0.5, 0]}
        />
      </Canvas>
      {!result && <div className="uv-empty-state">Run AUTO UV to generate islands</div>}
    </div>
  )
}
