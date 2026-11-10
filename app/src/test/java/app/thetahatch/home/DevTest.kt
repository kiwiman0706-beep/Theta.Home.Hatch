package app.thetahatch.home

import app.thetahatch.home.debug.DebugReceiver
import app.thetahatch.home.ui.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 試験版（β）の版の比べ方と、adb から触れない秘密の項目。 */
class DevTest {
    @Test
    fun betaVersions() {
        assertTrue(isNewer("1.4.0-beta.1", "1.3.1"))
        assertTrue(isNewer("1.4.0-beta.2", "1.4.0-beta.1"))
        assertTrue(isNewer("1.4.0", "1.4.0-beta.2"))
        assertFalse(isNewer("1.4.0-beta.2", "1.4.0"))
        assertFalse(isNewer("1.3.1", "1.3.1"))
        assertTrue(isNewer("1.10.0", "1.9.2"))
    }

    @Test
    fun secretsAreHidden() {
        assertTrue(DebugReceiver.secret("geminiKey"))
        assertTrue(DebugReceiver.secret("nasPassword"))
        assertTrue(DebugReceiver.secret("nasDid"))
        assertFalse(DebugReceiver.secret("theme"))
        assertFalse(DebugReceiver.secret("homeSections"))
        assertFalse(DebugReceiver.secret("weatherDetail"))
    }
}
