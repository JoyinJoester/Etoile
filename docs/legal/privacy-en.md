# Privacy and data

Etoile is an independent Android client for GitHub and GitLab. This notice describes the current source implementation; third-party services apply their own terms and privacy policies. Updated: 2026-10-21.

## Accounts and authorization

Your device connects to GitHub or the GitLab instance you choose to sign in, read content and perform actions you request. Access tokens, refresh tokens and pending OAuth validation data are kept in Android Keystore-backed encrypted preferences. GitHub and GitLab keep separate account stores. A build configured with a GitHub authentication service also sends the authorization exchange and refresh requests to that configured service; its operator controls service-side processing and retention.

Etoile does not ask for your service password directly. Browser and embedded sign-in pages belong to the selected provider. Release builds embed no GitHub OAuth client secret; browser authorization requires a deployed authentication service. Debug builds can embed a secret for compatibility with personal OAuth Apps; that secret cannot remain confidential in an installation package.

## Content stored on your device

The application stores account profiles, settings, locally organized Stars, drafts, reading caches, selected application sources and downloaded packages in its private storage. GitHub response caches are encrypted. Drafts and other local preferences are not all encrypted independently. Draft restoration depends on the screen and Android task lifecycle; it is not an external backup service. Android application backup is disabled.

Removing an account deletes its local credential, but does not delete your GitHub / GitLab account or revoke every authorization at the provider. Revoke the application or token in the provider's security settings when needed. Clear app storage or uninstall to remove remaining application data and downloads. Android and the destination installer govern copies outside this application's storage.

## Network requests and third parties

GitHub and your chosen GitLab instance receive the API requests necessary for the features you use. Opening the application store can fetch catalogues from the enabled F-Droid sources. Downloading a package contacts its hosting service. Avatars and images in Markdown can contact the image hosts named in that content. These services receive ordinary connection data such as your IP address and requested resource. External browser pages and installed applications follow their own policies.

The current dependency configuration contains no advertising or analytics SDK. The application does not automatically upload diagnostics to the developer. If you send a bug report, remove tokens, private repository content and personal information from logs and screenshots first. Do not put credentials in a public issue.

## Permissions

Internet and network-state permissions support API and download requests. Notification permission supports optional sign-in code notifications; sign-in does not require granting it. Package-install permission is used only when you choose to hand a downloaded package to Android's system installer; Android may require a separate per-app approval. Installed-version checks are limited by Android package visibility.

## Questions and changes

The project source and public issue tracker are available at https://github.com/JoyinJoester/Etoile. For a privacy concern, avoid posting private data publicly; first request a private contact channel from the maintainer. This notice must be reviewed when service endpoints, storage, permissions or third-party dependencies change.
