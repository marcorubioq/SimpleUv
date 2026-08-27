#include "uv_engine.h"

#include <cmath>
#include <iostream>
#include <string>

namespace {

bool uvIsNormalized(const simpleuv::OutputVertex& vertex) {
    constexpr float epsilon = 0.0001F;
    return vertex.u >= -epsilon && vertex.u <= 1.0F + epsilon &&
           vertex.v >= -epsilon && vertex.v <= 1.0F + epsilon;
}

float uvTriangleArea(
    const simpleuv::OutputVertex& a,
    const simpleuv::OutputVertex& b,
    const simpleuv::OutputVertex& c) {
    return std::abs((b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u)) * 0.5F;
}

} // namespace

int main() {
    simpleuv::MeshInput cube;
    cube.positions = {
        -0.5F, -0.5F,  0.5F,  0.5F, -0.5F,  0.5F,  0.5F,  0.5F,  0.5F, -0.5F,  0.5F,  0.5F,
         0.5F, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F,  0.5F, -0.5F,  0.5F,  0.5F, -0.5F,
    };
    cube.indices = {
        0, 1, 2, 0, 2, 3, 1, 4, 7, 1, 7, 2, 4, 5, 6, 4, 6, 7,
        5, 0, 3, 5, 3, 6, 3, 2, 7, 3, 7, 6, 5, 4, 1, 5, 1, 0,
    };

    simpleuv::UvEngine engine;
    std::string error;
    if (!engine.addMesh(cube, error)) {
        std::cerr << "AddMesh failed: " << error << '\n';
        return 1;
    }
    if (!engine.computeCharts(error)) {
        std::cerr << "ComputeCharts failed: " << error << '\n';
        return 2;
    }

    simpleuv::PackOptions options;
    options.resolution = 256;
    options.padding = 2;
    const auto result = engine.packCharts(options);

    if (!result.success || result.meshes.size() != 1 || result.chartCount == 0 ||
        result.meshes[0].indices.size() != cube.indices.size() ||
        result.outputVertices < result.inputVertices) {
        std::cerr << "PackCharts produced an invalid result: " << result.error << '\n';
        return 3;
    }

    for (const auto& vertex : result.meshes[0].vertices) {
        if (!uvIsNormalized(vertex) || vertex.sourceVertex >= result.inputVertices) {
            std::cerr << "Invalid output vertex mapping or UV coordinate.\n";
            return 4;
        }
    }

    std::uint32_t degenerateUvTriangles = 0;
    const auto& outputMesh = result.meshes[0];
    for (std::size_t index = 0; index + 2 < outputMesh.indices.size(); index += 3) {
        const auto area = uvTriangleArea(
            outputMesh.vertices[outputMesh.indices[index]],
            outputMesh.vertices[outputMesh.indices[index + 1]],
            outputMesh.vertices[outputMesh.indices[index + 2]]);
        if (area <= 1.0e-8F) ++degenerateUvTriangles;
    }
    if (degenerateUvTriangles > 0) {
        std::cerr << degenerateUvTriangles << " output UV triangles are degenerate.\n";
        return 5;
    }

    std::cout << "xatlas native smoke test passed\n"
              << "  input vertices: " << result.inputVertices << '\n'
              << "  output vertices: " << result.outputVertices << '\n'
              << "  triangles: " << result.inputTriangles << '\n'
              << "  charts: " << result.chartCount << '\n'
              << "  non-degenerate UV triangles: " << result.inputTriangles << '\n'
              << "  atlas: " << result.width << 'x' << result.height << '\n'
              << "  chart ms: " << result.chartDurationMs << '\n'
              << "  pack ms: " << result.packDurationMs << '\n';
    return 0;
}

