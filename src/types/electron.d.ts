import type { NativeUvRequest, NativeUvResult } from '../native/uvTypes'

declare global {
  interface Window {
    simpleUv: {
      generateUv(request: NativeUvRequest): Promise<NativeUvResult>
    }
  }
}

export {}
