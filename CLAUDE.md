# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About

box2dlights is a 2D lighting framework for libGDX games. It uses Box2D physics raycasting to compute shadows and OpenGL ES 2.0 for rendering. Published to Maven Central as `com.badlogicgames.box2dlights:box2dlights`.

## Build Commands

```bash
./gradlew build          # Compile, test, and assemble JAR
./gradlew test           # Run tests
./gradlew javadoc        # Generate API docs
./gradlew clean          # Clean build directory
./gradlew publish        # Publish to Maven Central (CI only)
```

Java 1.8 source/target compatibility is required.

## Project Structure

Single Gradle module (`box2dlights`) with two packages:

- **`box2dLight/`** — Core library (18 files): `RayHandler`, light types, framebuffer management
- **`shaders/`** — GLSL shader programs: shadow, blur, diffuse, and custom shader support
- **`test/`** — Interactive test launchers using libGDX LWJGL backend (not automated unit tests)

## Architecture

### Rendering Pipeline

`RayHandler` is the central orchestrator. Each frame:
1. Each active `Light` casts rays against Box2D fixtures via raycasting
2. Intersection points become mesh vertices representing light/shadow geometry
3. Geometry is rendered to a framebuffer (`LightMap`) using the configured blend mode
4. Optional Gaussian blur (`Gaussian`) softens shadow edges
5. The light map is composited over the scene with ambient light and optional gamma correction

### Light Hierarchy

```
Light (abstract, Disposable)
├── PositionalLight (abstract) — has world position
│   ├── PointLight      — circular, rays in all directions
│   └── ConeLight       — sector-shaped with direction and angle
├── DirectionalLight    — infinite-distance (sun-like), no position
└── ChainLight          — rays distributed along a chain of vertices
```

### Blend Modes

Three rendering modes with different GL blend functions:
- **Simple** (`SRC_ALPHA, GL_ONE`) — no shadows
- **Shadow** (`GL_ONE, GL_ONE_MINUS_SRC_ALPHA`) — shadow rendering
- **Diffuse** (`GL_DST_COLOR, GL_ZERO`) — diffuse lighting effect

Configured via `RayHandlerOptions` or set directly on `RayHandler`.

### Key Design Points

- `LightMap` manages the FBO(s) used for light accumulation and blur passes
- Lights can be **static** (rays computed once), **dynamic** (recomputed each frame), or **xray** (no shadows)
- Culling skips lights whose bounding volume is offscreen
- Each light has an optional `ContactFilter` to control which Box2D fixtures cast shadows
- Custom shaders are supported by subclassing the shader classes in `shaders/`
- Pseudo-3D shadow mode (experimental) varies shadow height based on normal direction

### Integration Pattern

```java
RayHandler rayHandler = new RayHandler(world);
rayHandler.setAmbientLight(0.1f, 0.1f, 0.1f, 1f);
PointLight light = new PointLight(rayHandler, 128, Color.WHITE, 10f, x, y);

// In render loop:
rayHandler.setCombinedMatrix(camera);
rayHandler.updateAndRender();
```
