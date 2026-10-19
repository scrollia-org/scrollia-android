/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scrollia.app.helpers

object Constants {
    /** Name of database file. */
    const val DATABASE_NAME = "scrollia.db"

    /** Epub Mime Type. */
    const val EPUB_MIME_TYPE = "application/epub+zip"

    /** Placeholder for unknown error. */
    const val UNKNOWN_ERR = "unknown-error"

    // Public-facing URLs.
    const val DEV_EMAIL = "contact@scrollia.org"
    const val WEBSITE = "https://scrollia.org"
    const val PRIVACY_POLICY = "https://scrollia.org/privacy"

    // TODO: Restore these once the resources exist publicly. The GitHub org
    //  (github.com/scrollia-org) is private for now, and there is no Telegram
    //  community or sponsorship page yet. The matching entries in AboutScreen
    //  are hidden until then.
    //  - DEV_GITHUB_URL / GITHUB_REPO / PROJECT_CONTRIBUTORS / GITHUB_ISSUE
    //  - DEV_TELEGRAM_URL / TELEGRAM_GROUP
    //  - SUPPORT (sponsorship)
}