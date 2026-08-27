#include "uv_engine.h"

#include "xatlas.h"

#include <chrono>
#include <limits>
#include <utility>

namespace simpleuv {
namespace {

using Clock = std::chrono::steady_clock;

double elapsedMilliseconds(const Clock::time_point start) {
    return std::chrono::duration<double, std::milli>(Clock::now() - start).count();
}

bool validateMesh(const MeshInput& mesh, std::string& error) {
    if (mesh.positions.empty() || mesh.positions.size() % 3 != 0) {
        error = "Positions must contain one or more xyz vertices.";
        return false;
    }
    if (mesh.indices.empty() || mesh.indices.size() % 3 != 0) {
        error = "Indices must contain one or more triangles.";
        return false;
    }
    if (!mesh.normals.empty() && mesh.normals.size() != mesh.positions.size()) {
        error = "Normals must be empty or match the position count.";
        return false;
    }
    if (mesh.positions.size() / 3 > std::numeric_limits<std::uint32_t>::max()) {
        error = "Mesh has too many vertices for xatlas.";
        return false;
    }

    const auto vertexCount = static_cast<std::uint32_t>(mesh.positions.size() / 3);
    for (const auto index : mesh.indices) {
        if (index >= vertexCount) {
            error = "An index references a missing vertex.";
            return false;
        }
    }
    return true;
}

} // namespace

class UvEngine::Impl {
public:
    Impl() : atlas(xatlas::Create()) {}
    ~Impl() { xatlas::Destroy(atlas); }

    xatlas::Atlas* atlas = nullptr;
    std::uint32_t inputVertices = 0;
    std::uint32_t inputTriangles = 0;
    double chartDurationMs = 0.0;
    bool chartsComputed = false;
};

UvEngine::UvEngine() : impl_(std::make_unique<Impl>()) {}
UvEngine::~UvEngine() = default;
UvEngine::UvEngine(UvEngine&&) noexcept = default;
UvEngine& UvEngine::operator=(UvEngine&&) noexcept = default;

bool UvEngine::addMesh(const MeshInput& mesh, std::string& error) {
    if (!validateMesh(mesh, error)) return false;

    xatlas::MeshDecl declaration;
    declaration.vertexCount = static_cast<std::uint32_t>(mesh.positions.size() / 3);
    declaration.vertexPositionData = mesh.positions.data();
    declaration.vertexPositionStride = sizeof(float) * 3;
    declaration.indexCount = static_cast<std::uint32_t>(mesh.indices.size());
    declaration.indexData = mesh.indices.data();
    declaration.indexFormat = xatlas::IndexFormat::UInt32;

    if (!mesh.normals.empty()) {
        declaration.vertexNormalData = mesh.normals.data();
        declaration.vertexNormalStride = sizeof(float) * 3;
    }

    const auto result = xatlas::AddMesh(impl_->atlas, declaration);
    if (result != xatlas::AddMeshError::Success) {
        error = xatlas::StringForEnum(result);
        return false;
    }

    impl_->inputVertices += declaration.vertexCount;
    impl_->inputTriangles += declaration.indexCount / 3;
    impl_->chartsComputed = false;
    return true;
}

bool UvEngine::computeCharts(std::string& error) {
    if (impl_->inputVertices == 0) {
        error = "Add at least one mesh before computing charts.";
        return false;
    }

    const auto start = Clock::now();
    xatlas::ComputeCharts(impl_->atlas);
    impl_->chartDurationMs = elapsedMilliseconds(start);
    impl_->chartsComputed = true;
    return true;
}

UvResult UvEngine::packCharts(const PackOptions& options) {
    UvResult result;
    result.inputVertices = impl_->inputVertices;
    result.inputTriangles = impl_->inputTriangles;
    result.chartDurationMs = impl_->chartDurationMs;

    if (!impl_->chartsComputed) {
        result.error = "Compute charts before packing.";
        return result;
    }
    if (options.resolution == 0) {
        result.error = "Packing resolution must be greater than zero.";
        return result;
    }

    xatlas::PackOptions packOptions;
    packOptions.resolution = options.resolution;
    packOptions.padding = options.padding;
    packOptions.rotateCharts = options.rotateCharts;
    packOptions.rotateChartsToAxis = options.rotateChartsToAxis;
    packOptions.bruteForce = options.bruteForce;

    const auto start = Clock::now();
    xatlas::PackCharts(impl_->atlas, packOptions);
    result.packDurationMs = elapsedMilliseconds(start);
    result.width = impl_->atlas->width;
    result.height = impl_->atlas->height;
    result.chartCount = impl_->atlas->chartCount;
    result.atlasCount = impl_->atlas->atlasCount;

    if (result.width == 0 || result.height == 0 || impl_->atlas->meshCount == 0) {
        result.error = "xatlas did not produce a packed atlas.";
        return result;
    }

    result.meshes.reserve(impl_->atlas->meshCount);
    for (std::uint32_t meshIndex = 0; meshIndex < impl_->atlas->meshCount; ++meshIndex) {
        const auto& source = impl_->atlas->meshes[meshIndex];
        MeshOutput output;
        output.chartCount = source.chartCount;
        output.indices.assign(source.indexArray, source.indexArray + source.indexCount);
        output.vertices.reserve(source.vertexCount);

        for (std::uint32_t vertexIndex = 0; vertexIndex < source.vertexCount; ++vertexIndex) {
            const auto& vertex = source.vertexArray[vertexIndex];
            output.vertices.push_back({
                vertex.xref,
                vertex.chartIndex,
                vertex.atlasIndex,
                vertex.uv[0] / static_cast<float>(result.width),
                vertex.uv[1] / static_cast<float>(result.height),
            });
        }

        result.outputVertices += source.vertexCount;
        result.meshes.push_back(std::move(output));
    }

    result.success = true;
    return result;
}

void UvEngine::reset() {
    impl_ = std::make_unique<Impl>();
}

} // namespace simpleuv
