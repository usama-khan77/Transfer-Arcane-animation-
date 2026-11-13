#ifndef AI_ANIMATION_ENGINE_H
#define AI_ANIMATION_ENGINE_H

#include <string>
#include <vector>
#include <cstdint>

namespace AIAnimation {

struct Image {
    uint32_t width = 0;
    uint32_t height = 0;
    std::vector<uint8_t> pixels;
};

struct EngineConfig {
    std::string modelPath;
    std::string outputPath;
    int maxWidth = 1920;
    int maxHeight = 1920;
    int threadCount = 4;
};

class Engine {
public:
    Engine();

    bool initialize(const EngineConfig& config);

    bool transform(
        const Image& input,
        Image& output,
        const std::string& prompt
    );

    void release();

    bool isReady() const;

private:
    EngineConfig config_;
    bool ready_;
};

}

#endif
