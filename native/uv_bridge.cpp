#include "uv_engine.h"
#include "xatlas.h"

#include <cstdint>
#include <cstring>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {

constexpr std::uint32_t kInputMagic = 0x31565549;  // IUV1
constexpr std::uint32_t kOutputMagic = 0x3156554F; // OUV1
constexpr std::uint32_t kProtocolVersion = 1;
constexpr std::uint32_t kMaxMeshes = 4096;
constexpr std::uint32_t kMaxVerticesPerMesh = 100'000'000;
constexpr std::uint32_t kMaxIndicesPerMesh = 300'000'000;

template <typename T>
bool readValue(T& value) {
    return static_cast<bool>(std::cin.read(reinterpret_cast<char*>(&value), sizeof(T)));
}

template <typename T>
bool readVector(std::vector<T>& values, std::size_t count) {
    if (count > std::numeric_limits<std::streamsize>::max() / sizeof(T)) return false;
    values.resize(count);
    return count == 0 || static_cast<bool>(std::cin.read(
        reinterpret_cast<char*>(values.data()),
        static_cast<std::streamsize>(count * sizeof(T))));
}

template <typename T>
void writeValue(const T& value) {
    std::cout.write(reinterpret_cast<const char*>(&value), sizeof(T));
}

template <typename T>
void writeVector(const std::vector<T>& values) {
    if (!values.empty()) {
        std::cout.write(
            reinterpret_cast<const char*>(values.data()),
            static_cast<std::streamsize>(values.size() * sizeof(T)));
    }
}

void writeResult(const simpleuv::UvResult& result) {
    writeValue(kOutputMagic);
    const std::uint32_t success = result.success ? 1U : 0U;
    const auto errorLength = static_cast<std::uint32_t>(result.error.size());
    const auto meshCount = static_cast<std::uint32_t>(result.meshes.size());
    writeValue(success);
    writeValue(errorLength);
    writeValue(result.inputVertices);
    writeValue(result.inputTriangles);
    writeValue(result.outputVertices);
    writeValue(result.chartCount);
    writeValue(result.atlasCount);
    writeValue(result.width);
    writeValue(result.height);
    writeValue(result.chartDurationMs);
    writeValue(result.packDurationMs);
    writeValue(meshCount);
    std::cout.write(result.error.data(), static_cast<std::streamsize>(result.error.size()));

    for (const auto& mesh : result.meshes) {
        const auto vertexCount = static_cast<std::uint32_t>(mesh.vertices.size());
        const auto indexCount = static_cast<std::uint32_t>(mesh.indices.size());
        writeValue(vertexCount);
        writeValue(indexCount);
        writeValue(mesh.chartCount);
        for (const auto& vertex : mesh.vertices) {
            writeValue(vertex.sourceVertex);
            writeValue(vertex.chartIndex);
            writeValue(vertex.atlasIndex);
            writeValue(vertex.u);
            writeValue(vertex.v);
        }
        writeVector(mesh.indices);
    }
    std::cout.flush();
}

simpleuv::UvResult failure(std::string message) {
    simpleuv::UvResult result;
    result.error = std::move(message);
    return result;
}

} // namespace

int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);
    xatlas::SetPrint(nullptr, false);

    std::uint32_t magic = 0;
    std::uint32_t version = 0;
    std::uint32_t meshCount = 0;
    simpleuv::PackOptions options;
    std::uint32_t flags = 0;

    if (!readValue(magic) || !readValue(version) || !readValue(meshCount) ||
        !readValue(options.resolution) || !readValue(options.padding) || !readValue(flags)) {
        writeResult(failure("Incomplete bridge request header."));
        return 1;
    }
    if (magic != kInputMagic || version != kProtocolVersion) {
        writeResult(failure("Unsupported bridge protocol."));
        return 2;
    }
    if (meshCount == 0 || meshCount > kMaxMeshes) {
        writeResult(failure("Invalid mesh count."));
        return 3;
    }

    options.rotateCharts = (flags & 1U) != 0;
    options.rotateChartsToAxis = (flags & 2U) != 0;
    options.bruteForce = (flags & 4U) != 0;

    simpleuv::UvEngine engine;
    for (std::uint32_t meshIndex = 0; meshIndex < meshCount; ++meshIndex) {
        std::uint32_t vertexCount = 0;
        std::uint32_t indexCount = 0;
        std::uint32_t hasNormals = 0;
        if (!readValue(vertexCount) || !readValue(indexCount) || !readValue(hasNormals)) {
            writeResult(failure("Incomplete mesh header."));
            return 4;
        }
        if (vertexCount == 0 || vertexCount > kMaxVerticesPerMesh ||
            indexCount == 0 || indexCount > kMaxIndicesPerMesh) {
            writeResult(failure("Mesh exceeds bridge limits or is empty."));
            return 5;
        }

        simpleuv::MeshInput mesh;
        if (!readVector(mesh.positions, static_cast<std::size_t>(vertexCount) * 3) ||
            (hasNormals != 0 && !readVector(mesh.normals, static_cast<std::size_t>(vertexCount) * 3)) ||
            !readVector(mesh.indices, indexCount)) {
            writeResult(failure("Incomplete mesh buffer data."));
            return 6;
        }

        std::string error;
        if (!engine.addMesh(mesh, error)) {
            writeResult(failure("Mesh " + std::to_string(meshIndex + 1) + ": " + error));
            return 7;
        }
    }

    std::string error;
    if (!engine.computeCharts(error)) {
        writeResult(failure(error));
        return 8;
    }

    const auto result = engine.packCharts(options);
    writeResult(result);
    return result.success ? 0 : 9;
}
