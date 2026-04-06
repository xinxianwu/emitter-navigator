<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Emitter Navigator Changelog

## [Unreleased]

### Added
- F12 Go-to-Declaration navigation between emit/on call pairs across the project
- Results popup sorted by current file first, then by line number
- Support for `.js`, `.ts`, `.jsx`, `.tsx`, `.vue` files
- Settings UI under **Settings → Tools → Emitter Navigation** to customize emit/on method names
- Per-method event argument index configuration (e.g. `send_io_room:1`)
- Methods without an explicit index match any string argument (up to 9 arguments)
- Variable reference support: `const key = "event"` can be used as the event name argument
- Context menu action **Find Emitter Pairs** to summarize emit/on pairs in the current file