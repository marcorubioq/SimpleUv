import type { NativeUvResult } from '../native/uvTypes'

export interface UvDrawData {
  facePositions: Float32Array
  faceCharts: Uint32Array
  edgePositions: Float32Array
  pointPositions: Float32Array
}

export interface UvIslandDrawData extends UvDrawData {
  chart: number
}

function pushUv(target: number[], uvs: Float32Array, vertexIndex: number, z: number) {
  target.push(uvs[vertexIndex * 2], uvs[vertexIndex * 2 + 1], z)
}

export function buildUvDrawData(result: NativeUvResult): UvDrawData {
  const facePositions: number[] = []
  const faceCharts: number[] = []
  const edgePositions: number[] = []
  const pointPositions: number[] = []
  let chartOffset = 0

  for (const mesh of result.meshes) {
    for (let vertex = 0; vertex < mesh.sourceVertices.length; vertex += 1) {
      pushUv(pointPositions, mesh.uvs, vertex, 0.03)
    }

    for (let offset = 0; offset + 2 < mesh.indices.length; offset += 3) {
      const triangle = [mesh.indices[offset], mesh.indices[offset + 1], mesh.indices[offset + 2]]
      const chart = chartOffset + Math.max(0, mesh.chartIndices[triangle[0]])

      for (const vertex of triangle) {
        pushUv(facePositions, mesh.uvs, vertex, 0)
        faceCharts.push(chart)
      }

      const edges = [
        [triangle[0], triangle[1]],
        [triangle[1], triangle[2]],
        [triangle[2], triangle[0]],
      ]
      for (const [first, second] of edges) {
        pushUv(edgePositions, mesh.uvs, first, 0.02)
        pushUv(edgePositions, mesh.uvs, second, 0.02)
      }
    }
    chartOffset += mesh.chartCount
  }

  return {
    facePositions: new Float32Array(facePositions),
    faceCharts: new Uint32Array(faceCharts),
    edgePositions: new Float32Array(edgePositions),
    pointPositions: new Float32Array(pointPositions),
  }
}

export function buildUvIslands(result: NativeUvResult): UvIslandDrawData[] {
  const output: UvIslandDrawData[] = []
  let nextIsland = 0

  for (const mesh of result.meshes) {
    const vertexCount = mesh.sourceVertices.length
    const parents = Uint32Array.from({ length: vertexCount }, (_, index) => index)

    function find(vertex: number): number {
      let root = vertex
      while (parents[root] !== root) root = parents[root]
      while (parents[vertex] !== vertex) {
        const parent = parents[vertex]
        parents[vertex] = root
        vertex = parent
      }
      return root
    }

    function union(first: number, second: number) {
      const firstRoot = find(first)
      const secondRoot = find(second)
      if (firstRoot !== secondRoot) parents[secondRoot] = firstRoot
    }

    for (let offset = 0; offset + 2 < mesh.indices.length; offset += 3) {
      const first = mesh.indices[offset]
      const second = mesh.indices[offset + 1]
      const third = mesh.indices[offset + 2]
      if (first >= vertexCount || second >= vertexCount || third >= vertexCount) continue
      union(first, second)
      union(second, third)
    }

    const islands = new Map<number, { chart: number; faces: number[]; charts: number[]; edges: number[]; points: number[] }>()
    for (let offset = 0; offset + 2 < mesh.indices.length; offset += 3) {
      const triangle = [mesh.indices[offset], mesh.indices[offset + 1], mesh.indices[offset + 2]]
      if (triangle.some((vertex) => vertex >= vertexCount)) continue

      const root = find(triangle[0])
      let island = islands.get(root)
      if (!island) {
        island = { chart: nextIsland, faces: [], charts: [], edges: [], points: [] }
        nextIsland += 1
        islands.set(root, island)
      }

      for (const vertex of triangle) {
        pushUv(island.faces, mesh.uvs, vertex, 0)
        pushUv(island.points, mesh.uvs, vertex, 0.03)
        island.charts.push(island.chart)
      }
      const edges = [
        [triangle[0], triangle[1]],
        [triangle[1], triangle[2]],
        [triangle[2], triangle[0]],
      ]
      for (const [first, second] of edges) {
        pushUv(island.edges, mesh.uvs, first, 0.02)
        pushUv(island.edges, mesh.uvs, second, 0.02)
      }
    }

    for (const island of islands.values()) {
      output.push({
        chart: island.chart,
        facePositions: new Float32Array(island.faces),
        faceCharts: new Uint32Array(island.charts),
        edgePositions: new Float32Array(island.edges),
        pointPositions: new Float32Array(island.points),
      })
    }
  }

  return output
}
