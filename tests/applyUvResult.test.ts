import { describe, expect, it } from 'vitest'
import type { MeshData } from '../src/mesh/MeshData'
import { buildUvGeometry } from '../src/mesh/applyUvResult'
import type { NativeUvMeshResult } from '../src/native/uvTypes'
import { createCheckerMaterial } from '../src/viewport3d/checkerMaterial'

describe('buildUvGeometry', () => {
  it('duplicates 3D attributes using xatlas source-vertex mappings', () => {
    const mesh: MeshData = {
      id: 'triangle',
      name: 'Triangle',
      positions: new Float32Array([0, 0, 0, 1, 0, 0, 0, 1, 0]),
      normals: new Float32Array([0, 0, 1, 0, 0, 1, 0, 0, 1]),
      uvs: null,
      indices: new Uint32Array([0, 1, 2]),
      worldMatrix: new Float32Array(16),
      faces: [],
      edges: [],
      issues: [],
    }
    const result: NativeUvMeshResult = {
      sourceVertices: new Uint32Array([0, 1, 2, 0]),
      chartIndices: new Int32Array([0, 0, 0, 1]),
      atlasIndices: new Int32Array([0, 0, 0, 0]),
      uvs: new Float32Array([0, 0, 1, 0, 0, 1, 0.5, 0.5]),
      indices: new Uint32Array([0, 1, 2, 3, 1, 2]),
      chartCount: 2,
    }

    const geometry = buildUvGeometry(mesh, result)

    expect(geometry.getAttribute('position').count).toBe(4)
    expect(geometry.getAttribute('uv').count).toBe(4)
    expect(Array.from(geometry.getAttribute('position').array)).toEqual([
      0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0,
    ])
    expect(Array.from(geometry.index!.array)).toEqual([0, 1, 2, 3, 1, 2])
    geometry.dispose()
  })
})

describe('createCheckerMaterial', () => {
  it('creates an entirely procedural checker texture', () => {
    const material = createCheckerMaterial()

    expect(material.map).not.toBeNull()
    expect(material.map!.image.width).toBe(256)
    expect(material.map!.image.height).toBe(256)
    material.map!.dispose()
    material.dispose()
  })
})
