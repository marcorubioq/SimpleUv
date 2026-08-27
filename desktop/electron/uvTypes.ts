export interface NativeMeshRequest {
  positions: Float32Array
  normals: Float32Array | null
  indices: Uint32Array
}

export interface NativeUvRequest {
  meshes: NativeMeshRequest[]
  options: {
    resolution: number
    padding: number
    rotateCharts: boolean
    rotateChartsToAxis: boolean
    bruteForce: boolean
  }
}

export interface NativeUvMeshResult {
  sourceVertices: Uint32Array
  chartIndices: Int32Array
  atlasIndices: Int32Array
  uvs: Float32Array
  indices: Uint32Array
  chartCount: number
}

export interface NativeUvResult {
  success: boolean
  error: string
  meshes: NativeUvMeshResult[]
  inputVertices: number
  inputTriangles: number
  outputVertices: number
  chartCount: number
  atlasCount: number
  width: number
  height: number
  chartDurationMs: number
  packDurationMs: number
}
