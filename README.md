> **:warning: ᴛʜɪs ʀᴇᴘᴏsɪᴛᴏʀʏ ɪs ᴅᴇᴘʀᴇᴄᴀᴛᴇᴅ :warning:**
>
> We moved all the `r2-*-kotlin` modules to a single repository: [`kotlin-toolkit`](https://github.com/readium/kotlin-toolkit).

# Readium Mobile Test App (Kotlin/Android)

A test app for the Kotlin implementation of Readium-2. Stable builds are [available on Google Play](https://play.google.com/apps/testing/org.readium.r2reader).

[![BSD-3](https://img.shields.io/badge/License-BSD--3-brightgreen.svg)](https://opensource.org/licenses/BSD-3-Clause)

All migration steps necessary in reading apps to upgrade to major versions of the Readium toolkit will be documented in the [Migration Guide](https://readium.org/mobile/kotlin/migration-guide).

## Features

- [x] EPUB 2.x and 3.x support
- [x] Readium LCP support
- [x] CBZ support
- [x] Custom styles
- [x] Night & sepia modes
- [x] Pagination and scrolling
- [x] Table of contents
- [x] OPDS 1.x and 2.0 support
- [x] EPUB FXL support
- [x] EPUB RTL support


## Dependencies

- [Shared Models](https://github.com/readium/r2-shared-kotlin) (Model, shared for both streamer and navigator) [![Release](https://jitpack.io/v/readium/r2-shared-kotlin.svg)](https://jitpack.io/#readium/r2-shared-kotlin)
- [Streamer](https://github.com/readium/r2-streamer-kotlin) (The parser/server) [![Release](https://jitpack.io/v/readium/r2-streamer-kotlin.svg)](https://jitpack.io/#readium/r2-streamer-kotlin) 
- [Navigator](https://github.com/readium/r2-navigator-kotlin) (The bare ViewControllers for displaying parsed resources) [![Release](https://jitpack.io/v/readium/r2-navigator-kotlin.svg)](https://jitpack.io/#readium/r2-navigator-kotlin)
- [Readium CSS](https://github.com/readium/readium-css) (Handles styles, layout and user settings)

## Install and run the testapp
[Get started with the testapp](https://github.com/readium/r2-workspace-kotlin/blob/master/README.md)


