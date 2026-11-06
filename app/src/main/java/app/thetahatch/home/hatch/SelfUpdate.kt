package app.thetahatch.home.hatch

import app.thetahatch.home.hub.Catalog

/** このアプリ自身の配布先と、開発の会話。更新そのものは更新のハブ（hub/UpdateHub）が行う。 */
object SelfUpdate {
    const val REPO = "Theta.Home.Hatch"
    /** 機能追加を頼むときに開く、開発の会話。 */
    const val REQUEST_URL = "https://claude.ai/code/session_01GMSgoGarRChJJQLGtjAFKY"
    const val RELEASES_URL = "https://github.com/${Catalog.OWNER}/$REPO/releases"
}
