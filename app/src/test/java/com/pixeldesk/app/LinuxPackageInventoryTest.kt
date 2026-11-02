package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxPackageInventoryTest {
    @Test
    fun parsesAndSortsInstalledPackageRows() {
        val packages = LinuxPackageInventory.parse(
            "zlib1g:arm64\t1.2.13-1\tinstalled\n" +
                "curl\t7.88.1-10\tinstalled\n" +
                "bash\t5.2.15-2\tinstalled\n"
        )
        assertEquals(listOf("bash", "curl", "zlib1g:arm64"), packages.map { it.name })
        assertEquals("7.88.1-10", packages[1].version)
    }

    @Test
    fun ignoresHeadersMalformedRowsAndUninstalledPackages() {
        val packages = LinuxPackageInventory.parse(
            "Listing... Done\n" +
                "bad name\t1.0\tinstalled\n" +
                "-invalid\t2.0\tinstalled\n" +
                "valid-package\n" +
                "valid-package\t\tinstalled\n" +
                "removed-package\t1.2\tconfig-files\n" +
                "valid-package\t1.2\tinstalled\n"
        )
        assertEquals(listOf("valid-package"), packages.map { it.name })
    }

    @Test
    fun deduplicatesPackageNamesAndSupportsArchitectureSuffix() {
        val packages = LinuxPackageInventory.parse(
            "libc6:arm64\t2.36\tinstalled\n" +
                "libc6:arm64\t2.36\tinstalled\n"
        )
        assertEquals(1, packages.size)
        assertTrue(packages.single().name.endsWith(":arm64"))
    }
}
