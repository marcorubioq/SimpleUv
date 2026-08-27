#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace simpleuv {

struct MeshInput {
    std::vector<float> positions;
    std::vector<float> normals;
    std::vector<std::uint32_t> indices;
};

struct PackOptions {
    std::uint32_t resolution = 1024;
    std::uint32_t padding = 4;
    bool rotateCharts = true;
    bool rotateChartsToAxis = true;
    bool bruteForce = false;
};

struct OutputVertex {
    std::uint32_t sourceVertex = 0;
    std::int32_t chartIndex = -1;
    std::int32_t atlasIndex = -1;
    float u = 0.0F;
    float v = 0.0F;
};

struct MeshOutput {
    std::vector<OutputVertex> vertices;
    std::vector<std::uint32_t> indices;
    std::uint32_t chartCount = 0;
};

struct UvResult {
    bool success = false;
    std::string error;
    std::vector<MeshOutput> meshes;
    std::uint32_t inputVertices = 0;
    std::uint32_t inputTriangles = 0;
    std::uint32_t outputVertices = 0;
    std::uint32_t chartCount = 0;
    std::uint32_t atlasCount = 0;
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    double chartDurationMs = 0.0;
    double packDurationMs = 0.0;
};

class UvEngine {
public:
    UvEngine();
    ~UvEngine();

    UvEngine(const UvEngine&) = delete;
    UvEngine& operator=(const UvEngine&) = delete;
    UvEngine(UvEngine&&) noexcept;
    UvEngine& operator=(UvEngine&&) noexcept;

    bool addMesh(const MeshInput& mesh, std::string& error);
    bool computeCharts(std::string& error);
    UvResult packCharts(const PackOptions& options);
    void reset();

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace simpleuv

