import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const positions = new Float32Array([
  -0.5, -0.5, 0.5, 0.5, -0.5, 0.5, 0.5, 0.5, 0.5, -0.5, 0.5, 0.5,
  0.5, -0.5, -0.5, -0.5, -0.5, -0.5, -0.5, 0.5, -0.5, 0.5, 0.5, -0.5,
])
const indices = new Uint16Array([
  0, 1, 2, 0, 2, 3, 1, 4, 7, 1, 7, 2, 4, 5, 6, 4, 6, 7,
  5, 0, 3, 5, 3, 6, 3, 2, 7, 3, 7, 6, 5, 4, 1, 5, 1, 0,
])

const positionBytes = Buffer.from(positions.buffer)
const indexBytes = Buffer.from(indices.buffer)
const binaryChunk = Buffer.concat([positionBytes, indexBytes])
const binaryPadding = Buffer.alloc((4 - (binaryChunk.length % 4)) % 4)
const binary = Buffer.concat([binaryChunk, binaryPadding])

const document = {
  asset: { version: '2.0', generator: 'SimpleUV test model generator' },
  scene: 0,
  scenes: [{ nodes: [0] }],
  nodes: [{ mesh: 0, name: 'Test Cube' }],
  meshes: [{ primitives: [{ attributes: { POSITION: 0 }, indices: 1 }] }],
  buffers: [{ byteLength: binaryChunk.length }],
  bufferViews: [
    { buffer: 0, byteOffset: 0, byteLength: positionBytes.length, target: 34962 },
    { buffer: 0, byteOffset: positionBytes.length, byteLength: indexBytes.length, target: 34963 },
  ],
  accessors: [
    {
      bufferView: 0,
      componentType: 5126,
      count: positions.length / 3,
      type: 'VEC3',
      min: [-0.5, -0.5, -0.5],
      max: [0.5, 0.5, 0.5],
    },
    { bufferView: 1, componentType: 5123, count: indices.length, type: 'SCALAR' },
  ],
}

const jsonSource = JSON.stringify(document)
const jsonPadding = ' '.repeat((4 - (Buffer.byteLength(jsonSource) % 4)) % 4)
const json = Buffer.from(jsonSource + jsonPadding)
const header = Buffer.alloc(12)
const jsonHeader = Buffer.alloc(8)
const binaryHeader = Buffer.alloc(8)
const totalLength = header.length + jsonHeader.length + json.length + binaryHeader.length + binary.length

header.writeUInt32LE(0x46546c67, 0)
header.writeUInt32LE(2, 4)
header.writeUInt32LE(totalLength, 8)
jsonHeader.writeUInt32LE(json.length, 0)
jsonHeader.writeUInt32LE(0x4e4f534a, 4)
binaryHeader.writeUInt32LE(binary.length, 0)
binaryHeader.writeUInt32LE(0x004e4942, 4)

const outputDirectory = path.resolve('test_models')
await mkdir(outputDirectory, { recursive: true })
await writeFile(path.join(outputDirectory, '01_cube.glb'), Buffer.concat([
  header,
  jsonHeader,
  json,
  binaryHeader,
  binary,
]))

console.log('Generated test_models/01_cube.glb')

