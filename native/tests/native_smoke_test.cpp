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

    std::cout << "xatlas native smoke test passed\n"
              << "  input vertices: " << result.inputVertices << '\n'
              << "  output vertices: " << result.outputVertices << '\n'
              << "  triangles: " << result.inputTriangles << '\n'
              << "  charts: " << result.chartCount << '\n'
              << "  atlas: " << result.width << 'x' << result.height << '\n'
              << "  chart ms: " << result.chartDurationMs << '\n'
              << "  pack ms: " << result.packDurationMs << '\n';
    return 0;
}

