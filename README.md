# MNV X11

An X11 display server for Android. Run Linux GUI apps from Termux or a proot distro and use them on your phone with touch, mouse and keyboard.

## Modules

- **`xserver`**: the X11 core-protocol server in plain Kotlin, with no Android dependencies. It renders into a software framebuffer (each window keeps its own pixels and they are composited) and bundles the public-domain misc-fixed bitmap fonts.
- **`app`**: the Android app (`com.mnvstudio.x11`), built with Jetpack Compose and Poppins:
  - Onboarding and setup flow
  - Home screen showing server status
  - Full-screen display with touchpad / direct-touch input and an extra-keys bar
  - Settings and a guide
  - A foreground service that keeps the server running

## Built-in Linux

The app can run Linux apps by itself, without Termux. It downloads Alpine Linux's minimal root filesystem (about 4 MB) on first use and runs it with [proot](https://proot-me.github.io) (GPL-2.0), with no root needed. The **Linux apps** screen installs apps with `apk` and opens them on the display. It also has a command box for anything else.

proot isn't stored in the repository. The `fetchProot` Gradle task downloads static Android builds from `green-green-avk/build-proot-android` into `app/src/main/jniLibs` before each build (you can change the source with `-PprootBaseUrl=...`). The binaries are packaged as `libproot.so` and `libproot-loader.so`, because Android only lets apps execute files from their native library directory.

## Connecting apps from Termux

Start the server in the app, then in Termux:

```sh
pkg install x11-repo && pkg install xterm
export DISPLAY=:0
xterm &
```

The server listens on the abstract socket `@/tmp/.X11-unix/X0` (`DISPLAY=:0`) and on TCP `127.0.0.1:6000` (`DISPLAY=127.0.0.1:0`).

## What works

The core protocol: windows, drawing, images, text with the bundled fixed fonts, keyboard, pointer, properties, selections and window manager redirection. No X extensions are offered yet (RENDER, SHM, XInput, RANDR, ...), so classic Xlib/Xt apps work (xterm, xeyes, xclock and xcalc are tested). Full desktops and GTK/Qt apps need those extensions.

## Testing the server on a desktop

The server can run headless against real X clients:

```sh
./gradlew :xserver:testClasses
# then run com.mnvstudio.x11.server.HeadlessServerKt with args: <display> <width> <height> <screenshot dir>
DISPLAY=127.0.0.1:1 xterm &
```

The harness reads commands from stdin: `shot <name>`, `move <x> <y>`, `click <button>`, `type <text>` and `key <keysym hex>`.
