# Game Engine: Rem's Engine

Parallel to my video editor Rem's Studio, I am developing my own game engine. I have often written the beginnings of
small games, but they always had much in common, so I decided to write my own engine.

- direct Java/Kotlin support
- usable in my favourite IDE: Intellij Idea
- completely Open Source
- no fees
- fewer annoyances with skeletons than Unreal Engine 4
- support for all kinds of formats
- working/loading from inside compressed folders
- I get to learn even more about game engines <3

This engine does not have the target to offer the best graphics, or be the best performant. Its goal is to be nice and
quick to develop in, like a tool box.

## What does the engine look like?

![Promo Image of Editor](https://remsengine.phychi.com/img/promo-editor.webp)

What can you see in this image?

### UI Overview

Left is a tree view of the scene, center is the 3d view of the scene, right is the property editor.
At the bottom, you can see two file explorers with thumbnail generation: the left one is the project,
the right one is my documents-folder.

All UI is part of the engine, so you can create your own components ("Panel"), and your own rendering styles.

### Asset Loading

To render assets as thumbnails, or in 3d, they need to be loaded.
This scene contains glb/gltf, obj and fbx files, jpegs and webps.

### Rendering Debug Modes

Currently, the scene is in "MSAA Deferred" mode, so it renders MSAAx8 with deferred light rendering.
There is more than fifty render modes for debugging all kinds of features and rendering processes.
In the shown scene, there is also a bit of post-processing: SSR, SSAO, Bloom, outline for selection,
refraction for glass. Shadows are conditionally real-time ofc, too.

### Skeletal Animations

The woman with the phone is a scene from Mixamo, that I just loaded as a sub-scene.
Most file readers export their contents as a ready-to-use sub-scene.
It's animated ofc.

### Transparency

Transparent objects are currently rendered with an order-independent approach,
but it will need a weighting function in the future, so closer panes have more weight on the final color.

## Most Relevant Websites

### [Official Website](https://remsengine.phychi.com/)

Contains most important links (documentation, discord, github), and a tutorial to get started.

### [Documentation](https://remsengine.phychi.com/docs/)

JavaDoc-like code reference with a handy search bar.
Generated by [Rem's Docs Generator](https://github.com/AntonioNoack/RemsDocsGenerator) 😄.

The code is mainly documented within itself, so I recommend you to download the engine source code.
Using it, you can easily look up how and where pieces of engine code are being used.

### [Wiki](https://github.com/AntonioNoack/RemsStudio/wiki)

Since both projects originate from the same base, the wiki currently
is located at Rem's Studio's repository. It contains a bit of information about serialization and the modding/plugin system.

## Features

All features can be found in some test in the test folder.
Some depend on local files, which you might not have, but most should work, and I try to minimize those.

### Architecture

- Entity-Component based system like Unity, plus Systems similar to Bevy
- 64-bit fp transformations for universe-scale games
  - on GPU-side, camera is always close-to/at origin, FP32
- AABB optimized scene hierarchy
- mods / plugins from the start: your game is a mod(ule) for the engine
- in editor: automatic file reload, on file change
- heavy operations are cache-based, with automatic free after not-requesting for a set time
  - ImageCache/TextureCache (CPU/GPU)
  - MeshCache, MaterialCache, AnimationCache, SkeletonCache, PrefabCache
  - AudioCache
  - FontManager for textures and sizes
  - PDFCache, VideoCache
  - (File)MetadataCache
- Lua scripting/limited visual coding support

### File Formats

As this engine is a toolbox, I want to be able to load all kinds of formats:

  - Image formats (ffmpeg, ImageIO, Image4j, custom): png, jpg, tga, ico, dds, exr, hdr, svg, pcx, qoi, xcf (Gimp)
  - Video formats (ffmpeg): wav, mp3, mp4, avi, flv, gif
  - Mesh formats (Assimp, custom) obj, fbx, gltf, dae, blend, vox, ply, md2, md5mesh, mitsuba
  - Package formats (Apache Compress, JUnRar): zip, tar, tar.gz, rar, 7z, bz2, lz4, xar, oar
  - Metadata formats (custom): json, csv, yaml, xml
  - Others (Apache PDFBox, custom): pdf, tar/text-based Unity-packages
  - Note: not all are fully supported

The engine can also load most files from compressed folders (recursively as well), just not video at the moment.
Files have been abstracted into a class called FileReference, for storage files, web files, files inside zips, in-memory-files, pseudo-files, ...,
so you can handle and reference them all the same way.

### Graphics

Instead of relying on pre-defined text files that are compiled shaders, Rem's Engine goes the route of runtime-compilation.
For future Vulkan ports, a list of used shaders may be required, so all shaders then can be pre-compiled.

This has the advantage that shaders for materials only need to be defined once, for forward- and deferred rendering, for non-instanced, instances, skeleton-animated,
and any other special kind of rendering.

- PBR-based material descriptions (roughness-metallic)
- pipeline / shader-object abstraction over graphics APIs like OpenGL
- simple switch between forward- and deferred rendering
    - for beautiful MSAA with a few lights,
    - or thousands of lights without performance hit
    - Note: forward rendering does not support SSR
- FXAA as cheap anti-aliasing, MSAA as expensive anti-aliasing
- different light types, with shadow support: directional, spot, point, circular, rectangle
- shadows with cascades (directional, spot, point)
- planar reflections
- screen space reflections (SSR)
- screen space ambient occlusion (SSAO)
- environment map reflections
- non-animated, animated, procedural and GPU-only meshes
- static and animated meshes can be drawn using instanced rendering
    - animation states are stored in 2d texture per skeleton -> instanced meshes can be in different animations, but still be rendered together
- signed distance functions can be used as mesh replacements
- bloom to convey brightness
- AMD FSR 1.0: dynamic upscaling and sharpening
    - is used to upscale images in the file explorer as well
- transparent (glass-like) meshes
  - looks fine until there is dark layers
  - order independent

### UI

- easily extendable based UI library
- 3d gizmos
- real-time graph editors
    - materials (shaders)
    - render pipelines (post-processing)
    - animations
- Android-inspired layout system
- text inputs have integrated spellchecking
- all kinds of other value inputs
- rendered on GPU with OpenGL
- controller support
    - works for UI automatically
    - you can even use your controller in other programs as a mouse, while Rem's Engine/Studio is running :3

### Physics

- Bullet as 3d physics engine
    - running on a separate thread for uninterrupted graphics (optional)
    - running with 64 bit floating precision for accuracy / universe simulations
- Box2d as 2d physics engine
    - currently a prototype

### Virtual Reality

Virtual/Augmented Reality support is currently WIP, and implemented using OpenXR.
Scenes can be already viewed in 3d, and traversed, but controls for 3d UI haven't been implemented yet.
Creating a native build for the meta quest is planned, too, but not yet started.

### Planned Features

- easy local multiplayer
- usable Multiplayer, local and tcp/udp
- per-button export to Windows/Linux (currently WIP/manual)
- fully supported Lua scripting
    - [LuaAnimTextPanel](Lua/src/me/anno/lua/ui/LuaAnimTextPanel.kt)
    - [QuickScriptComponent](Lua/src/me/anno/lua/QuickScriptComponent.kt)

### Maybe Later Features

- automatic export to Web
- automatic export to Android
- light baking for realistic graphics
- visual coding? works for some stuff already (pipelines, materials, animation trees)
- support separate mice / keyboards for local multiplayer?
- Vulkan/DirectX12 backend to support hardware raytracing

## Projects using Rem's Engine

- [Rem's Studio](https://github.com/AntonioNoack/RemsStudio)
- [Cellular Automata](https://github.com/AntonioNoack/CellularAutomata)
- [Tsunami Simulation](https://github.com/AntonioNoack/RemsTsunamis)
- [Voxel World](https://github.com/AntonioNoack/RemsEngine-VoxelWorld)
- [Monte-Carlo Map Optimization](https://github.com/AntonioNoack/MonteCarloMapOptimization)
- [Rubik's Cube UI](https://github.com/AntonioNoack/RubiksCubeUI)
- a few more, non-published (yet?)

## Getting Started

- Download an IDE of your choice. I prefer IntelliJ IDEA. Eclipse may work with a bit of luck, too.
- Download either the engine source code, and compile it yourself, or download a pre-compiled jar.
    - if that's too out-of-date, ask me, e.g., on Discord, to create a build for you

Trying out things is fastest by adding RemsEngine.jar to your project dependencies, or by directly working
in the tests folder in the engine project.

For creating your own games, there's two ways:
- Data oriented: use predefined components, and combine them into scenes using the built-in editor; maybe use flow graphs for custom logic
- Code oriented: define scenes and logic procedurally

## Samples

Besides my personal projects that use Rem's Engine, there is also quite a few samples within the engine source code.
They also function as tests for me.

Some tests, starting the whole engine, like a small game:

- [GFX: MarchingCubes.kt](test/src/me/anno/tests/geometry/MarchingCubes.kt)
- [Math: PathfindingAccTest.kt](test/src/me/anno/tests/geometry/PathFindingAccTest.kt)

Some feature tests:

- [Math: SDFColliderTest.kt](test/src/me/anno/tests/collider/SDFColliderTest.kt)
- [Internal: Hierarchy.kt](test/src/me/anno/tests/engine/prefab/Hierarchy.kt)
- [GFX: MarchingSquares.kt](test/src/me/anno/tests/geometry/MarchingSquares.kt)
- [GFX: MarchingCubes.kt](test/src/me/anno/tests/geometry/MarchingCubes.kt)
- [GFX: Reduction.kt](test/src/me/anno/tests/shader/Reduction.kt)
- [GFX: FSR.kt](test/src/me/anno/tests/shader/FSR1.kt)
- [Debug: JsonFormatterTest.kt](test/src/me/anno/tests/io/files/JsonFormatterTest.kt)
- [UI: DrawCurves.kt](test/src/me/anno/tests/shader/DrawCurves.kt)
- [UI: AnimTest.kt](test/src/me/anno/tests/ui/AnimTest.kt)
- [Snake Game](test/src/me/anno/tests/game/Snake.kt), [Running Web Demo](https://remsengine.phychi.com/jvm2wasm/snake/)

You can find most examples in the "tests" folder. In total, there are more than 400 handwritten tests for you to see how
the engine works :).

## Ports

- Linux, Windows by default
- [Web (WASM), currently very slow and limited](https://github.com/AntonioNoack/JVM2WASM), also got a C/C++-port (could be compiled to any platform)
- [Android](https://github.com/AntonioNoack/RemsEngine-Android), currently JVM, but might use JVM2CPP in the future.
- [DirectX11 backend](https://github.com/AntonioNoack/JDirectX11), not perfect yet
- MacOS should be simple, if OpenGL support is good enough
  - I've tried running it inside a VirtualBox VM, but I've got issues creating an OpenGL context
- iOS: not yet tested, and idk whether Java runs on iOS. "codenameone"?

## Build It

All libraries are shipped with the engine :).
Use any IDE you want, with Java and Kotlin support. Best use IntelliJ IDEA.

Then run any of the tests you want 😊.

Some parts of the engine have been packed into modules.
This is the preferred way to create a game, or library.
Currently extracted modules:
- PDF (pdf document to image converter)
- SDF (signed distance functions)
- Bullet (3d physics)
- BulletJME (3d physics running in C++ code)
- Box2d (2d physics)
- Recast (path finding)
- Image (image loaders)
- Mesh (mesh loaders)
- Network (general server-client logic)
- JVM (JVM-only functionality/implementations)
- Lua (Lua language support using Lua4J)
- Unpack (Unzipping files; loading Unity project files)

So if you need them, don't forget to import them into your project.
(either as a compiled artifact = .jar, or as an Intellij module)

## Ship It

### Linux, Windows

Shipping is easy on Windows and Linux: just export your projects will all dependencies into a .jar file, or download
dependencies on the first start (as I do with FFMPEG and spellchecking).

I'm also working on a way to automatically export a project: you can find the code in the Export-module, and the export button in the option-bar in the engine UI.

### Android

Shipping to Android is a bit more complicated:

- download the [Android fork](https://github.com/AntonioNoack/RemsEngine-Android) as an Android Studio project
- modify it to your needs
- libraries can be added just like in IntelliJ IDEA

### Web (HTML5, WASM, WebGL)

*Note: this is currently broken when using 3d as of March 17th 2024*

My JVM->WASM translation is a little slow at the moment, unfortunately. It's fast enough for simple games though.
Lots of things haven't been implemented or are half-baked, so be cautious and don't expect too much (e.g., image loading
is very limited currently)!

Shipping to web:

- download [JVM2WASM](https://github.com/AntonioNoack/JVM2WASM)
- build your game, best as a panel-creating function, into a JAR (don't use sync IO)
- bind that as the missing VideoStudio/RemsEngine.jar in JVM2WASM
- run JVM2WASM.kt to create the WASM file and index0.js bindings
- if needed, implement missing or extra functions from Java

First demo: [Snake Game](https://remsengine.phychi.com/jvm2wasm/snake/)
from [tests](test/src/me/anno/tests/game/Snake.kt).

## Just Try/Use It

If you don't want to compile the engine yourself, and just want to focus on developing games, plugins or mods, you can
use [pre-built versions](https://github.com/AntonioNoack/RemsEngine/releases).
If you want an up-to-date build, just write me, e.g., via Discord, and I'll create one for you.

## Used libraries

* [LWJGL](https://www.lwjgl.org/) (Graphics and Audio; OpenGL, GLFW, OpenAL, OpenXR)
* [Assimp](https://github.com/assimp/assimp) (loading 3d meshes; from LWJGL)
* [JOML](https://github.com/JOML-CI/JOML) (Matrix calculations and transforms for rendering)
* [FFMpeg](https://ffmpeg.org/) (Video/Image/Audio Import & Export)
* [HSLuv](https://github.com/hsluv/hsluv-java) (HSL alternative with constant brightness)
* [LanguageTool](https://languagetool.org/) (Spellchecking)
* [JTransforms](https://sites.google.com/site/piotrwendykier/software/jtransforms) (Fast Fourier Transform)
* [Apache Imaging](https://commons.apache.org/proper/commons-imaging/) (More supported image formats, like .ico)
* [Apache CLI](https://commons.apache.org/proper/commons-cli/) (Basics of Command Line Interface)
* [Thumbnailator](https://github.com/coobird/thumbnailator/) (Only EXIF related code, detecting rotated JPEG files)
* [JNA Platform](https://github.com/java-native-access/jna) (Moving files to trash)
* [Apache PDFBox](https://pdfbox.apache.org/) (Rendering PDFs)
* [JAI ImageIO Core](https://github.com/jai-imageio/jai-imageio-core) (More image formats for PDFs)
* [JAI ImageIO JPEG2000](https://github.com/jai-imageio/jai-imageio-jpeg2000) (JPEG 2000 support for PDFs)
* [jUnRAR](https://github.com/edmund-wagner/junrar), [Apache VFS](https://search.maven.org/artifact/org.apache.commons/commons-vfs2/2.8.0/jar) (
  Reading RAR files)
* [Image4j](https://github.com/imcdonagh/image4j) (Reading ICO images)
* [Bullet](http://jbullet.advel.cz/) (3d Physics, adjusted to be FP64 instead of FP32)
* [Box2d](https://github.com/jbox2d/jbox2d) (2d Physics, still FP32)
* [LuaJ](https://github.com/luaj/luaj) (Lua scripting)
* [QOI-Java](https://github.com/saharNooby/qoi-java) (QOI image format)
* [Recast4j](https://github.com/AntonioNoack/recast4j/) (NavMesh generation; converted to Kotlin and adjusted for JOML;
  partially integrated)

If I forgot something, just write me a message 😄.
