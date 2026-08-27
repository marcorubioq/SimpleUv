import {
  BufferAttribute,
  type BufferGeometry,
  type Group,
  type InterleavedBufferAttribute,
  type Mesh,
  type Object3D,
} from 'three'
import type { MeshData, MeshDocument, MeshEdge, MeshFace, MeshIssue, MeshSummary } from './MeshData'

type GeometryAttribute = BufferAttribute | InterleavedBufferAttribute

const DEGENERATE_EPSILON = 1e-20

function copyAttribute(attribute: GeometryAttribute, itemSize: 2 | 3): Float32Array {
  const result = new Float32Array(attribute.count * itemSize)

  for (let index = 0; index < attribute.count; index += 1) {
    const offset = index * itemSize
    result[offset] = attribute.getX(index)
    result[offset + 1] = attribute.getY(index)
    if (itemSize === 3) result[offset + 2] = attribute.getZ(index)
  }

  return result
}

function createIndices(geometry: BufferGeometry, vertexCount: number): Uint32Array {
  if (!geometry.index) {
    return Uint32Array.from({ length: vertexCount }, (_, index) => index)
  }

  const result = new Uint32Array(geometry.index.count)
  for (let index = 0; index < geometry.index.count; index += 1) {
    result[index] = geometry.index.getX(index)
  }
  return result
}

function triangleIsDegenerate(positions: Float32Array, a: number, b: number, c: number): boolean {
  const ax = positions[a * 3]
  const ay = positions[a * 3 + 1]
  const az = positions[a * 3 + 2]
  const abx = positions[b * 3] - ax
  const aby = positions[b * 3 + 1] - ay
  const abz = positions[b * 3 + 2] - az
  const acx = positions[c * 3] - ax
  const acy = positions[c * 3 + 1] - ay
  const acz = positions[c * 3 + 2] - az
  const crossX = aby * acz - abz * acy
  const crossY = abz * acx - abx * acz
  const crossZ = abx * acy - aby * acx

  return crossX * crossX + crossY * crossY + crossZ * crossZ <= DEGENERATE_EPSILON
}

function buildTopology(positions: Float32Array, indices: Uint32Array) {
  const faces: MeshFace[] = []
  const edges: MeshEdge[] = []
  const edgeLookup = new Map<string, number>()
  let invalidIndices = 0
  let degenerateTriangles = 0
  const vertexCount = positions.length / 3

  for (let offset = 0; offset + 2 < indices.length; offset += 3) {
    const vertices = [indices[offset], indices[offset + 1], indices[offset + 2]] as [number, number, number]
    if (vertices.some((vertex) => vertex >= vertexCount)) {
      invalidIndices += 1
      continue
    }

    if (triangleIsDegenerate(positions, ...vertices)) degenerateTriangles += 1

    const faceIndex = faces.length
    const faceEdges = [
      [vertices[0], vertices[1]],
      [vertices[1], vertices[2]],
      [vertices[2], vertices[0]],
    ].map(([first, second]) => {
      const vertexA = Math.min(first, second)
      const vertexB = Math.max(first, second)
      const key = `${vertexA}:${vertexB}`
      const existing = edgeLookup.get(key)

      if (existing !== undefined) {
        edges[existing].faces.push(faceIndex)
        return existing
      }

      const edgeIndex = edges.length
      edgeLookup.set(key, edgeIndex)
      edges.push({ vertexA, vertexB, faces: [faceIndex], seam: false })
      return edgeIndex
    }) as [number, number, number]

    faces.push({ vertices, edges: faceEdges })
  }

  return { faces, edges, invalidIndices, degenerateTriangles }
}

function extractMesh(mesh: Mesh, index: number): MeshData | null {
  const positionAttribute = mesh.geometry.getAttribute('position')
  if (!positionAttribute || positionAttribute.itemSize !== 3 || positionAttribute.count === 0) return null

  const positions = copyAttribute(positionAttribute, 3)
  const indices = createIndices(mesh.geometry, positionAttribute.count)
  const normalAttribute = mesh.geometry.getAttribute('normal')
  const uvAttribute = mesh.geometry.getAttribute('uv')
  const normals = normalAttribute?.itemSize === 3 ? copyAttribute(normalAttribute, 3) : null
  const uvs = uvAttribute?.itemSize === 2 ? copyAttribute(uvAttribute, 2) : null
  const topology = buildTopology(positions, indices)
  const nonManifoldEdges = topology.edges.filter((edge) => edge.faces.length > 2).length
  const issues: MeshIssue[] = []

  if (!normals) issues.push({ code: 'missing-normals', message: 'Mesh has no vertex normals.', count: 1 })
  if (topology.invalidIndices > 0) {
    issues.push({ code: 'invalid-index', message: 'Triangles reference missing vertices.', count: topology.invalidIndices })
  }
  if (topology.degenerateTriangles > 0) {
    issues.push({ code: 'degenerate-triangle', message: 'Triangles have zero or near-zero area.', count: topology.degenerateTriangles })
  }
  if (nonManifoldEdges > 0) {
    issues.push({ code: 'non-manifold-edge', message: 'Edges are shared by more than two faces.', count: nonManifoldEdges })
  }

  return {
    id: mesh.uuid,
    name: mesh.name || `Mesh ${index + 1}`,
    positions,
    indices,
    normals,
    uvs,
    worldMatrix: new Float32Array(mesh.matrixWorld.elements),
    faces: topology.faces,
    edges: topology.edges,
    issues,
  }
}

function isMesh(object: Object3D): object is Mesh {
  return 'isMesh' in object && object.isMesh === true
}

function summarize(meshes: MeshData[]): MeshSummary {
  return meshes.reduce<MeshSummary>(
    (summary, mesh) => {
      summary.meshes += 1
      summary.vertices += mesh.positions.length / 3
      summary.triangles += mesh.faces.length
      summary.edges += mesh.edges.length
      summary.boundaryEdges += mesh.edges.filter((edge) => edge.faces.length === 1).length
      summary.nonManifoldEdges += mesh.edges.filter((edge) => edge.faces.length > 2).length
      summary.degenerateTriangles += mesh.issues.find((issue) => issue.code === 'degenerate-triangle')?.count ?? 0
      summary.meshesWithUvs += mesh.uvs ? 1 : 0
      summary.meshesWithoutNormals += mesh.normals ? 0 : 1
      return summary
    },
    {
      meshes: 0,
      vertices: 0,
      triangles: 0,
      edges: 0,
      boundaryEdges: 0,
      nonManifoldEdges: 0,
      degenerateTriangles: 0,
      meshesWithUvs: 0,
      meshesWithoutNormals: 0,
    },
  )
}

export function extractMeshDocument(scene: Group): MeshDocument {
  scene.updateMatrixWorld(true)
  const meshes: MeshData[] = []

  scene.traverse((object) => {
    if (!isMesh(object)) return
    const mesh = extractMesh(object, meshes.length)
    if (mesh) meshes.push(mesh)
  })

  if (meshes.length === 0) {
    throw new Error('The model does not contain any non-empty triangle meshes.')
  }

  return { meshes, summary: summarize(meshes) }
}

