plugins {
    id("dev.kikugie.stonecutter")
    // Both ids come from the same Loom: remap for the obfuscated versions (up to 1.21.11), plain for unobfuscated 26.x.
    // build.gradle applies the one each version needs.
    id("net.fabricmc.fabric-loom-remap") version "1.18.2" apply false
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
}
stonecutter active "1.21.1"
