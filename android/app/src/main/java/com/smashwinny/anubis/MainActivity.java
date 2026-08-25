package com.smashwinny.anubis;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://app.anubis.invalid/";
    private static final int FILE_PICKER = 42;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.addJavascriptInterface(new AndroidBridge(), "AnubisAndroid");
        webView.setWebViewClient(new LocalClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_PICKER); }
                catch (Exception e) { fileCallback = null; Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        webView.loadUrl(ORIGIN);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_PICKER || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
        fileCallback = null;
    }

    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    private class LocalClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri=request.getUrl();
            if (!"app.anubis.invalid".equals(uri.getHost())) return super.shouldInterceptRequest(view, request);
            String name=uri.getPath(); if (name==null||name.equals("/")) name="/index.html"; name=name.substring(1);
            try { InputStream in=getAssets().open(name); String ext=MimeTypeMap.getFileExtensionFromUrl(name),mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return new WebResourceResponse(mime==null?"application/octet-stream":mime,"UTF-8",in); }
            catch(Exception e){ return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new ByteArrayInputStream(new byte[0])); }
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if ("app.anubis.invalid".equals(request.getUrl().getHost())) return false;
            startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl())); return true;
        }
    }

    private class AndroidBridge {
        @JavascriptInterface public void saveBackup(String text, String filename) {
            runOnUiThread(() -> { try {
                OutputStream out;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues v=new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,filename); v.put(MediaStore.Downloads.MIME_TYPE,"application/json"); v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
                    Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v); if(uri==null)throw new Exception();
                    out=getContentResolver().openOutputStream(uri);
                } else {
                    File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS); if(dir==null)throw new Exception();
                    out=new FileOutputStream(new File(dir,filename));
                }
                if(out==null)throw new Exception(); try(OutputStream target=out){target.write(text.getBytes(StandardCharsets.UTF_8));}
                Toast.makeText(MainActivity.this,"加密备份已保存到下载目录",Toast.LENGTH_LONG).show();
            } catch(Exception e){Toast.makeText(MainActivity.this,"备份保存失败",Toast.LENGTH_LONG).show();} });
        }
    }
}
