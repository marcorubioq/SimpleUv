const SUPPORTED_EXTENSIONS = new Set(['glb', 'gltf'])

export interface ModelFile {
  name: string
  data: ArrayBuffer
}

export async function readModelFile(file: File): Promise<ModelFile> {
  const extension = file.name.split('.').pop()?.toLowerCase()

  if (!extension || !SUPPORTED_EXTENSIONS.has(extension)) {
    throw new Error('Unsupported file. Choose a .glb or .gltf model.')
  }

  if (file.size === 0) {
    throw new Error('The selected model is empty.')
  }

  return {
    name: file.name,
    data: await file.arrayBuffer(),
  }
}

