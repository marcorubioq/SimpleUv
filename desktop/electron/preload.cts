const { contextBridge, ipcRenderer } = require('electron') as typeof import('electron')

interface NativeUvRequest {
  meshes: Array<{
    positions: Float32Array
    normals: Float32Array | null
    indices: Uint32Array
  }>
  options: {
    resolution: number
    padding: number
    rotateCharts: boolean
    rotateChartsToAxis: boolean
    bruteForce: boolean
  }
}

contextBridge.exposeInMainWorld('simpleUv', {
  generateUv: (request: NativeUvRequest) => ipcRenderer.invoke('uv:generate', request),
})

