import { app } from 'electron'
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import type { NativeUvMeshResult, NativeUvRequest, NativeUvResult } from './uvTypes.js'

const INPUT_MAGIC = 0x31565549
const OUTPUT_MAGIC = 0x3156554f
const PROTOCOL_VERSION = 1
const HEADER_BYTES = 24
const PROCESS_TIMEOUT_MS = 5 * 60 * 1000

function assertRequest(request: NativeUvRequest): void {
  if (!request || !Array.isArray(request.meshes) || request.meshes.length === 0) {
    throw new Error('At least one mesh is required.')
  }
  if (!Number.isInteger(request.options.resolution) || request.options.resolution <= 0) {
    throw new Error('Resolution must be a positive integer.')
  }
  if (!Number.isInteger(request.options.padding) || request.options.padding < 0) {
    throw new Error('Padding must be a non-negative integer.')
  }

  for (const mesh of request.meshes) {
    if (!(mesh.positions instanceof Float32Array) || mesh.positions.length === 0 || mesh.positions.length % 3 !== 0) {
      throw new Error('Mesh positions must be a non-empty Float32Array of xyz values.')
    }
    if (!(mesh.indices instanceof Uint32Array) || mesh.indices.length === 0 || mesh.indices.length % 3 !== 0) {
      throw new Error('Mesh indices must be a non-empty Uint32Array of triangles.')
    }
    if (mesh.normals !== null && (!(mesh.normals instanceof Float32Array) || mesh.normals.length !== mesh.positions.length)) {
      throw new Error('Mesh normals must be null or match the position buffer.')
    }
  }
}

function typedArrayBuffer(array: Float32Array | Uint32Array): Buffer {
  return Buffer.from(array.buffer, array.byteOffset, array.byteLength)
}

export function serializeRequest(request: NativeUvRequest): Buffer {
  assertRequest(request)
  const header = Buffer.allocUnsafe(HEADER_BYTES)
  let flags = 0
  if (request.options.rotateCharts) flags |= 1
  if (request.options.rotateChartsToAxis) flags |= 2
  if (request.options.bruteForce) flags |= 4

  header.writeUInt32LE(INPUT_MAGIC, 0)
  header.writeUInt32LE(PROTOCOL_VERSION, 4)
  header.writeUInt32LE(request.meshes.length, 8)
  header.writeUInt32LE(request.options.resolution, 12)
  header.writeUInt32LE(request.options.padding, 16)
  header.writeUInt32LE(flags, 20)

  const chunks: Buffer[] = [header]
  for (const mesh of request.meshes) {
    const meshHeader = Buffer.allocUnsafe(12)
    meshHeader.writeUInt32LE(mesh.positions.length / 3, 0)
    meshHeader.writeUInt32LE(mesh.indices.length, 4)
    meshHeader.writeUInt32LE(mesh.normals ? 1 : 0, 8)
    chunks.push(meshHeader, typedArrayBuffer(mesh.positions))
    if (mesh.normals) chunks.push(typedArrayBuffer(mesh.normals))
    chunks.push(typedArrayBuffer(mesh.indices))
  }
  return Buffer.concat(chunks)
}

class BufferReader {
  private offset = 0

  constructor(private readonly data: Buffer) {}

  private ensure(bytes: number): void {
    if (this.offset + bytes > this.data.length) throw new Error('Native bridge returned truncated data.')
  }

  uint32(): number {
    this.ensure(4)
    const value = this.data.readUInt32LE(this.offset)
    this.offset += 4
    return value
  }

  int32(): number {
    this.ensure(4)
    const value = this.data.readInt32LE(this.offset)
    this.offset += 4
    return value
  }

  float32(): number {
    this.ensure(4)
    const value = this.data.readFloatLE(this.offset)
    this.offset += 4
    return value
  }

  float64(): number {
    this.ensure(8)
    const value = this.data.readDoubleLE(this.offset)
    this.offset += 8
    return value
  }

  string(bytes: number): string {
    this.ensure(bytes)
    const value = this.data.toString('utf8', this.offset, this.offset + bytes)
    this.offset += bytes
    return value
  }
}

export function parseResult(data: Buffer): NativeUvResult {
  const reader = new BufferReader(data)
  if (reader.uint32() !== OUTPUT_MAGIC) throw new Error('Native bridge returned an unknown protocol.')

  const success = reader.uint32() === 1
  const errorLength = reader.uint32()
  const result: NativeUvResult = {
    success,
    error: '',
    meshes: [],
    inputVertices: reader.uint32(),
    inputTriangles: reader.uint32(),
    outputVertices: reader.uint32(),
    chartCount: reader.uint32(),
    atlasCount: reader.uint32(),
    width: reader.uint32(),
    height: reader.uint32(),
    chartDurationMs: reader.float64(),
    packDurationMs: reader.float64(),
  }
  const meshCount = reader.uint32()
  result.error = reader.string(errorLength)

  for (let meshIndex = 0; meshIndex < meshCount; meshIndex += 1) {
    const vertexCount = reader.uint32()
    const indexCount = reader.uint32()
    const chartCount = reader.uint32()
    const mesh: NativeUvMeshResult = {
      sourceVertices: new Uint32Array(vertexCount),
      chartIndices: new Int32Array(vertexCount),
      atlasIndices: new Int32Array(vertexCount),
      uvs: new Float32Array(vertexCount * 2),
      indices: new Uint32Array(indexCount),
      chartCount,
    }
    for (let vertexIndex = 0; vertexIndex < vertexCount; vertexIndex += 1) {
      mesh.sourceVertices[vertexIndex] = reader.uint32()
      mesh.chartIndices[vertexIndex] = reader.int32()
      mesh.atlasIndices[vertexIndex] = reader.int32()
      mesh.uvs[vertexIndex * 2] = reader.float32()
      mesh.uvs[vertexIndex * 2 + 1] = reader.float32()
    }
    for (let index = 0; index < indexCount; index += 1) mesh.indices[index] = reader.uint32()
    result.meshes.push(mesh)
  }
  return result
}

function bridgeExecutable(): string {
  const developmentPath = path.join(app.getAppPath(), 'native', 'build', 'Release', 'uv_bridge.exe')
  const packagedPath = path.join(process.resourcesPath, 'native', 'uv_bridge.exe')
  const executable = app.isPackaged ? packagedPath : developmentPath
  if (!fs.existsSync(executable)) {
    throw new Error(`Native UV bridge was not found at ${executable}. Run npm run native:build.`)
  }
  return executable
}

export async function runNativeUv(request: NativeUvRequest): Promise<NativeUvResult> {
  const input = serializeRequest(request)
  const executable = bridgeExecutable()

  return await new Promise((resolve, reject) => {
    const child = spawn(executable, [], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] })
    const stdout: Buffer[] = []
    const stderr: Buffer[] = []
    const timer = setTimeout(() => {
      child.kill()
      reject(new Error('Native UV generation timed out.'))
    }, PROCESS_TIMEOUT_MS)

    child.stdout.on('data', (chunk: Buffer) => stdout.push(chunk))
    child.stderr.on('data', (chunk: Buffer) => stderr.push(chunk))
    child.on('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
    child.on('close', () => {
      clearTimeout(timer)
      try {
        const output = Buffer.concat(stdout)
        if (output.length === 0) {
          reject(new Error(Buffer.concat(stderr).toString('utf8') || 'Native UV bridge returned no data.'))
          return
        }
        resolve(parseResult(output))
      } catch (error) {
        reject(error)
      }
    })
    child.stdin.end(input)
  })
}
