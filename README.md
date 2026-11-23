# xmousebutton

A Java port of the idea behind X-Mouse Button Control: remap mouse buttons and the scroll
wheel, with different bindings for different applications.

Each profile in the config file applies to a process (for example `chrome.exe`) or a window
title (for example `*YouTube*`). The first matching profile wins, and `[default]` is used when
nothing else matches.

This project is a clean-room rewrite. It does not contain code from the original application.

## Status

| Platform | Config check (`--check`) | Remapping |
|----------|--------------------------|-----------|
| Windows  | yes                      | yes (low-level mouse hook, JNA) |
| Linux    | yes                      | not yet |
| macOS    | yes                      | not yet |

The Windows backend is the only one that can see and swallow mouse input. The Linux and macOS
builds validate configs and exit with a message. Contributions for X11, Wayland or macOS
backends are welcome; see [Adding a backend](#adding-a-backend).

## Download

Tagged releases include:

- `XMouseButton-windows-<version>.zip`, a bundled app image (no Java install needed)
- `XMouseButton-linux-<version>.tar.gz` and `XMouseButton-macos-<version>.tar.gz`
- `xmousebutton-<version>-all.jar`, a single jar that needs Java 21 or newer

Each release also has `SHA256SUMS.txt`.

## Quick start

1. Run the program once. It creates `~/.xmousebutton/config.conf` with example bindings.
2. Edit that file, or pass your own with `--config`.
3. Check the file for mistakes:

   ```
   java -jar xmousebutton-0.1.0-all.jar --check --config my.conf
   ```

4. Start the remapper. On Windows it runs in the tray. Use **Reload config** from the tray menu
   after editing the file.

### Command line

```
xmousebutton [--config FILE] [--check] [--version]
```

| Option | Meaning |
|--------|---------|
| `--config FILE` | read bindings from FILE (default `~/.xmousebutton/config.conf`) |
| `--check` | parse the config, print errors with line numbers, and exit |
| `--version` | print the version |

## Configuration

```ini
# comments start with # or ;

[default]
x1 = key alt+left
x2 = key alt+right

[browser]
match.process = chrome.exe, msedge.exe, firefox.exe
match.title   = *YouTube*
wheel_left    = key alt+left
middle        = layer 2
layer2.left   = key ctrl+t
```

### Sections and matching

- `[name]` starts a profile. Names must be unique (case-insensitive).
- `match.process` and `match.title` take comma-separated glob patterns (`*` and `?`).
  Matching is case-insensitive. When both are set, both must match.
- A profile with no match rules never matches on its own. `[default]` is the fallback.
- Profiles are checked in the order they appear in the file.

### Buttons

`left`, `right`, `middle`, `x1`, `x2`, `wheel_up`, `wheel_down`, `wheel_left`, `wheel_right`

Wheel buttons only fire on scroll; they have no release.

### Actions

| Action | Example | Effect |
|--------|---------|--------|
| `passthrough` | `left = passthrough` | leave the input alone (this is the default for unbound buttons) |
| `block` | `right = block` | swallow the input and do nothing |
| `key` | `x1 = key ctrl+shift+tab` | hold the modifiers, tap the key, release |
| `click` | `middle = click left` | click a physical button |
| `wheel` | `wheel_up = wheel down 3` | scroll by 1 to 20 notches (`up`, `down`, `left`, `right`) |
| `run` | `middle = run "C:\Tools\my app.exe" -x` | start a program without waiting for it |
| `layer` | `middle = layer 2` | while the button is held, use the `layer2.*` bindings (layers 1 to 10) |
| `hold` | `x1 = hold 500 key ctrl+c \| key ctrl+v` | long press (100 to 10000 ms) runs the first action; a short tap runs the one after `\|` (default: nothing) |

Key names: `a`–`z`, `0`–`9`, `f1`–`f24`, `enter`, `tab`, `esc`, `space`, `backspace`, `delete`,
`insert`, `home`, `end`, `pageup`, `pagedown`, `left`, `up`, `right`, `down`, `plus`, `minus`,
`comma`, `period`, `printscreen`, `capslock`, `volume_up`, `volume_down`, `mute`,
`media_play_pause`, `media_next`, `media_prev`, `media_stop`, `browser_back`,
`browser_forward`, `browser_refresh`, `browser_home`. Modifiers: `ctrl`, `shift`, `alt`, `win`.

### Chords

`first+second = action` fires when `second` is pressed while `first` is held. Both releases are
swallowed, so neither button's own action is seen by Windows as a click.

```ini
[default]
left = block
right = block
left+right = key ctrl+c
```

Chords are checked in order: `left+right` and `right+left` are separate bindings. Only physical
buttons can be chorded. The first button still runs its own binding when pressed, so bind it to
`block`, `hold` or a layer if you want the chord to be silent. A chord cannot use `hold` or
`layer`.

### Hold (long press)

```ini
[default]
x1 = hold 500 key ctrl+shift+esc | key f5
```

Holding `x1` for 500 ms runs `key ctrl+shift+esc` while the button is still down. Releasing it
earlier runs `key f5`. Hold timers are cancelled when a chord uses the button. Hold does not work
on wheel buttons, and a hold action cannot be another hold or a layer.

### Layers

`layerN.button` binds a button only while layer N is active. A layer falls back to the base
binding for any button it does not define, so you only list what changes.

```ini
[editor]
match.process = Code.exe, idea64.exe
middle        = layer 2
layer2.left   = key f12
layer2.wheel_up   = key ctrl+plus
layer2.wheel_down = key ctrl+minus
```

## Known gaps compared with X-Mouse Button Control

These are not implemented yet:

- per-profile pointer speed, DPI and scroll-speed settings
- a graphical configuration editor (the config file is plain text)
- macros and text typing
- Linux and macOS input backends

## Building

Requirements: JDK 21 or newer. The Gradle wrapper is included.

```
./gradlew build          # compile and run the tests
./gradlew fatJar         # build build/libs/xmousebutton-<version>-all.jar
```

On Windows use `gradlew.bat` in place of `./gradlew`.

To create the app image that the release workflow produces:

```
jpackage --type app-image --name XMouseButton --input <dir with the fat jar> \
         --main-jar xmousebutton-<version>-all.jar \
         --main-class io.github.amjadsmarzug.xmousebutton.Main --dest dist
```

## Project layout

```
src/main/java/io/github/amjadsmarzug/xmousebutton/
  Main.java                 command line and startup
  model/                    buttons, actions, key combos, profiles, config
  config/                   file parser and action parser
  engine/                   turns raw events into actions, tracks presses and layers
  platform/                 OS boundary (Platform interface)
  platform/windows/         JNA backend: hook, key and mouse injection, window lookup
  ui/                       tray icon
src/main/resources/         default-config.conf (copied on first run)
src/test/java/              unit tests (no desktop needed)
```

## Adding a backend

Implement `io.github.amjadsmarzug.xmousebutton.platform.Platform` and add it to
`PlatformFactory.detect()`. The engine does not care how events arrive, only that
`startMouseHook` delivers `ButtonEvent`s and that `tapKeys`, `sendButton`, `sendWheel` and
`launch` do the work. Anything the backend injects must be filtered out before it reaches the
handler, otherwise remapped clicks would be remapped again.

## Continuous integration

- `.github/workflows/ci.yml` builds and tests on Ubuntu, Windows and macOS for every push to
  `main` and every pull request.
- `.github/workflows/release.yml` runs when a `v*` tag is pushed. It builds an app image for
  each OS, attaches the archives and the fat jar to a GitHub release, and writes checksums.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew build` before opening a pull
request. Keep changes to the Windows backend small and test them on a real Windows machine if
you can, because the unit tests cannot exercise the hook.

## License

MIT. See [LICENSE](LICENSE).
