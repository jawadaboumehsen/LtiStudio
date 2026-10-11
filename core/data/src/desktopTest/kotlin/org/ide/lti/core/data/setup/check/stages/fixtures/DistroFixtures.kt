/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages.fixtures

object DistroFixtures {
    val WSL_LV_REAL =
        """
          NAME                   STATE           VERSION
        * Ubuntu                 Running         2
          docker-desktop         Stopped         2
        """.trimIndent()

    val WSL_STATUS_REAL =
        """
        Default Distribution: Ubuntu
        Default Version: 2
        """.trimIndent()

    val OS_RELEASE_UBUNTU_24_04 =
        """
        PRETTY_NAME="Ubuntu 24.04.4 LTS"
        NAME="Ubuntu"
        VERSION_ID="24.04"
        VERSION="24.04.4 LTS (Noble Numbat)"
        VERSION_CODENAME=noble
        ID=ubuntu
        ID_LIKE=debian
        HOME_URL="https://www.ubuntu.com/"
        SUPPORT_URL="https://help.ubuntu.com/"
        BUG_REPORT_URL="https://bugs.launchpad.net/ubuntu/"
        PRIVACY_POLICY_URL="https://www.ubuntu.com/legal/terms-and-policies/privacy-policy"
        UBUNTU_CODENAME=noble
        LOGO=ubuntu-logo
        """.trimIndent()

    val OS_RELEASE_UBUNTU_22_04 =
        """
        PRETTY_NAME="Ubuntu 22.04.3 LTS"
        NAME="Ubuntu"
        VERSION_ID="22.04"
        VERSION="22.04.3 LTS (Jammy Jellyfish)"
        VERSION_CODENAME=jammy
        ID=ubuntu
        ID_LIKE=debian
        HOME_URL="https://www.ubuntu.com/"
        SUPPORT_URL="https://help.ubuntu.com/"
        BUG_REPORT_URL="https://bugs.launchpad.net/ubuntu/"
        PRIVACY_POLICY_URL="https://www.ubuntu.com/legal/terms-and-policies/privacy-policy"
        UBUNTU_CODENAME=jammy
        LOGO=ubuntu-logo
        """.trimIndent()

    val OS_RELEASE_UBUNTU_25_04 =
        """
        PRETTY_NAME="Ubuntu 25.04"
        NAME="Ubuntu"
        VERSION_ID="25.04"
        VERSION="25.04 (Plucky Puffin)"
        VERSION_CODENAME=plucky
        ID=ubuntu
        ID_LIKE=debian
        HOME_URL="https://www.ubuntu.com/"
        SUPPORT_URL="https://help.ubuntu.com/"
        BUG_REPORT_URL="https://bugs.launchpad.net/ubuntu/"
        PRIVACY_POLICY_URL="https://www.ubuntu.com/legal/terms-and-policies/privacy-policy"
        UBUNTU_CODENAME=plucky
        LOGO=ubuntu-logo
        """.trimIndent()

    val OS_RELEASE_DEBIAN =
        """
        PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
        NAME="Debian GNU/Linux"
        VERSION_ID="12"
        VERSION="12 (bookworm)"
        VERSION_CODENAME=bookworm
        ID=debian
        HOME_URL="https://www.debian.org/"
        SUPPORT_URL="https://www.debian.org/support"
        BUG_REPORT_URL="https://bugs.debian.org/"
        """.trimIndent()
}
