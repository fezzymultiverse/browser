## V1.26.0
- Long-press a link: a small popup with the address and two buttons, Copy Link and Open in New Tab (Background tab and Download link are small buttons under them). Images keep their longer menu. Tested: page script syntax only; not run on a device.
- URL bar ("Laser Focus"): tap it to type and the accent border glows brighter and pulses slowly; Reduce motion turns the pulse off. CSS only.
- Downloads: every finished download now also keeps a copy inside the browser (app storage) next to the one in the phone's Downloads. Open uses the browser copy first, then the Downloads link, then a search by name. The browser copy is deleted when the download is removed from the list (Delete, Clear finished, Kill Switch, Blaze). Downloads made before this version have no browser copy. Downloads manager lists "Saved by the browser" with an Open button. Texts that still said Downloads/Festers now say Downloads. The Files list still only reads Downloads/Festers (older files and zip extractions). Native; not compiled, not run on a device. FileProvider path for the browser copy depends on Capacitor's default file_paths.xml (unverified).
- Pull down to refresh: no spinner, nothing drawn while pulling. After the pull is accepted the page reloads and a wide panel "Your Page Is Being Restored / Bojack Security" drops in from the top, stays at least 1.3 s and lifts away when the page finishes (5 s at most). New guard: if the finger started inside a box on the page that is itself scrolled (chat, feed, panel) it is treated as scrolling and never refreshes. Native (FBWebPlugin); not compiled, not run on a device.
- Browser Lock: new Settings section "Browser Lock" (4-digit PIN, optional fingerprint, Lock when the app opens, Lock now, Remove lock). A lock icon sits on the Security posture panel: green with a soft glow = open (tap to lock), red = locked. Locked shows a cover over the whole browser with the PIN pad and the fingerprint prompt (tap the lock to ask again). Kill Switch locks the browser when it finishes. No timers; it only starts locked when the app is opened from closed (switchable). 5 wrong PINs make a 30 s wait. PIN stored as a salted PBKDF2 hash, never the PIN. No reset. Fingerprint uses the new FBVault.lockAuth (androidx.biometric, already in the build). PIN logic tested in Node only; native fingerprint and the cover not run on a device.
- Start page: the "Recent websites" list at the bottom is removed (History already has it).
- Not compiled, not run on a device.
## V1.25.0
- Pull down to refresh: the chip animation is gone. Pulling down past the line (same dead zone and hold as before) just reloads the page, nothing is drawn.
- LLM icon in the bottom bar is now a brain-on-a-chip logo (label still LLM).
- Downloads now save straight into the phone's Downloads folder, not Downloads/Festers (downloader, in-page saves, media saves, setup export). Files already in Downloads/Festers are still found.
- Downloads > Open first tries the saved link directly instead of pre-checking it (that check could make "gone" appear for files that were there); it only searches by name if that fails. Not verified on a device.
- Not compiled, not run on a device.
## V1.24.0
- Pull down to refresh reworked: a dark rounded chip with a red edge that traces as you pull and an orange reload arrow. Much harder to trigger: dead zone, resistance (about 2.5 cm of travel) and a short hold are needed. Native (FBWebPlugin).
- Bottom bar: Settings icon red. LLM icon is now the initials AI instead of the G.
- AI keys: "Paste a note with your keys" (Settings > AI and the AI panel). Picks out every Groq and Gemini key, saves new ones, tests them, shows a health bar per key plus an All AI keys meter and Test again.
- Downloads: Open now also searches Downloads by name anywhere, "name (1)" renames and the plain file; if still not found it opens the Downloads app and names the file instead of just saying it is gone. Not verified on a device.
- Burner icon is a dog paw with nails (tab, start page, quick sheet).
- Start page under the clock: Wi-Fi name, and while a VPN is on, its exit place (city, region, postcode, country, provider) from a free IP lookup. IP lookup cannot give a street address. Toggle: Settings > New tab.
- Kill Switch, Scorch, Burn and Blaze dialogs have an Animation preview switch: plays the animation, erases nothing.
- Start page search box says "Bojack Security · search or enter an address".
- Not compiled, not run on a device.
## V1.23.12
- CI fix: the GitLab build failed at `npm install` because @capacitor/assets pulls in sharp, whose install downloads libvips from GitHub and timed out. @capacitor/assets is no longer a dependency; the pipeline installs it separately (`--no-save`, 3 tries) and carries on with the default icons if that still fails. npm now retries network fetches. No app change.
## V1.23.11
- Pull down to refresh a web page: drag down while the page is at the very top, a small round cyan spinner drops in, let go past the threshold and the page reloads while it spins; it lifts away when the page finishes (or after 8 s). Native (FBWebPlugin: attachPull, PtrView), no new library, the page still receives every touch. Not compiled, not run on a device. Open: pages that are their own full-screen scroller (maps, web apps) can start a refresh when dragged down at their top.
## V1.23.10
- Autofill & Passkeys status now says where Conduint takes the fingerprint (Conduint V0.52.1 > Passkeys & autofill > Trusted apps). Text only; no native change.
## V1.23.9
- Kill Switch now asks first: a prompt explains what it clears and what it keeps, with Deny and Approve. Approve runs it. The lightning is gone: the screen flashes dark, a warm light glows behind two pop-ups ("Strategy Over Impulse", then a dog paw with "Fezzy Browser Cleared"). Driven from JS, so Reduce motion cannot blank it.
- Dashboard: the four tiles (Kill Switch, Scorch, Burn, Blaze) are removed from the start page.
- New: swipe up on the bottom bar (orange handle on its top edge) for a Quick actions sheet: Kill Switch, Burn, Scorch, Blaze and Burner window (the private window). The menu and Settings entries are unchanged.
- Fix: Burn and Scorch glitching. The fire map is coarser on a phone (fewer cells to redraw each frame), the per-cell trig is precomputed, and the page no longer shakes behind the fire (the native web view could not shake with it).
- Fix: dialog buttons wrap onto a second row instead of running off the left edge (Autofill and passkeys: "Choose autofill service" was cut off).
- Change: the bottom bar is plain again (default text colour, no glow).
- Not compiled, not run on a device. Passes a headless-Chromium run of the sheet, the Kill Switch prompt and the effect.
## V1.23.8
- Kill Switch (new tile under the clock on the dashboard, also in the site menu and Settings): one tap clears tabs, history, cookies, cache, the downloads list and the saved session. The vault, bookmarks, notes, collections and settings are kept. Plays electric sparks across the screen, then a "Kill Switch" toast.
- Scorch, Burn and Blaze tiles sit under Kill Switch, each with its own icon and idle glow. Scorch and Burn keep their existing dialogs and fire effects and stay in Settings and the menu.
- Blaze (new): total wipe of everything including the vault, then the app restarts blank. Tap it first: it explains itself with its icon and name, and "Show animation" plays the flames and smoke WITHOUT erasing anything. "Blaze now" asks "Are you sure?", then you must type BLAZE in capitals. Also in Settings and the menu. The durable settings mirror is deleted first so the recovery script cannot bring data back.
- Double-tap the Bojack mascot: Privacy and Security sheet (mascot icon on top; why it matters, your IP address, hackers, your digital footprint, what Festers does).
- Bookmarks: renamable, can be put into groups (new group, rename group, remove group), shown grouped in the Bookmarks panel and as a Bookmarks group in the site menu.
- Tab colour groups: long-press a tab (or Site menu > Tab colour group) to pick a colour and optional group name; same-colour tabs stay together, colour is kept after a restart.
- Vault only locks when you press Lock (auto-lock set to Never once; the setting stays). Fingerprint first, 4-digit PIN as the backup, as before.
- Downloads: Open now finds the saved file again (saved link, then a MediaStore search of Downloads/Festers, then the plain file) so it no longer says the file is gone. Images keep a real extension for every type (jpg, png, gif, webp, avif, heic, heif, svg, bmp, ico, tiff, jxl), in the app and in the downloader.
- Look: solid cyan caret in the address bar and search box; neon sweep line across the top plus a pulsing Bojack paw in the address bar while a page loads; thin glowing cyan scrollbar (app and native page view); purple glow around the whole screen, including over web pages, while the vault is unlocked.
- Tracker badge: Android only draws an icon badge for a live notification, so a quiet "N trackers blocked today" notification carries the count (channel "Blocked trackers badge"; needs notification permission; S.badge=false turns it off).
- Not compiled, not run on a device. index.html passes node --check and a headless-Chromium run of Kill Switch, Blaze (demo and real wipe), the privacy sheet, tab colours and bookmark rename/groups. Java (FBDownloader, FBSysPlugin, FBWebPlugin) is brace-balance checked only.

## V1.23.7
- Bottom bar colours: LLM orange (was Groq), Media cyan, Bookmarks and Sets orange, Downloads green, Vault purple, Settings red. Scorch (red) and Security (blue) are coloured too and show their colour wherever they appear. Icons glow in their colour, the open item gets a tinted background and a coloured underline.
- Links from other apps: root cause of "opens and stands still" was that the browser intent filter existed but nothing ever read the link. MainActivity now keeps the http(s) link from the intent (cold start, onNewIntent, onResume), FBSys.launchUrl hands it to the page once, and the page opens it in a new tab (closing any open panel first). The CI script also makes sure the launcher activity is singleTask.
- Media rewritten. Categories (All, Music, Video, Images, Docs) with counts, every category is a drop-down group with an arrow, Tiles/Grid/Detailed views, neat square thumbnails: image previews, video frame, album art for music, colour-coded type tiles for documents.
- Audio metadata reader written in plain JS: ID3v1/v2 (MP3), MP4/M4A, FLAC, Ogg Vorbis and Opus, WAV. Shows title, artist, album, album artist, year, genre, track, disc, composer, label, BPM, key, format, bitrate, sample rate, channels, duration, size, modified, encoder, ISRC, copyright, lyrics flag and comment, plus embedded album art (also as a blurred backdrop and on the lock screen through Media Session). Previous/Next buttons.
- "Did you know" pill: facts about the artist drop in like a pill, a red bar shows when there is more text and the text scrolls through, then the pill lifts away. One every minute while music plays. Modes: Pill (drops over the panel), In player (reserved space in the player card), Off. Facts come from Wikipedia (only the artist name is sent, cached 30 days), mixed with facts from the file's own tags. Works offline with the tag facts.
- Not compiled, not run on a device. JS passes node --check; the tag readers were tested in Node with synthetic ID3, MP4, FLAC and Ogg files.

## V1.23.6
- Downloads panel: new Downloads button on the bottom bar with two tabs, Downloads and History. Downloads shows percentage, speed, time left, Pause, Resume, Cancel, Open, Copy link, Delete file; paste a link to download it. History is searchable by day. A badge on the button counts active downloads. Uses the new native engine (FBDownloader: Range resume, retry, 3 at once, Downloads/Festers).
- Bottom bar is now AI, Media, Bookmarks, Downloads, Sets, Vault, Settings. Scorch and Security left the bar: Scorch and Burn are in Settings > Security Console and Privacy.
- Security Console: every security tool (Breach, DNS, VPN, Offline, Backup, Updates, Export, Passkeys) now lives in Settings > Security Console, under the posture score and protection switches. Anything that used to open the Security panel opens the console.
- Burn rewritten as a scorch. Root cause of the black screen: Reduce motion sets html.rm *{animation:none!important}; the old overlay was solid black and used CSS animations to fade and to show its label, so with Reduce motion on you got black and no text. The new Burn has no CSS animation. Fire starts at the bottom and the middle, an orange-white front eats across the screen leaving black char, the char smoulders, turns to ash and crumbles away while hot ash and embers drift up. With Reduce motion on it plays shorter with no flash or shake. Scorch still honours Reduce motion.
- Collections: colour tags (10 colours), a colour dot on each collection, filter chips by colour.
- Vault: Import from JSON for Bitwarden, 1Password (export.data), Proton Pass, Dashlane, Keeper, Enpass and generic JSON. Encrypted exports are refused with the right instructions. Duplicates are skipped.
- Autofill and passkeys status names Conduint with separate On/Off pills, and has an Open Conduint button. The setup script adds the <queries> entry so Festers can see Conduint on Android 11+.
- Back: on the phone, Back now closes an open Settings section first, and when nothing is left the first press shows "Press Back again to leave Festers" and only the second leaves. In a plain browser, Back walks the same steps through a history guard instead of leaving the page.
- versionCode 12306.
- Not compiled, not run on a device. JS passes node --check. Open: Burn look and speed on the Honor X5b, FBDownloader on device, Back double press.

## V1.23.5
- Downloads showed no sign of life: toast() wrote to a shell element that the native page view covers, so Downloading, Saved and every failure reason were invisible while a page was open. They now show as Android toasts (FBWebPlugin.nativeToast). The first download asks for the notification permission so Android can show its own progress bar and Download complete. versionCode 12305.
- Not compiled, not run on a device.

## V1.23.4
- Build fix: FBWebPlugin.java line 951 had a comment written as `{ }: a cancelled load ...` with no `//`, so javac stopped with 9 compile errors in :app:compileDebugJavaWithJavac and the V1.23.3 build produced no APK. The comment is now `{ } // a cancelled load ...`. No behaviour change.
- Project documents for GitLab: MIT LICENSE kept (and `"license": "MIT"` added to package.json), CODE_OF_CONDUCT.md, expanded CONTRIBUTING.md and SECURITY.md (GitLab wording), .gitlab/ issue templates (Bug, Feature request), merge request template and CODEOWNERS, and a wiki/ folder with ready-to-push GitLab wiki pages.
- versionCode 12304 (derived from package.json by ci/android-setup.sh).
- Not compiled, not run on a device. The V1.23.3 items (Back/Forward direction, Burn) are still untested on a device.

## V1.23.3
- Back and Forward: the phone now tells the page which way a navigation went (FBWebPlugin markDir/takeDir, sent as "dir" with the page event). Before, the page guessed with a 2.5 second timer; a slow site could commit after it expired, so Back looked like a brand new page and the Forward list was thrown away. The hardware Back fallback (goBackActive) never told the page at all and now does. The toolbar Back/Forward, the menu buttons and the Android Back gesture all use the same path.
- Burn: always plays (Reduce motion no longer skips it; Scorch still honours it). The flames start the moment you tap Burn and the wipe happens underneath. Ash now burns red-hot: every flake has a glowing halo and keeps a hot red-orange body until it fades, instead of cooling to grey in a second or two; embers cool more slowly; the falling ash lasts longer and is thicker.
- Not compiled, not run on a device. JS passes node --check; Java brace balance checked. Open: Back/Forward on a slow site on device; cold-start scroll restore still to do.

## V1.23.2
- Conduint passkeys: Festers now has its half of the Conduint passkey bridge (Conduint V0.50.9 or newer, tested against the V0.51.2 source). A page script (document-start, navigator.credentials shim) hands passkey create/get requests to FBWebPlugin.PkBridge, which starts Conduint's FestersBridgeActivity with startActivityForResult, using the page origin read in Java from the main-frame URL. MainActivity.onActivityResult returns Conduint's answer to the page as a normal WebAuthn credential. No fingerprint has to be pasted anywhere: Conduint shows the Festers certificate and pins it after one confirmation.
- The bridge steps aside (the page's normal navigator.credentials runs) in burner tabs, on sites in the passkey skip list (Google, GitHub, GitLab by default), when Web passkeys is off, on non-https pages, for conditional-UI requests, and when Conduint is missing or too old.
- Fixed Festers' own wrong guidance: the Trust Festers row and the status note no longer tell you to paste a fingerprint into Conduint (CONDUINT_TRUST_FESTERS_SHA256 is an optional GitLab CI variable, not an in-app field).
- Not compiled, not run on a device. JS passes node --check; Java brace balance checked. Risks: MainActivity.onActivityResult override signature against Capacitor 7's BridgeActivity; whether the document-start shim loads on the device WebView.

## V1.23.1
- Downloads: Festers now has its own downloader as a safety net. If Android's DownloadManager refuses a job, or queues it and then fails it, Festers downloads the file itself (page cookies, user agent and referer, redirects followed, streamed into Downloads/Festers) and shows the real reason if that fails too. blob: downloads that navigate are now fetched by the page and saved. Save image and Download link on long-press no longer need the Media Downloader extension. Toast says "Saved <name> in Downloads/Festers" when a Festers download finishes.
- Cookies: new Cookies setting (Allow all, Block third-party, Block all) in Settings > Site Permissions and Privacy & Security, sent to the native view on every page open. New Cookies item in the page menu shows this site's cookie names, changes the mode, and deletes this site's cookies. Clear all cookies (and Delete Browsing Data > Cookies and site data) wipe website cookies and site data and keep the browser's own bookmarks, notes and vault.
- Vault: every password box (create vault, unlock, change master password, backups) now has a show/hide eye. The entry editor keeps its own eye.
- Not compiled, not run on a device. JS passes node --check; Java brace balance checked. Open: does a download land in Downloads/Festers on device; does Block third-party stop a third-party cookie; Back/Forward and scroll restore still to do.

## V1.23.0
- Certificates: Festers now repairs "untrusted" chains the way Chrome does. It reads the certificate's AIA field, downloads the missing intermediate certificate and validates the rebuilt chain against the phone's trust store (native/FBCertHelper.java). Forged or self-signed certificates still fail. User-installed certificate authorities are trusted (ci/network_security_config.xml). Continue anyway now works for every certificate problem (expired, wrong name, bad date) after showing issuer, dates and SHA-256; "Allow this time" or "Allow site".
- HTTPS-only falls back to http for sites with no working HTTPS (old government sites) and remembers the host (Settings > Sites kept on http).
- Burn: rewritten as an explosion. White-hot flash, shockwave rings, fireball, hundreds of hot red embers and ash flakes thrown outward that cool to grey ash, screen shake, haptics, S.O.I. x Bojack 999 tag.
- Data loss ROOT CAUSE fixed: clearData called WebStorage.deleteAllData(), which also erased the app's own localStorage (bookmarks, collections, notes, settings, vault). Now only website origins are deleted, JS snapshots and restores the app's keys around it, and every save is mirrored to fb-state.json in the app's private files with automatic recovery on start.
- Passkeys: Web passkeys is ON by default, switched per site (Google, GitHub, GitLab stay off, editable list). Autofill and passkeys status now shows On/Off pills for passkey app, Web passkeys and autofill service separately, with what to do next. Passkey-only providers (Conduint) no longer read as "off".
- Same page on return: Restore tabs on start is forced on once; the session now keeps each tab's back list, restore no longer overwrites itself mid-way.
- Collections: accordion (tap to expand, remembered), rename collection, edit name and address of any site, move up/down, remove, Expand/Collapse all.
- Not compiled, not run on a device. JS passes node --check; Java brace balance checked.

## V1.22.7
- Certificate errors no longer wipe the page for sub-resources. Prodigy's WebSheet (syncing_V1) has no SSL handler at all; Android's stock client just refuses the bad request and the page still loads, and srd.sassa.gov.za loads there. Festers replaced the whole page with "Secure connection failed" for ANY certificate error, including one on a script, image or API call. Now only a failure of the page itself (same URL as the last main-frame request) shows the error page; others are refused silently and shown as "Last blocked host" in Page check.
- Correction to V1.22.6: its note that SASSA "leaves out its intermediate certificate" was an assumption. Prodigy loading the same site with stock validation argues against it. Continue anyway stays (untrusted chain only, main page only, per site, with a warning) but may not be needed for SASSA.
- Compared with WebSheet: Prodigy keeps JavaScript on always (toggle off by default), third-party cookies on, no HTTPS-only rewrite, no per-navigation script policy, no renderer replacement, no page-replacing error pages. Festers NoScript is off by default; the "basic view" (sandboxed iframe, shows "Blank page? This site may block embedding") is only used when the native open call fails.
- Java brace/paren balance checked only. Not compiled, not run on a device.

## V1.22.6
- Burn and Scorch: real flame tongues now rise along the bottom edge with the ash and embers, and a flame icon with the word BURN (SCORCH for Scorch) shows in the middle. Reduced motion still skips it.
- Forward: Festers now keeps its own forward list per tab (native FBWebPlugin.fwdStack). Back pushes the page you left; Forward uses WebView goForward and, when WebView says it cannot, loads that saved page. Opening a new page clears the list. Stale back/forward markers expire after 2.5 s. Root cause of the dead Forward button was NOT confirmed on a device.
- Google sign-in (popups): onCreateWindow now hands the page a real tab view (popupId) instead of discarding it and reopening the URL, so window.opener, postMessage and window.close() work (onCloseWindow closes the tab and returns to the opener). Popups without a tap are still refused except from Google, YouTube, GitLab and GitHub hosts. This is the leading suspect for the account tap that just refreshed; not confirmed on a device.
- Certificate errors: srd.sassa.gov.za (and other sites that leave out their intermediate certificate) fail with "untrusted". The error bar now offers Continue anyway for the untrusted-chain error only, per site, after a warning. Expired, wrong-name and wrong-date errors still cannot be bypassed. Allowed sites persist (fb sslAllow), are passed to native on every open (sslHosts) and are listed and clearable in Settings > Autofill & Passkeys > Certificate exceptions.
- Autofill status: reads the real autofill service and passkey providers from Android (autofill_service, credential_service, credential_service_primary) instead of only hasEnabledAutofillServices(), says when Conduint is a passkey provider but not the autofill service, and says when Web passkeys is off (the default since V1.22.5, which stops Conduint being asked at all).
- Java brace/paren balance and page script node --check only. Not compiled, not run on a device.

## V1.22.5
- Passkeys: Festers had no page-level passkey bridge and advertised WebView passkey support in app mode (sideloaded apps never get CREDENTIAL_MANAGER_SET_ORIGIN). Sites like Google, GitHub and GitLab then tried a passkey that Android refuses for an app not in their assetlinks.json. Prodigy WebSheet enables none of this and Google loads there. Web passkeys is now a setting, off by default (Settings > Autofill & Passkeys).
- Google sign-in no longer forces Chrome-style client hints; Prodigy sends the WebView defaults. The Browser identity hints setting still applies to every site when set to Chrome-style.
- Shields no longer block consent managers (cookielaw, onetrust, trustarc), chat widgets (intercom, drift, zopim, tawk, livechat) or CRM hosts (hubspot, marketo, pardot, klaviyo, braze, iterable, onesignal). Sites that hide the page until those scripts load went blank. Prodigy blocks only ad and analytics hosts.
- setJavaScriptCanOpenWindowsAutomatically is true, as in Prodigy.
- NOT fixed: popups still open as a separate tab, so window.opener is lost (Prodigy passes the new tab to the popup). Needs a native tab id handed to the page layer.
- Java brace and paren balance checked and page script node --check only. Not compiled, not run on a device.

## V1.22.4
- Pages falling back to their no-JavaScript versions: JavaScript was switched on or off once, when a tab opened, from the first site's NoScript state. A tab that began on a blocked site stayed script-less on every later site (and the reverse), so sites such as search engines served their basic HTML versions. Now `FBWebPlugin.applyJsPolicy` re-checks NoScript and the allow list on every main-frame navigation (the page passes `noscript` and `allow` in open/setOptions).
- Allowed sites now cover their subdomains (allowing github.com also allows gist.github.com); the menu toggle removes the matching entry.
- One failed native open no longer pins a tab to the iframe "basic view" (where Google, DuckDuckGo, GitHub and others are forced to open outside): the next address you type tries the native view again.
- Java checked for brace balance and the page script with node --check only. Not compiled, not run on a device.

## V1.22.3
- Groq: default model is now openai/gpt-oss-120b (quick asks use openai/gpt-oss-20b, preview option qwen/qwen3.8-27b). Groq's deprecation page lists llama-3.3-70b-versatile and llama-3.1-8b-instant with an 08/16/26 shutdown, while its models page still lists them as production with contact-sales limits; the pages disagree, so they are no longer defaults. Saved dead models migrate automatically. gpt-oss calls send reasoning_effort low so replies are never empty.
- Groq and Gemini: if the provider says the model is gone, the app reads the live model list (Groq) or walks gemini-3.8-flash, 3.6-flash, 3.5-flash-lite, 3.1-flash-lite (Gemini), switches, and retries once.
- Gemini: default is gemini-3.8-flash. gemini-2.5-* stays selectable but Google limits it to keys that already used it. Free-tier status of each model is not stated on Google's official pages; community sources put 3.x Flash near 10 requests per minute and 1,500 per day.
- AI persona: Festers AI knows the person is Fezzy, the creator, and speaks like a professional security detail who is his friend: calm, protective, no slang. Security and privacy first, page text treated as untrusted data. Only the handle Fezzy is sent, not the full name.
- Not run on a device or against the live Groq and Gemini APIs.

## V1.22.2
- Web loading (Google, GitLab): FBWebPlugin now sends the same reduced Chrome user agent as Prodigy's WebSheet ("Linux; Android 10; K", Chrome/N.0.0.0, no device Build string, no full WebView version). Chrome client hints now apply to every Google host and read the real WebView version from the saved base UA.
- Site fixes: google, gstatic, googleapis, youtube, gitlab, gitlab-static and github hosts skip Shields blocking and keep third-party cookies even in Strict.
- ERR_NETWORK_CHANGED, connection reset/closed, QUIC and disconnected errors retry up to 3 times before the error page; ERR_ABORTED no longer replaces the page.
- Blob download hook keeps only the last 12 blobs (up to 50 MB each) instead of holding every blob for the life of the page (renderer memory).
- Engine picker in the top bar: syncNative now hides the native page while the dropdown is open (it was drawn behind the native WebView on real pages).
- Burn and Scorch: red ash flood (canvas embers, ash flakes, rising red wash, haptic), replaces the flame columns. Reduced motion skips it.
- Not compiled against Android SDK or run on a device here; JS passes node --check.

## V1.22.1
- Native download history now queries Android DownloadManager for real job status, downloaded bytes, total size and progress.
- Completed downloads can be opened with an Android app; active jobs can be cancelled; failed jobs can be retried as new downloads instead of navigating to the source page.
- Android DownloadManager does not expose public pause/resume controls, so the UI does not claim those controls exist. Cancellation and retry are implemented honestly.
- Version metadata aligned to 1.22.1. JavaScript syntax and ZIP integrity checked; Android SDK compilation and device tests remain unverified in this environment.

## V1.22.0
- Added persistent download-event history with file names, timestamps, status and failure details where the native WebView reports them.
- Added retry action for recorded failures when the source URL is available; retry reopens the source page because the current native download listener does not expose resumable download jobs.
- Added recent websites to the home dashboard, plus subtle animated quick-access tiles and a restrained top-edge light effect. The effect is attached to the dashboard top edge only, not the left side.
- Added a Reduced motion accessibility preference and respects the system reduced-motion preference by default.
- Limitation: pause/resume and accurate file-size tracking require native download-job support; they are not falsely shown as available for the current WebView DownloadListener.

## V1.21.8
- Native WebView lifecycle hardening: ordinary HTTP/HTTPS URLs remain native navigations so WebView follows redirects itself.
- Main-frame load failures now render a readable in-WebView error page instead of leaving a blank surface.
- SSL certificate failures render a dedicated security error page. Certificate validation is deliberately not bypassed; proceeding through an untrusted certificate can expose credentials and traffic to interception.
- Renderer death now removes and destroys the dead WebView, creates a replacement native WebView, and reloads the last known URL. Replacement uses safe default settings because the prior WebView instance is no longer usable.
- Validation limitation: Android SDK/Capacitor build and on-device lifecycle tests were not available in this environment.

## V1.21.7 (continued)
- App icon and startup background were near-black (`#0c0809`) in both the icon source (`assets/icon.svg`) and `capacitor.config.json`'s `backgroundColor` (the splash/cold-start flash color for the whole app). Both are now white. Run `npm run assets` (capacitor-assets generate --android) after this so the actual launcher icon files are regenerated from the fixed SVG — editing the SVG alone does not touch the already-generated PNGs under `android/app/src/main/res`.
- Google sign-in ("use a proper browser"): this is not fixable from inside the app. Festers already does everything that can be done client-side — the user agent has the WebView marker removed, the `X-Requested-With` header is hidden, and Google's own sign-in hosts are force-switched to Chrome-style client hints (V1.21.1) regardless of the browser-hints setting. Despite that, Google has deliberately refused sign-in inside *any* embedded Android WebView as a stated anti-phishing policy since 2016, and checks more than the hints — it is not just a spoofing gap that more header changes can close. There is no remaining lever here that stays inside the WebView. The **Outside** button (opens the same address in the phone's real browser) is the actual fix for this specific sign-in, not a workaround to wait out.

## V1.21.7
- Flicker while searching: the address-bar suggestions dropdown (`#sugg`) used to be in the same "something is covering the page" list as the menu and modals, so every keystroke that opened or re-rendered it hid the whole native page view, then showed it again the instant it closed — a hide/show cycle on every character typed. The native view's top edge is now just pushed down below the open dropdown instead, so it stays visible and only the suggestions panel moves. (`index.html` `syncNative`)
- Flicker, second cause: the hidden native view's placeholder background was a near-black colour (`#0c0809`), so each of those hide/show cycles flashed dark before white. It's now white, matching the page itself, so a cycle (where one still happens, e.g. opening the menu) shows no colour change. (`FBWebPlugin.ensureHolder`)
- Downloads doing nothing: `DownloadListener` (Android's own download hook) only ever fires for a direct navigation to a file URL. Most sites today build the file in JavaScript (`new Blob(...)` + `URL.createObjectURL` + a hidden `<a download>` click) and never navigate at all, so nothing reached it — no toast, no notification, no file. A document-start script now watches for that exact pattern, reads the blob's bytes on the page side, and hands them to a new native bridge (`FBBlob`) which saves the file into Downloads/Festers the same way other exports already do, and reports success or failure through the same download toast as before. The page's own click handler still runs normally. (`FBWebPlugin.blobCaptureScript`, `BlobBridge`)
- Blank pages: a page that finished loading at a transient size (keyboard up, rotation, the suggestions panel resizing the view before this version) could paint its last frame at that size and never repaint once the native view resized back, leaving a blank white page with no error. `setBounds` now forces a layout and repaint on the active page every time its real size changes, not only when a tab is first shown. Unconfirmed as the full fix for every blank-page report, since it is one known trigger among several already tried (V1.14.7, V1.19.2, V1.21.4–V1.21.6).
- Tested: Java structure checked with a real `javac`-equivalent brace/paren balance pass (string- and comment-aware, not the earlier naive character count) — balanced, no underflows. The injected document-start script and the full page script both pass `node --check`. NOT tested: no Android SDK is available here either, so nothing was compiled with `javac` against real Android/Capacitor classes or run on a device. The blank-page fix addresses one plausible cause found by reading the code; it is not confirmed against the actual site(s) that showed it.

## V1.21.6
- Passkeys: WebAuthn is no longer left off. With the browser-origin permission (`CREDENTIAL_MANAGER_SET_ORIGIN`) Festers asks Android for browser mode; without it, for app mode, so the passkey prompt (Conduint, Bitwarden, Google) appears and no longer fails before it starts. The status screen reads the setting back and says whether the WebView kept it. In app mode a site accepts the result only if it lists Festers in its `assetlinks.json`, or if the passkey app trusts Festers (Conduint: `CONDUINT_TRUST_FESTERS_SHA256`). (`FBWebPlugin.enablePasskeys`)
- Back button: when the page script has nothing left to close, Back now tries the open page's own WebView history before sending the app to the background (`MainActivity.nativeBack`, `FBWebPlugin.goBackActive`). Fixes Back doing nothing or leaving the app when the script's copy of the history was stale.
- Blank pages: (1) a stopped page renderer (Android frees memory) left a dead, white view forever. The dead view is now dropped and the tab reopens on the same address (`onRenderProcessGone` kind `gone`, `goneTab`/`goneRevive` in the page script; a background tab reopens when selected; two stops in 20 s stops the retries). (2) The tracker shield no longer blocks a site's own requests, so visiting a listed host (yandex.ru, disqus.com) no longer blanks the page. (3) Showing a tab forces a layout and resumes timers, for pages that finished loading while hidden. (4) Pop-up helper views are destroyed after their callback, not inside it.
- NOT tested: Java not compiled against Android (parse-checked only), nothing run on a device. The page script passes `node --check`.

## V1.21.5
- Taken from the syncing app's browser (`WebSheet`), which loads the sites that Festers showed blank:
  - A document-start script gives every page `window.chrome` and `navigator.webdriver=false`, as real Chrome does (`FBWebPlugin.stealthScript`).
  - A protected-media (DRM) request from a page is granted, so Spotify / Netflix style players can start. Camera and microphone still ask every time.
  - Cookies are flushed to disk when a page finishes, so a login survives the app being closed.
- Already there from V1.21.4: `setDatabaseEnabled(true)`.
- Left as is on purpose: Festers keeps its popup rule (only a tap opens a new window) and its own tracker shield.
- NOT tested: Java not compiled, nothing run on a device. Whether this fixes your blank pages is unconfirmed.

## V1.21.4
- Web view: Festers now turns on `setDatabaseEnabled(true)` like Conduint's browser does. It was the only page-storage setting Conduint had that Festers lacked.
- UNCONFIRMED as the cause of the blank white page: no site or status line was available. Java not compiled, not run on a device.

## V1.21.3
- Passkeys: Festers no longer asks Android for browser-mode web passkeys unless Android granted it the browser-origin permission (`CREDENTIAL_MANAGER_SET_ORIGIN`). A sideloaded app is never granted it, so those requests failed (the site rejected the passkey) and may be what crashed the sign-in. With WebAuthn left off, a site falls back to its other sign-in.
- Settings > Autofill & Passkeys > status now says so and points to **Outside** (Chrome) for passkey sign-ins.
- NOT tested: Java not compiled, nothing run on a device. The crash cause is not confirmed from a log.

## V1.21.2
- Settings > Android Permissions: new **Passkey management** group. **Passkey manager** opens Android's passkey provider settings (Android 14+, falls back to the autofill picker). **Passkey permissions** shows the browser-origin permission (declared / granted), Android version, autofill service, default browser, package and signing SHA-256, with a plain note on what is missing. **Trust Festers in your passkey app** copies the package and fingerprint. **Default browser** shortcut.
- Native: `FBSysPlugin.openPasskeySettings` (action `android.settings.CREDENTIAL_PROVIDER`).
- Android decides whether Festers gets the browser-origin permission; the screen only reports it.
- Tested: page script syntax, and the three new buttons in headless Chromium with a mocked FBSys. NOT tested: Java not compiled, nothing run on a device.

## V1.21.1
- Google sign-in ("Couldn't sign you in. This browser or app may not be secure"): Google reads the browser's client hints, and the WebView's own hints say "Android WebView". Festers now switches a tab to Chrome-style hints automatically on `accounts.google.com` and `accounts.youtube.com` (when the page loads, and on redirects into those hosts) and back to the WebView's own hints when the tab moves to any other site, so Cloudflare checks elsewhere are not affected. The user agent was already cleaned and the X-Requested-With header already hidden. (`FBWebPlugin`: `syncHints`, `applyChrome`, `resetHints`, `isGoogleSignIn`.)
- The "Browser identity hints" setting text now says this.
- NOT tested: Java not compiled; not run on a device. Google may still refuse a non-Chrome browser on some accounts. If it does, use **Outside** for the sign-in.

## V1.21.0
- Settings > AI Providers & Keys: new **From a file** button under both Groq and Gemini. Pick a .txt, .md, .csv, .json, .env, .html, .rtf, .docx, .xlsx or .odt file; Festers finds Groq (`gsk_...`) and Gemini (`AIza...` or `AQ....`) keys by their shape, tells you how many it found, and after you confirm saves the new ones and health-checks them. Duplicates and anything else in the file are ignored. The file is read on the phone only and is not kept; the keys are never shown in the confirm box.
- Sites: when a link uses `intent://` and no installed app handles it, Festers now opens the link's own web fallback address (`browser_fallback_url`) instead of doing nothing. (`FBWebPlugin.shouldOverrideUrlLoading`, a small Java change.)
- Tested: page script syntax, and the file reader/key finder in Node with a text file and a .docx whose key is split across runs (finds both key types, trims a trailing full stop, ignores short or look-alike strings). NOT tested: on a device, the file picker in the Android WebView, real key tests, or the Java change (not compiled here). No site can be guaranteed to load: see the notes in HANDOFF.

## V1.20.2
- Build fix: `native/FBCredentialPlugin.java` called `CredentialManager.createCredential` and `getCredential`, which are Kotlin suspend functions that Java cannot call with an Executor and callback. They now call the Java-callable `createCredentialAsync` and `getCredentialAsync`. The `CreateCredentialException` and `GetCredentialException` imports pointed at `androidx.credentials`; they live in `androidx.credentials.exceptions`, which would also fail to compile. Removed two unused imports.
- ROOT CAUSE of the pipeline failure: `ci/android-setup.sh` and `.github/workflows/android.yml` copied only 7 native files and never `FBCredentialPlugin.java`, while `MainActivity` registers `FBCredentialPlugin.class`, so javac failed with "cannot find symbol". Both now copy `native/*.java`, and the setup script stops before Gradle if MainActivity registers a plugin whose file was not copied.
- Docs: README, CHANGELOG order (V1.20.x were pasted at the bottom) and HANDOFF brought up to date; README build/tag examples now say v1.20.2.
- Validation: `ci/android-setup.sh` run end to end on a mock Capacitor project (all 8 Java files copied, preflight passes, versionCode 12002), `bash -n`, YAML parse, brace balance. No javac here. NOT tested: a real Gradle build (not available here), or a device. The pipeline log was not available, so this is the most likely compile error, not a confirmed one.

## V1.20.1
- Keep the Back and Forward toolbar controls visible beside each other on narrow Android screens.
- Keep the existing single Back/Forward click handlers and use tracked/native tab history first, with shell-history fallback only when appropriate.
- Confirm the Android hardware Back callback invokes `window.__fbBack()` and backgrounds the task only when JavaScript returns `false`; overlays and available tab history are handled first.
- Validation: JavaScript syntax and ZIP integrity checked. Android build not run because generated Android/Gradle project files are not included in this source archive.

## V1.20.0
- Restored visibility of the Forward toolbar arrow on small Android screens; wired to both the app's tracked history and native WebView history.
- Tab manager: pin/unpin tabs, assign simple named groups, persisted session metadata, persisted recently closed tabs, and duplicate-tab confirmation (configurable).
- Downloads & Files: search and file-type filters, folder filter, total size for the app's Downloads/Festers directory, and duplicate candidates based on matching file name and size.
- Back button continues to close the topmost overlay first, then navigate tab history; Android back integration remains in MainActivity.
- Download pause/resume is not claimed: the current native download API does not expose reliable per-download pause/resume controls. Android's download notifications may expose controls depending on the download path and Android version.
- Requires build and on-device verification; no Android APK was built in this patch.

## V1.19.5
- Added native Capacitor `FBCredentialPlugin` passkey create/get methods using AndroidX Credential Manager.
- Added an explicit BiometricPrompt gate before passkey operations.
- Registered the plugin in `MainActivity`; documented JS usage and relying-party verification requirements.
- Clarified that `CredentialProviderService` is not required for a browser that consumes passkeys.

## V1.19.4
- Passkeys: the build now declares Android's browser-origin permission (CREDENTIAL_MANAGER_SET_ORIGIN) in the manifest, in ci/android-setup.sh and .github/workflows/android.yml. A browser needs it before Android lets it name a web site in a passkey request (Android 14+). Android may still refuse to grant it to a normal app.
- Settings > Autofill & Passkeys > status now also shows Festers' package name, whether the permission is declared and whether Android granted it, and the app's signing certificate SHA-256 with a **Copy fingerprint** button. That fingerprint is what a passkey app such as Conduint lists to trust Festers. It only stays the same while the signing key stays the same.
- Tested: page script syntax check, `bash -n` on the setup script, the manifest edit on a mock AndroidManifest.xml (adds once, second run changes nothing). NOT tested: Java was not compiled (brace and bracket counts only), nothing was built on GitLab, nothing was run on a device. Whether Android grants the permission is unknown until you open the status screen.

## V1.19.3
- Build now adds the two Android Credential Manager libraries (androidx.credentials:credentials and credentials-play-services-auth, default 1.5.0, override with the FB_CRED_VER variable) next to androidx.webkit, in ci/android-setup.sh (GitLab) and .github/workflows/android.yml. Android's WebView passkey guide lists them together with webkit 1.14.0.
- This only adds the libraries. It does not by itself make Bitwarden passkeys work: Bitwarden also has to trust Festers as a browser (its privileged-apps list), and that part is outside the app. Settings > Autofill & Passkeys shows the result on the phone.
- Tested: bash -n on the setup script, and the gradle edit against a mock build.gradle (adds both lines once, a second run changes nothing, a failed edit exits 1). NOT tested: the real Gradle build, version 1.5.0 resolving, or anything on a device. No page or Java change.

## V1.19.2
- Blocked certificates no longer leave a blank white page. When Android's WebView refuses a site's certificate, a bar appears at the top of the tab: "Can't verify this site" with the reason (chain not trusted here, expired, wrong site, wrong date), **Outside** (opens the address in your phone browser) and **Retry**. There is deliberately no "proceed anyway" button.
- Found on srd.sassa.gov.za: it loaded in Edge but showed "Certificate problem" here. Edge ships its own certificate checks and can complete a missing certificate chain; the app's WebView uses Android's store and cannot. Updating Android System WebView in Play Store can help; otherwise use Outside for that site.
- Native: FBWebPlugin onReceivedSslError now sends kind, why and issuer with the error event. Tested: page script syntax check and the bar in headless Chromium (Outside and Retry wired, Retry removes it, no page errors). NOT tested: Java was not compiled, and nothing ran on a device, so the real SASSA page is unconfirmed.

## V1.19.1
- Shields history now uses your local date, not UTC, so "today" and the weekday labels roll over at your midnight. Counts from before this version reset "today" once.
- Shields counts are saved the moment the app is hidden or closed (they used to wait 1.2 seconds, so the last blocks could be lost).
- Auto scene now updates itself every minute while the start page is open.
- AMOLED: the start page glow is much softer so the page stays near black. A scan for leftover dark-brown backgrounds on the start page and Shields panel found none.
- Chart labels are larger. README duplicates removed (permissions notes and two feature bullets).
- Tested in headless Chromium with a fixed clock: night scene with stars in light, dark and AMOLED, noon refresh removes the stars, local date key, save on pagehide, no page errors. NOT tested on a device. Not changed: the native page-view holder in FBWebPlugin.java still shows dark brown (#0c0809) behind pages while they load, even in AMOLED; fixing it needs a Java change.

## V1.19.0
- Shields charts: the Shields panel now has a "Last 7 days" bar chart (today highlighted), a "By type" donut (Ads, Analytics, Social, Other) and a "Top blockers, all time" list. Bars grow in and stop animating with Reduce motion. The type is guessed from the host name (heuristic, not a perfect list). History starts from this version, so earlier blocks only count in the all-time total.
- Festers AMOLED: a true-black dark theme (Settings > Theme). The home-screen sun/moon switch still flips light and dark and remembers whether your dark side was Dark or AMOLED.
- Auto start-page scene: Settings > New Tab Page > Scene > Auto. Hills and the sun or moon follow the clock (dawn 5-8, day 8-17, dusk 17-20, night with twinkling stars). Ocean, Field and City are unchanged.
- Tested in headless Chromium: page script syntax check, AMOLED variables applied, Auto scene renders, Shields charts in light and AMOLED with seeded data, no page errors. NOT tested on a device, or with real blocked-tracker events from the native view. Java was not changed.

## V1.18.3
- Gemini keys that start with `AQ.` are now accepted. Google's AI Studio now creates "auth keys" that start with `AQ.` instead of `AIza` (all new AI Studio keys since 28 May 2026), and the key box only allowed `AIza`, so it said "does not look like a Gemini key". Both formats work in Add key, Add several keys and the AI quick-add box. Keys are still sent only in the `x-goog-api-key` header to generativelanguage.googleapis.com.
- `AQ.` keys are also hidden from the text sent to AI (the "[secret]" privacy filter), like `AIza` keys.
- Tested in headless Chromium: the key checks accept `AIza` and `AQ.` shapes and still reject Groq keys, empty and short strings. NOT tested: a real `AQ.` key against Google from the sandbox (no network); the first Test in Settings, AI Providers & Keys will show whether Google accepts it. Java was not changed.

## V1.18.2
- Exports now save a real file. Cause: every export (vault backup, bookmarks, notes, settings, collections, history, CSV, offline pages, VPN configs, note editor) used a hidden download link, which does nothing inside the app's WebView, so no file appeared. They now save to Downloads/Festers through the native file plugin and show where ("Saved to Downloads/Festers/<name>") or the real error. Outside the app (plain browser) the old link is still the fallback.
- Email backup removed: EmailJS settings, "Send test backup email", "Send test PIN", "Require email PIN before any export", email vault sync, the lockout email and the email-PIN recovery are gone. Old stored EmailJS keys are wiped on start, and the setup zip no longer carries an email key.
- Backup is now the full-setup zip: Security center > Backup leads with Export full setup / Import setup zip, plus the password-locked .festers file (Save file / Restore). Export tab keeps the single-item exports and a full-setup button. Saving a setup zip records the time, and the Profile card shows "Vault backed up / No backup yet / Backup is old" with a Back up now button.
- Forgot master password: now offers Import setup zip, Import vault backup, or Erase and start fresh (asks for your passkey first if the export passkey gate is on). Nothing can open the vault without the master password.
- Start-page tiles, bookmarks and lists show brand marks instead of generic letters: Groq, DuckDuckGo, Wikipedia, GitHub, GitLab, YouTube, Reddit, Google, Gmail, Bing, Brave, X, Instagram, Facebook, WhatsApp, Telegram, LinkedIn, Spotify, Netflix, Amazon, TikTok, Discord, OpenAI, Claude, Stack Overflow, npm, Twitch, Pinterest, PayPal, Microsoft, Apple, Yahoo and more. They are built in (work offline, simple original drawings, not official logos). Other sites still use their favicon, then a letter.
- Settings > Security (new, second in the list): posture score, the main protection toggles, and one-tap links to every Security center tab plus the Vault and Scorch.
- Tested in headless Chromium with a mocked file plugin: tiles show brand SVGs, Security page renders, vault export, bookmarks export, password-locked backup and the full setup zip each call the native save with the right file name, last-backup time is stored, no page errors. NOT tested: on a device, the zip import after this change (code unchanged), and the native save on Android versions below 10. Java was not changed in this version.

## V1.18.1
- Stuck "Just a moment" / browser-check pages (for example GitLab sign-in): after 12 seconds on such a page a bar appears with the Bojack mark, "Verification stuck", **Try fix** and **Outside**. Try fix turns Strict tracker protection off first; if Strict is already off it switches the browser hints, then reloads the tab in a fresh view. Outside opens the page in your phone browser.
- Browser identity hints (Settings > Autofill & Passkeys): **WebView (default)** is now the default and sends the WebView's own hints. **Chrome-style** is the V1.16.1 behaviour, now with full platform version, model, bitness and architecture. Chrome-style helps Google sign-in but may make Cloudflare checks loop. Applies to tabs opened or reloaded after you change it.
- Shields never block browser-check and captcha hosts (Cloudflare challenges, hCaptcha, reCAPTCHA, Arkose, GeeTest, DataDome, gstatic). They were not on the block list before; this keeps it that way.
- Bug fix: switching Strict tracker protection off now takes effect on tabs that are already open (third-party cookies were only set when a tab was first created).
- Page check (Settings > Autofill & Passkeys): for the current tab shows JavaScript, cookies, third-party cookies, page storage, Shields, burner tab, browser hints, last blocked host, WebView version and user agent.
- Native: FBWebPlugin.diag, hintsPref, isChallenge, open() takes `hints`. Tested: page script syntax check; Java parse-scanned only (no syntax errors, not compiled). NOT tested: nothing was run on a device and the sandbox has no network, so whether this clears the GitLab check is unconfirmed. Cloudflare may still refuse an app WebView; if so use Outside.

## V1.18.0
- Protection status notification (Settings > Android Permissions area, off by default): one quiet, low-priority line in the notification shade, for example "Festers: Protected (63/100)" with "VPN off · DNS Quad9 Secure · 4 threats blocked". It updates when you change a protection and every 20 seconds, tapping it opens Festers, and turning it off removes it. If the notification permission is missing, turning it on asks for it. The VPN and DNS words are what Festers itself knows: the VPN guard check and the DNS filter picked in the Security panel, not a reading of Android's Private DNS.
- Downloads: the file name now shows as the notification title. Android's own download notification already shows a progress bar while a file downloads and "Download complete" after; that was already on in earlier versions. Saves made inside the app (blob or data files) are instant and do not notify.
- Native: FBSys.setStatusNote. Tested: page script syntax check and the toggle and text with a mocked plugin. Java parse-checked only, not compiled, nothing run on a device.

## V1.17.1
- Start page scene: the small Bojack in the bottom picture is gone (the big one at the top is the mascot). The scenery stays.
- Dashboard: green highlights. A soft green glow and a slow green light sweep along the top edge of the dashboard, a green glow on the score ring, protections that are on get a green edge, tint and glow with green status text, the Device block gets a pulsing green "live" dot, green edge on each Device card and a glow on the bars. Reduce motion (Settings) or the phone's own setting stops the movement. Colours, scores and layout are unchanged.
- Tested in headless Chromium. Not tested on a device.

## V1.17.0
- Start page scene: an optional picture along the bottom of the new tab, drawn behind everything as its own layer. Ocean (default), Field or City, with Bojack sitting in it. It never blocks taps, follows the light and dark theme (dark gets a night palette) and scales to any width. Settings > New Tab Page > Scene (Off, Ocean, Field, City). The security dashboard and the rest of the page are unchanged.
- Original artwork, not taken from any other browser.
- Tested in headless Chromium (all three scenes, light and dark, taps pass through). Not tested on a device.

## V1.16.2
- Device dashboard: the Network card now shows the Wi-Fi name. Android only hands the name to apps that hold the Location permission, so on Wi-Fi without it the card says "tap to show name" and tapping asks for Location (if Android has it blocked, app settings open). With Location allowed but the phone's Location switch off, Android returns no name and the card says to turn Location on. Festers never reads your position; the permission is only the key Android requires for the name.
- Not tested on a device; Java parse-checked only.

## V1.16.1
- Google "this browser or app may not be secure": the page already ran JavaScript and allowed third-party cookies in normal tabs (they only switch off with Strict tracker protection, burner windows, or NoScript on that site), and the user agent and app-name header were already cleaned. What was still giving the app away was the Chrome client hints (Sec-CH-UA and navigator.userAgentData), which listed "Android WebView". They now list Chromium and Google Chrome with the WebView's real version, matching the user agent.
- Settings > Autofill & Passkeys > Check status now also shows "Browser hints: Chrome <version>" or the reason they could not be set.
- No guarantee: Google also decides on its own signals and may still refuse sign-in inside an app WebView. Not tested on a device. Java parse-checked only; androidx.webkit method names are from memory, so if the build fails on one, the log names it.
- Tip: if Google still refuses, turn Strict tracker protection off, NoScript off for accounts.google.com, and use Check status to read the line above.

## V1.16.0
- Device dashboard on the start page: inside the Security dashboard (tap it open) a new Device block shows five cards that refresh every 3 seconds while the page is showing: Battery (percent, charging / full / on battery, plug type, temperature), RAM (used of total, low-memory flag), Network (Wi-Fi / mobile / ethernet / VPN / offline, Wi-Fi link speed, band and signal, metered, VPN on), Storage (used and free on the phone's data storage), CPU (core count, current and max MHz when Android allows reading them, chip name, and how much CPU Festers itself is using).
- Honest limits: Android 8+ blocks apps from reading whole-phone CPU load, so the CPU card shows Festers' own share, not the whole phone. Some phones hide CPU frequency files; the card then shows cores only. Wi-Fi network name is not shown (Android needs location permission for it). Outside the app (plain browser) the cards fall back to what the web exposes (battery, rough RAM, connection type, browser storage).
- Native: FBSys.deviceStats (read-only, no new prompts). ci/android-setup.sh and android.yml add the ACCESS_WIFI_STATE permission (normal permission, no prompt) for Wi-Fi speed and signal.
- Tested: page script syntax check and the device cards in headless Chromium with a mocked FBSys. Not tested: native Java (parse-checked only, not compiled), nothing run on a device.

## V1.15.0
- AI providers: Gemini joins Groq. Settings > AI Providers & Keys now holds both. Each provider has its own key pool with the same health meter (score, latency, ok/failed counts, rate-limit state), Use / Test / Rename / Delete per key, Test all keys, and a Test every key button for both pools.
- Several keys at once: Add several (Groq and Gemini) takes pasted keys separated by new lines, spaces or commas, skips duplicates and anything that is not that provider's key, then tests each one and reports healthy / rejected / rate limited. You can also paste a Groq or a Gemini key into the AI panel and it picks the right pool.
- Router (Settings > AI Providers & Keys > Routing): Auto sends long context to Gemini (page summaries whenever a Gemini key exists, plus any prompt over the threshold: 3k, 5k, 8k or 12k tokens) and everything else to Groq. Groq Only and Gemini Only force one provider. If a whole pool fails (all keys rejected or rate limited, server trouble) the other provider answers and the reply says so. Groq gets the fast small model for tiny asks and search ideas (Smart Groq model, on by default) and your chosen model otherwise; a model that is not in the fetched list falls back to your chosen one. When text has to go to Groq it is cut to fit and the reply says so.
- Gemini keys are sent in the x-goog-api-key header to generativelanguage.googleapis.com only. Rejected keys (400 API_KEY_INVALID, 401, 403), rate limits (429, with Google's retry delay or a 1 hour pause for a daily limit) and server errors (5xx) are told apart, and rotation skips keys that are bad or paused. A blocked or empty answer is reported as that, not as a key problem. Model list refreshes from the API.
- The panel is now called Festers AI and shows one chip per provider. Gemini's free tier may use prompts to improve Google's products, which the settings page says; Hide personal details stays on by default and applies to both.
- Save-password bar: after you sign in on a secure page in a normal tab, a bar offers Save (or Update when the username is saved with a different password) and Never. It waits for the next page and stays quiet if the password box is still showing (failed login), if the page is flagged by Email & password protection, or in burner windows. It needs an unlocked vault; with a locked vault the Save button opens the vault. Settings > Autofill & Passkeys: Offer to save passwords, and Clear list for sites set to Never.
- Key button in the address bar: turns the accent colour when the unlocked vault has a login for the site. Tap for Fill login, Save a login for this site, or Open passwords. With the vault locked or not created it opens the vault panel.
- Native: FBWebPlugin injects a small watcher on https pages in normal tabs (form submit, button tap or Enter while a password box has text) and reports username and password to the app over a JavaScript interface named FBCred. The URL comes from the WebView, not from the page. A page can call that interface itself, which can only make the bar offer to save what that page sends; nothing is ever read back out.
- Setup export: Gemini keys travel with the other keys when "Include Groq keys, VPN configs and email key" is ticked, unencrypted like the rest.
- Tested: page script syntax check; router, rotation, fallback, health states, Gemini error handling, request shape, bulk add and the save bar flow (save, update, never, failed login, burner, key menu) in headless Chromium with mocked Groq and Gemini responses and a stubbed native plugin. Not tested: real Groq or Gemini calls, the native Java (parse-checked only, not compiled), the password watcher on real sites (forms that clear the box before submit, multi-step logins), anything on a device. Gemini model names in the default list come from memory; Refresh pulls the real list.

## V1.14.7
- Black pages: the page canvas is now white, like other browsers. Before, it was a near-black colour, so any page that sets no background of its own showed up as a black screen.
- Black pages: if Android stops a page's renderer (low memory or a crash), Festers now stays open and shows "This page stopped, close this tab and open it again". Before, that could blank the page or close the whole app.
- Black pages: mixed content now follows Chrome's rule (images and similar load over http on an https page; scripts stay blocked). Before, everything over http was blocked and pages loaded half empty.
- "Not a browser": Festers asks the WebView to stop sending the X-Requested-With header, which carries this app's package name and makes some sites treat it as an embedded view. This helps with sites that check it. Google sign-in can still refuse an app WebView for other reasons, and that cannot be fixed from here.
- Passkeys: the status line (Settings > Autofill & Passkeys > Check status) now shows the WebView version and Android version, the real error if passkey support failed, whether the app-name header is hidden, and the last reason a page was stopped. Use it to tell what is blocking passkeys.
- Passkeys still depend on the passkey app (Google Password Manager, Conduint, Bitwarden) allowing Festers as a browser. Apps keep their own allow lists, so this can fail for reasons outside this app even when everything here is correct.
- Tested: Java files parse (no syntax errors) and the page script passes a syntax check. Not tested: the native Java is not compiled in the sandbox, and none of this has run on a device. The androidx.webkit method names are from memory. If the build fails on a name, the log will show which one.

## V1.14.6
- Signing: FB_KEY_ALIAS is now optional on GitLab. The build opens the keystore, uses the only key in it (or the one named festers), and prints the signing certificate SHA-256 in the job log. If you do set FB_KEY_ALIAS and it does not match, the build stops and lists the keys that exist.
- Signing: a half-set signing setup (for example the password without the keystore, or only the alias) now stops the build with a message naming what is missing, instead of quietly using a throwaway key. With none of the variables found, it still builds but prints a clear warning that the APK cannot update over a signed install.
- VPN configs: the file picker accepts any file, including .bin. Text files are recognised as WireGuard, OpenVPN, Xray / sing-box, proxy links, Clash, Shadowsocks, IKEv2 or Apple profiles. Anything else (such as .bin) is stored byte for byte, and Send or Save gives back the same file. 2 MB limit for files.
- VPN screen: generic wording and a generic example name, many more VPN services to choose from (Proton VPN, Mullvad, IVPN, Windscribe, NordVPN, Surfshark, ExpressVPN, CyberGhost, Private Internet Access, TunnelBear, hide.me, AirVPN, PrivadoVPN, Cloudflare WARP) and links to apps that use the configs (WireGuard, OpenVPN Connect, Outline, strongSwan).
- Tested: the signing script against real test keystores (one key, two keys, wrong alias, wrong password, half-set and unset variables) and the VPN file recognition and binary round trip in Node. Not tested: the new dialog on a device, or a real GitLab pipeline. The GitHub workflow (.github/workflows/android.yml) still needs all three secrets.

## V1.14.5
- Email & password protection (Settings > Privacy & Security, on by default). When a page has an email or password box, Festers checks it on the phone and shows a red bar if: the page is plain http; the address looks like a fake (punycode, hidden @, "paypal-login" style names, brand under a different domain); the form sends what you type to a different site or without encryption; or the page title names a big brand (PayPal, Apple, Microsoft, Amazon, Google and so on) but the address is not theirs. Details explains each reason; Leave goes to the home page; the X or Continue anyway hides it for that site until the app restarts.
- On a flagged page Festers does not offer Fill login or Fill code. Nothing is sent anywhere, and burner windows are checked too.
- These are rules of thumb and can warn on a safe page or miss a clever fake. Device build and runtime tests are still required.

## V1.14.4
- Links stay in the app: the email breach check, the VPN provider links, Groq key page, About links and update links now open in a Festers tab, not an outside browser. Open in other browser still exists on pages.
- Default browser: the request now uses Android's own role dialog correctly (it never showed before). If Android declines to show it, the Default apps list opens. A one-time offer appears a few seconds after first start.
- Android Permissions: new rows for Photos & media, Camera, Microphone and Location, each with its own Android prompt, plus Check status. The manifest now declares them (camera, microphone, location, media are optional features, so installs are never blocked).
- Site permissions: pages that ask for camera, microphone or location now get an Allow/Block prompt every time. If Android has the permission off, Festers tells you to turn it on in Android Permissions.
- Scorch now plays a flame animation. New Burn (menu, Settings, and a button inside Scorch): flames, then closes every tab including burner windows, clears tab groups, history, Groq chat, cookies, site data, cache and the saved session, and locks the vault. Bookmarks and notes are only erased if you tick them. Reduce motion skips the flames.
- Device build and runtime tests are still required.

## V1.14.3
- Auto-offer: when the vault is unlocked and a page has a login form that matches a saved login, a small bar offers Fill (or Choose if there are several). When the page has a one-time-code box, it offers Fill code for the matching login that has a TOTP key. Nothing fills until you tap; the X hides it for that site until the app restarts. Settings > Autofill & Passkeys > Suggest Fill login turns it off. Not shown in burner windows or when the vault is locked.
- One-tap TOTP: after Fill login puts in the password, a Code bar appears with Fill code and Copy. Codes are made at the moment you tap, digits only, filled into the visible code box (single box or one digit per box), with the same https and site check as Fill login. Copy uses the clipboard auto-clear.
- Device build and runtime tests are still required.

## V1.14.2
- Autofill: normal tabs now allow Android's autofill service (Google, Bitwarden, etc.); burner windows do not. New Settings > Autofill & Passkeys with Choose service and Check status.
- Fill login from vault: Menu > Page > Fill login from vault. Matches the page to a saved vault login (url and extra URIs, subdomains allowed, https only), asks for your fingerprint if you use a passkey lock or the login asks for it, fills username and password in the main frame, and refuses if the page host no longer matches. With no match it offers to save one first. Two-step logins: fill the username, continue, fill again.
- Passkeys on websites: the browser asks the WebView for WebAuthn support (browser mode). Android only grants this to browsers Google has approved, so it may be refused; Check status shows the answer. The build now adds androidx.webkit 1.14.0.
- Device build and runtime tests are still required.

## V1.14.1
- Full setup export and import: Settings > Privacy & Security > Backup > Export full setup writes one .zip (settings, bookmarks, notes, shortcuts, extensions, history, collections, encrypted vault, optional offline pages) to Downloads/Festers, or shares it. Import setup zip restores it and reloads. Groq keys, VPN configs and the email key are only included if you tick the box, and are not encrypted in the zip. The passkey record is never moved. Imported settings are filtered to known settings.
- Back button rewrite: Back now closes the topmost thing first (lock-safe dialogs, menu, engine list, reader, document viewer, side panel, find bar), leaves a full-screen video, goes back in the page, closes a tab that a page opened and returns to the page that opened it, closes an empty start-page tab when other tabs exist, and only then sends the app to the background. It never finishes the app.
- Keep tabs when you leave (Settings > Android Permissions, on by default): a small foreground service runs while Festers is in the background so Android is much less likely to kill it and reload your tabs. Tap Stop in its notification to turn it off. Session restore stays as the fallback.
- Device build and runtime tests are still required.

## V1.14.0
- Wordmark: FESTERS in black, BROWSER in orange (start page and About).
- Bottom bar is now always at the bottom: Groq, Scorch, Security, Media, Bookmarks, Sets, Settings, with an active marker.
- Bookmarks moved into a panel (search, open, open in new tab, remove, add page). The bookmarks bar under the address bar is gone.
- New Settings > General > Toolbar position (top or bottom) and a menu item to flip it. The menu opens above a bottom toolbar.
- Menu gets a Back / Forward / Reload / Home row; on narrow phones those buttons leave the toolbar so the address field has room.
- Session is saved when the app is hidden and restored on start by default.
- Groq keys: add them straight in the Groq panel or Settings. They are stored on the phone, no vault needed. Old vault keys still work.
- VPN configs: add, send, save and delete without the vault. Old vault configs still show.
- DNS: Cloudflare, Google and NextDNS removed. Five filtering providers: Quad9 Secure, AdGuard Family, CleanBrowsing Family, Control D Family, Mullvad Family, with private DNS hostnames, a lookup tool and Compare all 5. Not yet tested against the live servers.
- Not done yet: full-setup zip export/import, native back-button rewrite, keep-alive on exit.

## V1.13.0
- Groq AI replaces Duck.ai everywhere (rail, menu, address bar, shortcuts, help, About). Bring Your Own Key: save as many Groq keys as you like, encrypted in the vault, sent only to api.groq.com.
- New Settings > Groq AI & Keys: key health meter per key and for the whole pool (status, score, latency, requests and tokens left, ok/failed counts), Test / Test all, Use, Rename, Delete, model picker with Refresh, automatic key rotation on rejected or rate-limited keys.
- Privacy: personal details (emails, phones, cards, IPs, API keys) are hidden before sending; chats are not saved unless you turn it on; page text is sent only when you tap Summarize page; Privacy check uses Shields data, no page content. Old Duck.ai chats and settings are wiped on upgrade.
- Search help: ? or !ai in the address bar, an Ask Groq suggestion row, Better searches, and tappable query chips under answers.
- New Groq logo.
- Start page: the date now sits beside the clock (Settings > Home > Date).
- Search engines show their own brand icons in the top bar, dropdown and suggestions.
- Tools menu: Groq AI chat, Summarize page, Privacy check, Toolbox, Copy clean link, plus Shields, Security, Media.
- New Toolbox: link cleaner, hashes, Base64/URL/hex, JSON, time and IDs, text stats. All offline.
- New Clean tracking links setting (utm_*, fbclid, gclid and more) for typed addresses.
- Fixed the address field being only a few pixels wide on phones: Forward and Home are hidden on narrow screens.
- Device build and runtime tests are still required.

## V1.12.4
- GitLab CI: new .gitlab-ci.yml builds the debug APK on GitLab (Temurin 21, Node 22, Android SDK 35 cached between pipelines) and publishes it as a job artifact. Tags starting with v also create a GitLab release with a link to the APK.
- New ci/android-setup.sh (same manifest, permission, service, intent filter, plugin, versionCode and signing edits as the GitHub workflow, with the same fail-the-build checks) and ci/install-android-sdk.sh. Signing reads the GitLab CI/CD variables FB_KEYSTORE_B64, FB_KEYSTORE_PASSWORD and FB_KEY_ALIAS; the decoded key is deleted at the end of the job and never stored as an artifact.
- The GitHub workflow is unchanged. The in-app update check and About links still point at the GitHub repo in CONFIG.repo.

## V1.12.3
- Updates now install over the old app: the build sets versionCode and versionName from package.json (1.12.3 becomes 11203) and signs with a persistent key kept in CI secrets FB_KEYSTORE_B64, FB_KEYSTORE_PASSWORD and FB_KEY_ALIAS. Without those secrets the build still works but uses a throwaway key and prints a warning. *.jks files are now ignored by git.

## V1.12.2
- Downloads manager: View opens pdf, md, markdown, csv, tsv, docx, html, htm, txt, log, json, sh, ini, conf, py and js files in the built-in viewers and text editor (files up to 16 MB). Open is now Open with and still hands the file to another app. Back from a viewer returns to the Downloads list.
- Downloads manager: folders (such as extracted zips) can now be opened, with an Up button; View, Open with, Install and Delete work inside them. Extract stays on top-level zips.
- Notifications: the permission request now reports the answer; if Android has stopped showing the prompt, Festers offers to open app settings.
- Battery: new Battery exemption button asks Android directly (one tap), falling back to the battery settings list. New Android status check shows notifications, battery exemption, default browser and keep-alive.
- Keep-alive: the wake lock now runs in a foreground service (media playback type) with a Stop button in its notification and a 4 hour limit; falls back to the plain wake lock if the service cannot start.
- Zip extraction: files with an unknown type no longer get a forced MIME type, so names are kept as they are. Manifest gets WRITE_EXTERNAL_STORAGE (Android 9 and older only), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and the keep-alive service declaration, with CI checks that fail the build if any are missing.

## v1.12.1
- Fixed blank Settings page: the main script scope closed before the v1.10+ code, so Settings, menu and tab code crashed on load. Init now runs last inside the main scope.
- Removed duplicate b64 helper.

## v1.12.0
- Added Android permission settings for notifications, default browser selection, battery settings, app settings, and a timed media wake lock.
- Added HTTP/HTTPS intent filters and Android manifest permissions for notifications and wake locks.
- Clarified scoped file access and background media limitations.

## v1.12.0
- Added Android Permissions settings for notifications, default browser selection, battery settings, app settings, and an optional timed media wake lock.
- Added HTTP/HTTPS browser intent filters and Android manifest permissions for notifications and wake locks.
- Clarified scoped file access and media playback limitations.

# Changelog

## [1.11.0]
- New Downloads & Files section in Settings: Install downloaded apps (permission status + Allow button), Confirm before installing, Warn about risky files, Ask before every download, Wi-Fi only downloads, Extract zips into their own folder, Delete zip after extracting.
- New Downloads manager (menu > Downloads): lists Downloads/Festers with Install (.apk), Extract (.zip), Open and Delete.
- New native FBFiles plugin: REQUEST_INSTALL_PACKAGES + FileProvider install flow, safe zip extraction (blocks path tricks, caps entries and size).
- Build: workflow adds the install permission and checks the FileProvider; package.json restored.

## [1.10.0]
- Search engine picker is now a dropdown with icons beside the bookmark star (no card). 14 engines: Google, Bing, Copilot, DuckDuckGo, Brave, Startpage, Ecosia, Qwant, Yahoo, Yandex, Perplexity, Wikipedia, YouTube, GitHub.
- Media: categories (All/Audio/Video/Images/Docs), tile / grid / detailed views with size, duration, dimensions and date; add files from the picker and play them.
- Built-in viewers: PDF (native renderer, new FBPdf plugin), Markdown, CSV/TSV, DOCX, HTML (sandboxed, with source toggle). Text editor for .sh and .txt with Save and Save as.

## [1.9.3]
- Search picker: Microsoft Edge (Bing) replaced by Yahoo, so there is no duplicate Bing. Picker is Google, Bing, Microsoft Copilot, DuckDuckGo, Yahoo.

## [1.9.2]
- Search engine picker is now five choices: Google, Bing, Microsoft Copilot, DuckDuckGo and Microsoft Edge (Bing). Same list in Settings > Search. Old Brave, Startpage and Ecosia choices fall back to DuckDuckGo.

## [1.9.1]
- Security posture expands and collapses (state is remembered).
- Start page: FESTERS BROWSER is all black in Festers Light, a Bojack Security On Duty tag line sits under it, and the bangs hint is now Strategy Over Impulse.

## [1.9.0]
- Security posture now holds security functions only: Ad & tracker blocking, Tracker protection (Strict), NoScript, HTTPS-only, Threat protection and VPN guard. Vault, backup and email items are gone from it. Every row has its own on/off switch; tap a row for a live stats panel that refreshes every second.
- Threat protection (new): each new site name is checked once against Quad9 threat intelligence (confirmed against Cloudflare so typos are not flagged) plus local look-alike rules (@ tricks, punycode, fake brand sign-ins). Flagged sites show a Threat blocked page with Back to safety and Continue anyway. Site names, not pages, go to Quad9.
- VPN guard (new): watches Android's VPN status and warns when a VPN drops. Android decides when a VPN is on; the app cannot start one.
- Two themes only: Festers Light (the original, default) and Festers Dark. Sun/moon switch on the home screen. Crimson, Midnight and System are retired; old choices map to Light or Dark.
- Festers Light: orange and red accents, black outlines, and a soft black fade that deepens down the page like a layer.

## [1.8.0]
- Shields: the native page view blocks known ad, tracker and fingerprinting hosts and reports every block live. Toolbar shield button with an animated counter, Shields panel (this page, today, all time, top blocked hosts), Shields setting, Shields item in the menu and a Shields chip on the start page.
- Burner window: private tabs are now called Burner windows (purple tab, tinted toolbar, animated accent line).
- Email backup sends itself through EmailJS, no mail app needed. Free EmailJS has no attachments and a 50 KB limit, so the already-encrypted backup goes in the email body via a second "Backup template" ({{backup}}, {{file_name}}). Falls back to the share sheet when EmailJS is not set up or the backup is too big. Test button in Security > Email & Export.
- Vault recovery: "Locked out? Email recovery" on the locked vault emails an encrypted vault copy plus a one-time PIN; after five wrong master passwords a copy is emailed automatically. The PIN proves inbox ownership; only the master password (or an imported backup) can decrypt the vault.
- Profile (Settings > Profile and menu header): Active status, session, open tabs, shields, vault and passkey state, and Vault sync: an encrypted vault copy is emailed after changes (max one per 10 minutes, six per day) with live Syncing / Synced / Failed status and a Sync now button.
- Passkey wired: FBVault.confirm uses Android BiometricPrompt (fingerprint or screen lock). It guards exports, optionally the Vault and optionally the whole app (also after 2 minutes away). WebAuthn stays as a fallback.
- About rebuilt: Grant Festers, Developer, Ethical Hacker, App Engineering, Ravensmead; S.O.I. x Bojack Security; open source links (source, issues, security policy, licence); built with; honest limits.
- Motion: tab, modal and menu spring-in, staggered start-page chips, floating mascot, loading bar shimmer, press feedback. All of it respects Reduce motion.

## [1.7.0]
- Native page view (FBWeb plugin): one Android WebView per tab, so Google, YouTube, GitHub and Duck.ai load. Iframe view stays as the fallback.
- Search engine picker in the address bar (Google, Bing, DuckDuckGo, Brave, Startpage, Ecosia). Add to collection in the menu. VPN status panel, DNS provider list, encrypted VPN configs in Secrets.
- Vault quick unlock (FBVault plugin): fingerprint through Android BiometricPrompt with the master password wrapped by an Android Keystore key, plus an optional 4-digit PIN (Keystore-wrapped, 3 wrong tries add a delay, 5 erase it). Changing the master password re-wraps both; importing or deleting the vault clears them; a new fingerprint on the phone turns fingerprint unlock off. The fingerprint also confirms logins set to ask for the master password.
- Media Downloader extension (Menu > Page and Extensions > Download media): lists video, audio, images, PDFs and playlists found on the page (request sniffing in the native view plus a DOM scan of img, srcset, video, audio, source, links, og: meta tags and CSS backgrounds), filters by type, hides tiny images, batch-saves to Downloads/Festers with cookies and Referer. Long-press an image or link in a page for Save image, Download link, Open in new tab and Copy address. data: images are saved directly.

## [1.6.0]
- Mascot Bojack is a Labrador-golden retriever mix with a spiked collar and shield tag; icon.svg and assets/bojack.svg updated.
- Menu groups expand and collapse (Page, Tools, Extensions with count, Data and view); the open/closed state is remembered.
- New menu items: Site controls, Recent tabs (open and recently closed), Zoom, Find in page, Translate, Show reading mode, Install and create shortcut, Desktop site checkbox (lays pages out at desktop width), Delete browsing data, Help and feedback.

## [1.5.0]
- Red accent theme with orange highlights; Crimson theme.
- Settings are a vertical list on phones with drill-in pages and a back header; new sections: New Tab Page, Site Permissions, Delete Browsing Data, Accessibility.
- Vault, Notes and Secrets moved from the bottom bar into Settings.
- Top bar (Tabs + toolbar / Toolbar only / Hidden) and bottom bar are optional; red floating menu button when the top bar is hidden.
- Menu gets a quick row (Bookmarks, History, Downloads, Settings) and a grid (Add shortcut, Save page, Share, Delete data); new tab page settings popover and clock chip.

## [1.3.0]
- Bitwarden-style login fields (folder, favorite, extra URIs, custom fields, master-password re-prompt, passkey record); Bitwarden CSV round-trip; device passkey gate for exports; interface size, reduce motion, rail labels.

## [1.2.0]
- Scorch replaces Fire; vertical Security menu; real site icons on shortcuts and tabs; Duck.ai chat panel and ?question search; email PIN (EmailJS) gating exports; Bitwarden-format vault CSV, settings, bookmarks, notes, history exports; Collections; encrypted Secrets (SSH, TOTP, API keys, Wi-Fi, notes); media player restyled.

## 1.1.0
- Labrador mascot with security collar tag.
- Security dashboard on the start page (live score from your settings).
- NoScript mode with per-site allow list; private tabs use isolated storage.
- Security centre: breach checker (HIBP k-anonymity), DoH lookup, VPN check, offline pages, encrypted email backup, update checker.
- Media player (audio, video, images), Duck.ai launcher, SSL Labs and DNS check extensions.
- Capacitor project files, CSP, GitHub Actions APK build.

## 1.0.0
- Tabs, private tabs, bangs, bookmarks, history, encrypted vault with TOTP, notepad, extensions.
