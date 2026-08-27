import { BufferAttribute, BufferGeometry, type Group, type Mesh } from 'three'
import type { NativeUvMeshResult, NativeUvResult } from '../native/uvTypes'
import type { MeshData, MeshDocument } from './MeshData'

function isMesh(value: unknown): value is Mesh {
  return typeof value === 'object' && value !== null && 'isMesh' in value && value.isMesh === true
}
export function buildUvGeometry(mesh: MeshData, uvMesh: NativeUvMeshResult, sourceGeometry?: BufferGeometry): BufferGeometry {
  const vertexCount = uvMesh.sourceVertices.length
  if (uvMesh.uvs.length !== vertexCount * 2) throw new Error('UV buffer does not match the output vertex count.')

  const positions = new Float32Array(vertexCount * 3)
  const normals = mesh.normals ? new Float32Array(vertexCount * 3) : null

  for (let outputVertex = 0; outputVertex < vertexCount; outputVertex += 1) {
    const sourceVertex = uvMesh.sourceVertices[outputVertex]
    if (sourceVertex * 3 + 2 >= mesh.positions.length) {
      throw new Error('xatlas returned an invalid source-vertex mapping.')
    }
    positions.set(mesh.positions.subarray(sourceVertex * 3, sourceVertex * 3 + 3), outputVertex * 3)
    if (normals && mesh.normals) {
      normals.set(mesh.normals.subarray(sourceVertex * 3, sourceVertex * 3 + 3), outputVertex * 3)
    }
  }

  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('uv', new BufferAttribute(uvMesh.uvs.slice(), 2))
  geometry.setIndex(new BufferAttribute(uvMesh.indices.slice(), 1))
  if (normals) geometry.setAttribute('normal', new BufferAttribute(normals, 3))
  else geometry.computeVertexNormals()

  if (sourceGeometry) {
    for (const group of sourceGeometry.groups) geometry.addGroup(group.start, group.count, group.materialIndex)
  }
  geometry.computeBoundingBox()
  geometry.computeBoundingSphere()
  return geometry
}

export function applyUvResultToScene(scene: Group, document: MeshDocument, result: NativeUvResult): () => void {
  const renderMeshes: Mesh[] = []
  scene.traverse((object) => {
    if (isMesh(object)) renderMeshes.push(object)
  })

  if (renderMeshes.length !== document.meshes.length || result.meshes.length !== document.meshes.length) {
    throw new Error('Native UV result does not match the loaded scene meshes.')
  }

  const replacements = renderMeshes.map((renderMesh, index) => {
    const original = renderMesh.geometry
    const generated = buildUvGeometry(document.meshes[index], result.meshes[index], original)
    renderMesh.geometry = generated
    return { renderMesh, original, generated }
  })

  return () => {
    for (const { renderMesh, original, generated } of replacements) {
      if (renderMesh.geometry === generated) renderMesh.geometry = original
      generated.dispose()
    }
  }
}
