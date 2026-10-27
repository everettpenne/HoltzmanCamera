# Third-party notices

Holtzman is a fork of GrapheneOS Camera. The upstream project and its dependencies remain under their original licences; the fork's new code is MIT.

| Component | Licence | Where |
| --- | --- | --- |
| [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) | MIT | The upstream source, `LICENSE` |
| AndroidX / Jetpack, including CameraX | Apache-2.0 | Gradle dependencies; the vendored `androidxc/` classes are patched AndroidX code |
| Material Components for Android | Apache-2.0 | Gradle dependency |
| ZXing Core | Apache-2.0 | QR/barcode scanning |
| Kotlin / kotlin-stdlib | Apache-2.0 | The engine and the app |
| JUnit 4 | EPL-1.0 | Tests only (`:ix` and future app unit tests); not shipped |
| [Inter](https://github.com/rsms/inter) | SIL OFL 1.1 | Planned: the suite typeface. Add `docs/licenses/Inter-OFL.txt` with the font |
| [Glass](https://github.com/everettpenne/Glass) design tokens | MIT | Planned: colours, type scale and corner values copied into XML (not the Compose library) |

The upstream MIT notice is retained in [`LICENSE`](LICENSE). If a dependency is added, add a row here in the same commit.

Holtzman is not affiliated with or endorsed by GrapheneOS.
