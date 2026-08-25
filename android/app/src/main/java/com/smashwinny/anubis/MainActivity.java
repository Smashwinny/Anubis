package com.smashwinny.anubis;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.app.AlertDialog;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
    private static final String TAG = "AnubisScan";
    private static final String ORIGIN = "https://app.anubis.invalid/";
    private static final int FILE_PICKER = 42;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
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
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                Log.i(TAG,"JavaScript confirm requested");
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Anubis 确认")
                    .setMessage(message)
                    .setPositiveButton("确认",(dialog,which)->result.confirm())
                    .setNegativeButton("取消",(dialog,which)->result.cancel())
                    .setOnCancelListener(dialog->result.cancel())
                    .show();
                return true;
            }
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG,"web "+message.messageLevel()+" "+message.sourceId()+":"+message.lineNumber()+" "+message.message());
                return true;
            }
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
        Log.i(TAG,"onActivityResult request="+request+" result="+result+" data="+(data==null?"null":data.getAction()));
        IntentResult scan=IntentIntegrator.parseActivityResult(request,result,data);
        if(scan!=null){if(scan.getContents()!=null)webView.evaluateJavascript("window.onAnubisScan("+JSONObject.quote(scan.getContents())+")",null);return;}
        if (request != FILE_PICKER || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
        fileCallback = null;
    }

    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    private void startQrScanner() {
        Log.i(TAG,"startQrScanner activity="+getClass().getName()+" foreground="+hasWindowFocus());
        try {
            IntentIntegrator integrator=new IntentIntegrator(this).setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("扫描电脑上的 Anubis 配对二维码").setBeepEnabled(false).setOrientationLocked(false);
            Intent intent=integrator.createScanIntent();
            Log.i(TAG,"scan intent component="+intent.getComponent()+" action="+intent.getAction());
            startActivityForResult(intent,IntentIntegrator.REQUEST_CODE);
            Log.i(TAG,"startActivityForResult dispatched");
        } catch(Exception e) { Log.e(TAG,"scanner launch failed",e); Toast.makeText(this,"无法打开扫码相机："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show(); }
    }

    private class LocalClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri=request.getUrl();
            if (!"app.anubis.invalid".equals(uri.getHost())) return super.shouldInterceptRequest(view, request);
            String name=uri.getPath(); if (name==null||name.equals("/")) name="/index.html"; name=name.substring(1);
            try { InputStream in=getAssets().open(name); String ext=MimeTypeMap.getFileExtensionFromUrl(name),mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return new WebResourceResponse(mime==null?"application/octet-stream":mime,"UTF-8",in); }
            catch(Exception e){ return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new ByteArrayInputStream(new byte[0])); }
        }
        private boolean route(Uri uri) {
            Log.i(TAG,"route uri="+uri);
            if ("anubis".equals(uri.getScheme()) && "scan".equals(uri.getHost())) { startQrScanner(); return true; }
            if ("app.anubis.invalid".equals(uri.getHost())) return false;
            startActivity(new Intent(Intent.ACTION_VIEW,uri)); return true;
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return route(request.getUrl()); }
        @SuppressWarnings("deprecation") @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return route(Uri.parse(url)); }
    }

    private class AndroidBridge {
        @JavascriptInterface public void httpRequest(String id, String target, String method, String token, String body) {
            new Thread(() -> {
                HttpURLConnection connection=null;
                try {
                    URL url=new URL(target);
                    if(!"http".equals(url.getProtocol())&&!"https".equals(url.getProtocol()))throw new Exception("unsupported protocol");
                    connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(8000); connection.setReadTimeout(15000); connection.setUseCaches(false);
                    connection.setRequestMethod(method); connection.setRequestProperty("Authorization","Bearer "+token); connection.setRequestProperty("Content-Type","application/json");
                    if(body!=null&&!body.isEmpty()){connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                    int status=connection.getResponseCode(); InputStream in=status>=400?connection.getErrorStream():connection.getInputStream();
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream(); if(in!=null)try(InputStream source=in){byte[] chunk=new byte[8192];int count,total=0;while((count=source.read(chunk))!=-1){total+=count;if(total>13*1024*1024)throw new Exception("response too large");bytes.write(chunk,0,count);}}
                    finishHttp(id,status,new String(bytes.toByteArray(),StandardCharsets.UTF_8),null);
                } catch(Exception e){finishHttp(id,0,"",e.getClass().getSimpleName()+": "+e.getMessage());}
                finally {if(connection!=null)connection.disconnect();}
            },"AnubisHttp").start();
        }
        private void finishHttp(String id,int status,String body,String error){if(error==null)Log.i(TAG,"native HTTP completed status="+status);else Log.e(TAG,"native HTTP failed: "+error);runOnUiThread(()->webView.evaluateJavascript("window.onAnubisHttp("+JSONObject.quote(id)+","+status+","+JSONObject.quote(body)+","+(error==null?"null":JSONObject.quote(error))+")",null));}
        @JavascriptInterface public void scanPairing() {
            Log.i(TAG,"JavascriptInterface.scanPairing thread="+Thread.currentThread().getName());
            runOnUiThread(MainActivity.this::startQrScanner);
        }
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
