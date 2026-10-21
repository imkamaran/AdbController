# Contributing to ADB Controller

Thanks for your interest in improving ADB Controller! Bug reports, feature ideas and pull requests are all welcome.

## Reporting bugs

Open an issue and include:

- The Android version and model of the phone running ADB Controller **and** of the target device
- How you connected (Wi-Fi, USB OTG, rooted or not)
- Steps to reproduce, what you expected, and what happened
- A screenshot or logcat excerpt if possible (remove any personal information first)

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep changes focused — one fix or feature per pull request.
3. Follow the existing code style: Kotlin, Jetpack Compose, Material 3. Run a build before submitting:
   ```bash
   ./gradlew assembleDebug
   ```
4. Test on a real device or emulator, and describe what you tested in the pull request.
5. Sign off every commit (see below).

## Legal

### License of contributions

ADB Controller is licensed under the [GNU General Public License v3.0](LICENSE). By submitting a contribution (code, documentation, artwork or any other material) you agree that it is licensed under the same GPL-3.0 license as the project, with no additional terms or conditions. There is no separate contributor license agreement, and you keep the copyright to your work.

### Developer Certificate of Origin

Every commit must be signed off to certify that you wrote the contribution or otherwise have the right to submit it under the project's license, as described in the [Developer Certificate of Origin 1.1](https://developercertificate.org/). Sign off by adding the `-s` flag:

```bash
git commit -s -m "Fix crash when the device disconnects during an upload"
```

This adds a line like the following to the commit message, using your real name and email:

```
Signed-off-by: Jane Doe <jane@example.com>
```

Pull requests with commits that are not signed off cannot be merged.

### Third-party code

Do not submit code copied from projects with licenses that are incompatible with GPL-3.0. If your contribution includes third-party code, state its source and license in the pull request.

### No warranty

As stated in sections 15 and 16 of the GPL-3.0, ADB Controller is provided "as is", without warranty of any kind. Contributors and the maintainer are not liable for any damage, data loss or device malfunction resulting from its use.

### Responsible use

ADB Controller is intended for managing devices that you own or are explicitly authorized to administer. Contributions whose purpose is to access devices without the owner's consent, or to bypass security protections on devices you are not authorized to manage, will not be accepted.

## Code of conduct

Be respectful and constructive. Harassment or personal attacks of any kind are not tolerated in issues, pull requests or discussions.
