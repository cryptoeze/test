package com.cryptoeze.app.web

/** Scripts injected into every page of the website. */
object PageScripts {

    /** Runs at document start: clipboard, blob downloads and scroll tracking. */
    val DOCUMENT_START = """
        (function () {
          if (window.__cryptoEzeApp) return;
          window.__cryptoEzeApp = true;
          var app = window.${AppBridge.NAME};
          if (!app) return;

          // Reliable copy-to-clipboard inside the app.
          try {
            if (navigator.clipboard) {
              navigator.clipboard.writeText = function (text) {
                app.copyText(String(text));
                return Promise.resolve();
              };
            }
          } catch (e) {}

          // Remember blobs so generated files (statements, receipts, QR codes) can be saved.
          var blobs = {};
          var create = URL.createObjectURL;
          URL.createObjectURL = function (obj) {
            var url = create.apply(URL, arguments);
            try { if (obj instanceof Blob) blobs[url] = obj; } catch (e) {}
            return url;
          };
          function save(blob, name) {
            var reader = new FileReader();
            reader.onloadend = function () { app.saveBase64(reader.result, name || '', blob.type || ''); };
            reader.readAsDataURL(blob);
          }
          window.__cryptoEzeSaveBlob = function (url, name) {
            if (blobs[url]) { save(blobs[url], name); return; }
            fetch(url).then(function (r) { return r.blob(); }).then(function (b) { save(b, name); });
          };
          document.addEventListener('click', function (e) {
            var a = e.target && e.target.closest ? e.target.closest('a[href^="blob:"], a[href^="data:"]') : null;
            if (!a) return;
            e.preventDefault();
            e.stopPropagation();
            var name = a.getAttribute('download') || '';
            if (a.href.indexOf('data:') === 0) { app.saveBase64(a.href, name, ''); }
            else { window.__cryptoEzeSaveBlob(a.href, name); }
          }, true);

          // Tell the app when an inner container is scrolled so pull-to-refresh doesn't fight it.
          document.addEventListener('touchstart', function (e) {
            var el = e.target, scrolled = window.scrollY > 0;
            while (!scrolled && el && el !== document.body && el.nodeType === 1) {
              if (el.scrollTop > 0) scrolled = true;
              el = el.parentElement;
            }
            app.setInnerScrolled(scrolled);
          }, { passive: true, capture: true });
        })();
    """.trimIndent()

    /** Returns the page's theme colour (meta theme-color, else body background). */
    const val THEME_COLOR = """
        (function () {
          function bg(el) {
            if (!el) return null;
            var c = getComputedStyle(el).backgroundColor;
            return c && c !== 'transparent' && c !== 'rgba(0, 0, 0, 0)' ? c : null;
          }
          var m = document.querySelector('meta[name="theme-color"]');
          return (m && m.content) || bg(document.body) || bg(document.documentElement) || '';
        })();
    """

    fun fcmTokenEvent(token: String): String {
        val safe = token.replace("\\", "").replace("'", "")
        return """
            (function () {
              try { localStorage.setItem('cryptoeze_fcm_token', '$safe'); } catch (e) {}
              window.dispatchEvent(new CustomEvent('cryptoeze:fcm-token', { detail: '$safe' }));
            })();
        """.trimIndent()
    }
}
