# SimpleUV

SimpleUV is a Windows-first, standalone UV layout application for 3D artists. The project is currently at its first bootstrap milestone.

## Current features

- Electron desktop window
- React and TypeScript interface powered by Vite
- Three.js viewport rendered through React Three Fiber
- Perspective camera with orbit, pan, and zoom controls
- Grid, basic lighting, responsive canvas, and a temporary test cube
- GLB/GLTF opening through the toolbar or drag and drop
- Automatic camera framing and user-friendly load status
- Internal typed-array mesh representation with faces and edge adjacency
- Geometry statistics and checks for missing normals, degenerate triangles, and non-manifold edges

## Requirements

- Windows 10 or Windows 11
- Node.js 22.12 or newer
- npm 11 or newer
- Visual Studio 2022 with the Desktop development with C++ workload
- Windows SDK
- CMake and Ninja (required for the future native UV engine)

## Development

```powershell
npm install
npm run dev
```

The Vite development server and Electron window start together. Close the Electron window or press `Ctrl+C` in the terminal to stop development mode.

## Build

```powershell
npm run build
npm start
```

The renderer build is written to `dist/` and the Electron main process to `dist-electron/`.

## Project structure

```text
desktop/electron/  Electron main process
src/app/           React application shell
src/viewport3d/    Three.js 3D viewport
```

Future milestones will add internal topology, the native xatlas engine, a 2D UV editor, checker materials, packing controls, and GLB export.

## Known limitations

- The cube is a temporary renderer test object.
- Menu actions and Auto UV are placeholders.
- Standalone `.gltf` files that reference external `.bin` or texture files are not yet supported; use GLB for the most reliable import.
- No UV generation, 2D UV editor, checker, or export is implemented yet.
