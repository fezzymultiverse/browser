# Changelog

The full history is in `CHANGELOG.md` in the repository.

## V1.23.7
- Bottom bar colours: LLM orange (was Groq), Media cyan, Bookmarks and Sets orange, Downloads green, Vault purple, Settings red. Scorch (red) and Security (blue) are coloured too and show their colour wherever they appear. Icons glow in their colour, the open item gets a tinted background and a coloured underline.
- Links from other apps: root cause of "opens and stands still" was that the browser intent filter existed but nothing ever read the link. MainActivity now keeps the http(s) link from the intent (cold start, onNewIntent, onResume), FBSys.launchUrl hands it to the page once, and the page opens it in a new tab (closing any open panel first). The CI script also makes sure the launcher activity is singleTask.
- Media rewritten. Categories (All, Music, Video, Images, Docs) with counts, every category is a drop-down group with an arrow, Tiles/Grid/Detailed views, neat square thumbnails: image previews, video frame, album art for music, colour-coded type tiles for documents.
- Audio metadata reader written in plain JS: ID3v1/v2 (MP3), MP4/M4A, FLAC, Ogg Vorbis and Opus, WAV. Shows title, artist, album, album artist, year, genre, track, disc, composer, label, BPM, key, format, bitrate, sample rate, channels, duration, size, modified, encoder, ISRC, copyright, lyrics flag and comment, plus embedded album art (also as a blurred backdrop and on the lock screen through Media Session). Previous/Next buttons.
- "Did you know" pill: facts about the artist drop in like a pill, a red bar shows when there is more text and the text scrolls through, then the pill lifts away. One every minute while music plays. Modes: Pill (drops over the panel), In player (reserved space in the player card), Off. Facts come from Wikipedia (only the artist name is sent, cached 30 days), mixed with facts from the file's own tags. Works offline with the tag facts.
- Not compiled, not run on a device. JS passes node --check; the tag readers were tested in Node with synthetic ID3, MP4, FLAC and Ogg files.

## V1.23.6
- Downloads panel with History, Security Console in Settings, scorch Burn (Reduce motion fixed), collection colour tags, JSON password import, Conduint status, Back button double-press guard.

## V1.23.4
- Fixed the compile error in FBWebPlugin.java (missing `//` before a comment) that failed the V1.23.3 build.
- Added project documents, GitLab templates and this wiki.

## V1.23.3
- Back/Forward direction now reported by the native side.
- Burn always plays, with red-hot ash.
