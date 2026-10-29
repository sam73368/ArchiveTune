package moe.archivetune.server

object WebAssets {
    val indexHtml: String by lazy { load("web/index.html") }
    val manifest: String by lazy { load("web/manifest.webmanifest") }

    private fun load(path: String): String =
        WebAssets::class.java.classLoader
            .getResourceAsStream(path)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("missing resource $path")
}
