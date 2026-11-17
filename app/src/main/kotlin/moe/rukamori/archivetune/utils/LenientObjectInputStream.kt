/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

class LenientObjectInputStream(
    input: InputStream,
) : ObjectInputStream(input) {
    override fun readClassDescriptor(): ObjectStreamClass {
        val stored = super.readClassDescriptor()
        if (!stored.name.startsWith(APP_PACKAGE)) return stored
        val local =
            runCatching {
                ObjectStreamClass.lookup(Class.forName(stored.name, false, LenientObjectInputStream::class.java.classLoader))
            }.getOrNull() ?: return stored
        return if (local.serialVersionUID != stored.serialVersionUID) local else stored
    }

    private companion object {
        const val APP_PACKAGE = "moe.rukamori.archivetune."
    }
}
