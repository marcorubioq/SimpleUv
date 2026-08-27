export interface MeshIssue {
  code: 'invalid-index' | 'degenerate-triangle' | 'non-manifold-edge' | 'missing-normals'
  message: string
  count: number
}

export interface MeshFace {
  vertices: [number, number, number]
  edges: [number, number, number]
}

export interface MeshEdge {
  vertexA: number
  vertexB: number
  faces: number[]
  seam: boolean
}

export interface MeshData {
  id: string
  name: string
  positions: Float32Array
  indices: Uint32Array
  normals: Float32Array | null
  uvs: Float32Array | null
  worldMatrix: Float32Array
  faces: MeshFace[]
  edges: MeshEdge[]
  issues: MeshIssue[]
}

export interface MeshDocument {
  meshes: MeshData[]
  summary: MeshSummary
}

export interface MeshSummary {
  meshes: number
  vertices: number
  triangles: number
  edges: number
  boundaryEdges: number
  nonManifoldEdges: number
  degenerateTriangles: number
  meshesWithUvs: number
  meshesWithoutNormals: number
}

