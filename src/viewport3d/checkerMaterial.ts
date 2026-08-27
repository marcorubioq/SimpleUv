import {
  DataTexture,
  LinearMipmapLinearFilter,
  MeshStandardMaterial,
  NearestFilter,
  RGBAFormat,
  SRGBColorSpace,
  UnsignedByteType,
} from 'three'

export function createCheckerMaterial(): MeshStandardMaterial {
  const size = 256
  const cells = 16
  const cellSize = size / cells
  const data = new Uint8Array(size * size * 4)

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const cellX = Math.floor(x / cellSize)
      const cellY = Math.floor(y / cellSize)
      const light = (cellX + cellY) % 2 === 0
      const majorLine = x % (cellSize * 4) < 2 || y % (cellSize * 4) < 2
      const offset = (y * size + x) * 4
      const value = majorLine ? 84 : light ? 218 : 55
      data[offset] = majorLine ? 86 : value
      data[offset + 1] = majorLine ? 125 : value
      data[offset + 2] = majorLine ? 224 : value
      data[offset + 3] = 255
    }
  }

  const texture = new DataTexture(data, size, size, RGBAFormat, UnsignedByteType)
  texture.colorSpace = SRGBColorSpace
  texture.magFilter = NearestFilter
  texture.minFilter = LinearMipmapLinearFilter
  texture.generateMipmaps = true
  texture.needsUpdate = true

  return new MeshStandardMaterial({
    name: 'SimpleUV Procedural Checker',
    map: texture,
    metalness: 0,
    roughness: 0.72,
  })
}
