import { BufferGeometry, Float32BufferAttribute, Group, Mesh } from 'three'
import { describe, expect, it } from 'vitest'
import { extractMeshDocument } from '../src/mesh/extractMeshData'

function sceneWithGeometry(positions: number[], indices?: number[]) {
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3))
  if (indices) geometry.setIndex(indices)
  const scene = new Group()
  scene.add(new Mesh(geometry))
  return scene
}

describe('extractMeshDocument', () => {
  it('copies indexed geometry and builds face-edge adjacency', () => {
    const scene = sceneWithGeometry(
      [0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0],
      [0, 1, 2, 0, 2, 3],
    )

    const document = extractMeshDocument(scene)

    expect(document.summary).toMatchObject({
      meshes: 1,
      vertices: 4,
      triangles: 2,
      edges: 5,
      boundaryEdges: 4,
      nonManifoldEdges: 0,
    })
    expect(document.meshes[0].positions).toBeInstanceOf(Float32Array)
    expect(document.meshes[0].indices).toBeInstanceOf(Uint32Array)
    expect(document.meshes[0].edges.find((edge) => edge.faces.length === 2)).toBeDefined()
  })

  it('creates sequential indices for non-indexed triangles', () => {
    const scene = sceneWithGeometry([0, 0, 0, 1, 0, 0, 0, 1, 0])

    expect(Array.from(extractMeshDocument(scene).meshes[0].indices)).toEqual([0, 1, 2])
  })

  it('reports degenerate and non-manifold geometry', () => {
    const degenerate = sceneWithGeometry([0, 0, 0, 1, 0, 0, 2, 0, 0], [0, 1, 2])
    expect(extractMeshDocument(degenerate).summary.degenerateTriangles).toBe(1)

    const nonManifold = sceneWithGeometry(
      [0, 0, 0, 1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1],
      [0, 1, 2, 1, 0, 3, 0, 1, 4],
    )
    expect(extractMeshDocument(nonManifold).summary.nonManifoldEdges).toBe(1)
  })

  it('rejects scenes without triangle meshes', () => {
    expect(() => extractMeshDocument(new Group())).toThrow('does not contain any non-empty triangle meshes')
  })
})
