import { OrbitControls } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'

function TestCube() {
  return (
    <mesh castShadow receiveShadow position={[0, 0.5, 0]}>
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial color="#7c9cff" metalness={0.08} roughness={0.32} />
    </mesh>
  )
}

export function Viewport3D() {
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
        <TestCube />
        <gridHelper args={[20, 20, '#555d6d', '#303641']} />
        <OrbitControls makeDefault enableDamping dampingFactor={0.08} target={[0, 0.5, 0]} />
      </Canvas>
    </div>
  )
}

