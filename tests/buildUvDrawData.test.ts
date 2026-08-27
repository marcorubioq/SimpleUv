import { describe, expect, it } from 'vitest'
import type { NativeUvResult } from '../src/native/uvTypes'
import { buildUvDrawData, buildUvIslands } from '../src/uv-editor/buildUvDrawData'

function resultFixture(): NativeUvResult {
  return {
    success: true,
    error: '',
    inputVertices: 3,
    inputTriangles: 1,
    outputVertices: 3,
    chartCount: 1,
    atlasCount: 1,
    width: 256,
    height: 256,
    chartDurationMs: 1,
    packDurationMs: 1,
    meshes: [{
      sourceVertices: new Uint32Array([0, 1, 2]),
      chartIndices: new Int32Array([0, 0, 0]),
      atlasIndices: new Int32Array([0, 0, 0]),
      uvs: new Float32Array([0.1, 0.2, 0.8, 0.2, 0.1, 0.9]),
      indices: new Uint32Array([0, 1, 2]),
      chartCount: 1,
    }],
  }
}

describe('buildUvDrawData', () => {
  it('expands indexed UV triangles into render buffers', () => {
    const drawData = buildUvDrawData(resultFixture())

    const expectedPositions = [0.1, 0.2, 0, 0.8, 0.2, 0, 0.1, 0.9, 0]
    expect(drawData.facePositions).toHaveLength(expectedPositions.length)
    expectedPositions.forEach((value, index) => {
      expect(drawData.facePositions[index]).toBeCloseTo(value, 6)
    })
    expect(Array.from(drawData.faceCharts)).toEqual([0, 0, 0])
    expect(drawData.edgePositions).toHaveLength(18)
    expect(drawData.pointPositions).toHaveLength(9)
  })

  it('creates an independent render batch for every chart', () => {
    const islands = buildUvIslands(resultFixture())

    expect(islands).toHaveLength(1)
    expect(islands[0].chart).toBe(0)
    expect(islands[0].facePositions).toHaveLength(9)
    expect(islands[0].edgePositions).toHaveLength(18)
  })

  it('derives islands from output connectivity instead of chart labels', () => {
    const result = resultFixture()
    result.meshes[0] = {
      sourceVertices: new Uint32Array([0, 1, 2, 3, 4, 5]),
      chartIndices: new Int32Array([0, 0, 0, 0, 0, 0]),
      atlasIndices: new Int32Array([0, 0, 0, 0, 0, 0]),
      uvs: new Float32Array([
        0, 0, 0.4, 0, 0, 0.4,
        0.6, 0.6, 1, 0.6, 1, 1,
      ]),
      indices: new Uint32Array([0, 1, 2, 3, 4, 5]),
      chartCount: 2,
    }

    expect(buildUvIslands(result)).toHaveLength(2)
  })
})
