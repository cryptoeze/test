# JavaScript bridge methods are called by name from the website.
-keepclassmembers class com.cryptoeze.app.web.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
