package com.tmplayer.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemInstallerTest {
    @Before fun resetProviderPathCache() {
        // Robolectric gives each test a new data directory but FileProvider caches by authority.
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<String, Any>>(
            androidx.core.content.FileProvider::class.java, "sCache").clear()
    }
    private fun check(tv: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val file = File(app.cacheDir, "updates/test-update.apk").apply { parentFile.mkdirs(); writeText("test") }
        FormFactor.override(tv)
        try {
            assertTrue("permission Settings must not prevent an installer handoff", Updates.canInstall(app))
            val intent = SelfInstaller.viewIntent(app, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data?.scheme)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally { FormFactor.override(false); file.delete() }
    }
    @Test fun phoneAndTabletOpenNativeInstaller() = check(false)
    @Test fun televisionOpensNativeInstaller() = check(true)
}
