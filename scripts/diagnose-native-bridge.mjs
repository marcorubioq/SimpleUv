import { spawnSync } from 'node:child_process'

const positions = new Float32Array([
  -0.5, -0.5, 0.5, 0.5, -0.5, 0.5, 0.5, 0.5, 0.5, -0.5, 0.5, 0.5,
  0.5, -0.5, -0.5, -0.5, -0.5, -0.5, -0.5, 0.5, -0.5, 0.5, 0.5, -0.5,
])
const indices = new Uint32Array([
  0, 1, 2, 0, 2, 3, 1, 4, 7, 1, 7, 2, 4, 5, 6, 4, 6, 7,
  5, 0, 3, 5, 3, 6, 3, 2, 7, 3, 7, 6, 5, 4, 1, 5, 1, 0,
])
const header = Buffer.alloc(24)
header.writeUInt32LE(0x31565549, 0)
header.writeUInt32LE(1, 4)
header.writeUInt32LE(1, 8)
header.writeUInt32LE(1024, 12)
header.writeUInt32LE(4, 16)
header.writeUInt32LE(3, 20)
const meshHeader = Buffer.alloc(12)
meshHeader.writeUInt32LE(8, 0)
meshHeader.writeUInt32LE(36, 4)
meshHeader.writeUInt32LE(0, 8)
const input = Buffer.concat([
  header,
  meshHeader,
  Buffer.from(positions.buffer),
  Buffer.from(indices.buffer),
])

const execution = spawnSync('native/build/Release/uv_bridge.exe', [], { input, maxBuffer: 10_000_000 })
if (!execution.stdout.length) throw new Error(execution.stderr.toString() || 'Bridge returned no output')
const data = execution.stdout
let offset = 0
const u32 = () => { const value = data.readUInt32LE(offset); offset += 4; return value }
const i32 = () => { const value = data.readInt32LE(offset); offset += 4; return value }
const f32 = () => { const value = data.readFloatLE(offset); offset += 4; return value }
const f64 = () => { const value = data.readDoubleLE(offset); offset += 8; return value }

if (u32() !== 0x3156554f) throw new Error('Unexpected output magic')
const success = u32()
const errorLength = u32()
const stats = Array.from({ length: 7 }, u32)
f64(); f64()
const meshCount = u32()
const error = data.toString('utf8', offset, offset + errorLength)
offset += errorLength
if (!success) throw new Error(error)

const vertexCount = u32()
const indexCount = u32()
const chartCount = u32()
for (let vertex = 0; vertex < vertexCount; vertex += 1) {
  u32(); i32(); i32(); f32(); f32()
}
const outputIndices = Array.from({ length: indexCount }, u32)
const parents = Uint32Array.from({ length: vertexCount }, (_, index) => index)
const find = (vertex) => {
  while (parents[vertex] !== vertex) vertex = parents[vertex]
  return vertex
}
const union = (a, b) => {
  const rootA = find(a)
  const rootB = find(b)
  if (rootA !== rootB) parents[rootB] = rootA
}
for (let index = 0; index < outputIndices.length; index += 3) {
  union(outputIndices[index], outputIndices[index + 1])
  union(outputIndices[index + 1], outputIndices[index + 2])
}
const components = new Set(Array.from({ length: vertexCount }, (_, vertex) => find(vertex)))

if (indexCount !== 36 || outputIndices.some((index) => index >= vertexCount)) {
  throw new Error('Native bridge returned invalid output indices')
}
if (components.size !== chartCount) {
  throw new Error(`Expected ${chartCount} connected UV components, received ${components.size}`)
}

console.log(JSON.stringify({ meshCount, vertexCount, indexCount, chartCount, components: components.size, stats }, null, 2))
