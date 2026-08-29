# Manual test feeds

## Oversized article scroll fixture

The Moby-Dick fixture contains one Atom entry with the complete article body:

- Feed: https://hreader-moby-dick-fixture.hiosdra.chatgpt.site/feed.xml
- Article: https://hreader-moby-dick-fixture.hiosdra.chatgpt.site/moby-dick.html

Use it to exercise the fallback path for documents above the 262,000 px WebView
height limit. Add the feed to FreshRSS or Miniflux, sync it in hReader, and open
the article after the entry has been downloaded.

Verify that:

1. an upward drag first hides the article header and then continues through the
   WebView body without a visible pause;
2. a downward drag brings the body to its beginning before revealing the header;
3. changing direction near the handoff does not move the header and body at
   different rates;
4. releasing the finger keeps the scroll responsive and does not reset the
   article position;
5. reopening the article restores a position near the previous location.

For a deterministic offline fallback check, sync the entry while online, enable
airplane mode, and then open it again in hReader.
