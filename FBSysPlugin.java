package com.fezzy.festersbrowser;

import android.app.Activity;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.annotation.ActivityCallback;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "FBSys", permissions = {
  @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications"),
  @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera"),
  @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microphone"),
  @Permission(strings = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION }, alias = "location")
})
public class FBSysPlugin extends Plugin {
  private PowerManager.WakeLock wakeLock;
  @PluginMethod public void status(PluginCall call) {
    JSObject o=new JSObject();
    boolean notif=Build.VERSION.SDK_INT<33 || getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    boolean ignore=false; try { PowerManager pm=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE); ignore=pm!=null&&pm.isIgnoringBatteryOptimizations(getContext().getPackageName()); } catch(Throwable ignored) {}
    boolean defaultBrowser=false; try { if(Build.VERSION.SDK_INT>=29){RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);defaultBrowser=rm!=null&&rm.isRoleHeld(RoleManager.ROLE_BROWSER);} } catch(Throwable ignored) {}
    o.put("notifications",notif);o.put("batteryExempt",ignore);o.put("defaultBrowser",defaultBrowser);o.put("sdk",Build.VERSION.SDK_INT);
    boolean enabled=true; try { enabled=NotificationManagerCompat.from(getContext()).areNotificationsEnabled(); } catch(Throwable ignored) {}
    o.put("notificationsEnabled",enabled);
    o.put("keepAlive",FBKeepAliveService.running||(wakeLock!=null&&wakeLock.isHeld()));
    call.resolve(o);
  }
  @PluginMethod public void requestNotifications(PluginCall call) {
    try { if(Build.VERSION.SDK_INT>=33){ if(getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(getActivity(),new String[]{Manifest.permission.POST_NOTIFICATIONS},7321); } call.resolve(new JSObject().put("ok",true)); }
    catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  /** V1.23.7: the link another app asked Festers to open (no "url" when none). Read once, then cleared. */
  @PluginMethod public void launchUrl(PluginCall call) {
    JSObject o=new JSObject();
    String u=MainActivity.takeLink();
    if(u!=null)o.put("url",u);
    call.resolve(o);
  }
  /** V1.14.4: asks Android for the browser role with a result callback (the old plain startActivity never showed the dialog). Falls back to the Default apps list when Android declines to show it. */
  @PluginMethod public void chooseDefaultBrowser(PluginCall call) {
    try {
      if(Build.VERSION.SDK_INT>=29){
        RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);
        if(rm!=null&&rm.isRoleHeld(RoleManager.ROLE_BROWSER)){ call.resolve(new JSObject().put("ok",true).put("held",true).put("already",true)); return; }
        if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_BROWSER)){ startActivityForResult(call,rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER),"defaultResult"); return; }
      }
      openDefaultAppsList(call,true);
    } catch(Throwable e){ try { openDefaultAppsList(call,true); } catch(Throwable e2){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e2.getMessage()))); } }
  }
  @ActivityCallback private void defaultResult(PluginCall call, ActivityResult result) {
    if(call==null) return;
    boolean held=false;
    try { if(Build.VERSION.SDK_INT>=29){ RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE); held=rm!=null&&rm.isRoleHeld(RoleManager.ROLE_BROWSER); } } catch(Throwable ignored) {}
    if(held){ call.resolve(new JSObject().put("ok",true).put("held",true)); return; }
    try { openDefaultAppsList(call,false); } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  private void openDefaultAppsList(PluginCall call, boolean direct) {
    Intent i=new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(i);
    call.resolve(new JSObject().put("ok",true).put("held",false).put("fallback",true));
  }
  /** V1.14.4: camera, microphone, location, photos and media, as Android grants them to the app (sites still ask each time). */
  private boolean has(String p){ try { return getContext().checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED; } catch(Throwable t){ return false; } }
  private boolean mediaGranted(){
    if(Build.VERSION.SDK_INT>=33) return has("android.permission.READ_MEDIA_IMAGES")||has("android.permission.READ_MEDIA_VIDEO")||has("android.permission.READ_MEDIA_AUDIO");
    return has("android.permission.READ_EXTERNAL_STORAGE");
  }
  @PluginMethod public void permStatus(PluginCall call) {
    JSObject o=new JSObject();
    o.put("camera",has(Manifest.permission.CAMERA));
    o.put("microphone",has(Manifest.permission.RECORD_AUDIO));
    o.put("location",has(Manifest.permission.ACCESS_FINE_LOCATION)||has(Manifest.permission.ACCESS_COARSE_LOCATION));
    o.put("media",mediaGranted());
    o.put("notifications",Build.VERSION.SDK_INT<33||has(Manifest.permission.POST_NOTIFICATIONS));
    call.resolve(o);
  }
  @PluginMethod public void askPerm(PluginCall call) {
    try {
      String n=call.getString("name","");
      if("media".equals(n)){
        if(mediaGranted()){ call.resolve(new JSObject().put("ok",true).put("granted",true).put("blocked",false)); return; }
        String[] ps=Build.VERSION.SDK_INT>=33?new String[]{"android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_AUDIO"}:new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
        ActivityCompat.requestPermissions(getActivity(),ps,7340);
        call.resolve(new JSObject().put("ok",true).put("granted",false).put("pending",true).put("blocked",false)); return;
      }
      if(!("camera".equals(n)||"microphone".equals(n)||"location".equals(n))){ call.resolve(new JSObject().put("ok",false).put("error","Unknown permission")); return; }
      if(getPermissionState(n)==PermissionState.GRANTED){ call.resolve(new JSObject().put("ok",true).put("granted",true).put("blocked",false)); return; }
      requestPermissionForAlias(n,call,"permResult");
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  @PermissionCallback private void permResult(PluginCall call) {
    String n=call.getString("name","");
    boolean granted=getPermissionState(n)==PermissionState.GRANTED;
    boolean blocked=false;
    try {
      String probe="camera".equals(n)?Manifest.permission.CAMERA:"microphone".equals(n)?Manifest.permission.RECORD_AUDIO:Manifest.permission.ACCESS_FINE_LOCATION;
      blocked=!granted&&getActivity()!=null&&!ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),probe);
    } catch(Throwable ignored) {}
    call.resolve(new JSObject().put("ok",true).put("granted",granted).put("blocked",blocked));
  }
  @PluginMethod public void batterySettings(PluginCall call) {
    try { Intent i=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);call.resolve(new JSObject().put("ok",true)); }
    catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  @PluginMethod public void appSettings(PluginCall call) {
    try { Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getContext().getPackageName()));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);call.resolve(new JSObject().put("ok",true)); }
    catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  /** V1.12.2: asks for notifications and reports the answer (granted, or blocked so JS can open app settings). */
  @PluginMethod public void askNotifications(PluginCall call) {
    try {
      if(Build.VERSION.SDK_INT<33||getPermissionState("notifications")==PermissionState.GRANTED){ call.resolve(new JSObject().put("ok",true).put("granted",true).put("blocked",false)); return; }
      requestPermissionForAlias("notifications",call,"notifResult");
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  @PermissionCallback private void notifResult(PluginCall call) {
    boolean granted=getPermissionState("notifications")==PermissionState.GRANTED;
    boolean blocked=false;
    try { blocked=!granted&&getActivity()!=null&&!ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),Manifest.permission.POST_NOTIFICATIONS); } catch(Throwable ignored) {}
    call.resolve(new JSObject().put("ok",true).put("granted",granted).put("blocked",blocked));
  }
  /** V1.12.2: direct one-tap battery exemption prompt; falls back to the battery settings list. */
  @PluginMethod public void requestBatteryExemption(PluginCall call) {
    JSObject o=new JSObject();
    try {
      PowerManager pm=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
      if(pm!=null&&pm.isIgnoringBatteryOptimizations(getContext().getPackageName())){ o.put("ok",true);o.put("already",true);call.resolve(o);return; }
      try {
        Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);
        o.put("ok",true);o.put("direct",true);
      } catch(Throwable direct) {
        Intent i=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);
        o.put("ok",true);o.put("direct",false);
      }
    } catch(Throwable e){ o.put("ok",false);o.put("error",String.valueOf(e.getMessage())); }
    call.resolve(o);
  }
  /** V1.12.2: foreground service that holds the wake lock with a visible Stop notification. */
  @PluginMethod public void startKeepAlive(PluginCall call) {
    try {
      if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
      Intent i=new Intent(getContext(),FBKeepAliveService.class);i.setAction(FBKeepAliveService.ACTION_START);
      if(Build.VERSION.SDK_INT>=26)getContext().startForegroundService(i);else getContext().startService(i);
      boolean shown=true; try { shown=NotificationManagerCompat.from(getContext()).areNotificationsEnabled(); } catch(Throwable ignored) {}
      call.resolve(new JSObject().put("ok",true).put("notificationsOn",shown));
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  /** V1.14.1: turns "keep tabs alive when you leave the app" on or off. */
  @PluginMethod public void setKeepOnExit(PluginCall call) {
    FBKeepAliveService.autoEnabled=call.getBoolean("enabled",true);
    if(!FBKeepAliveService.autoEnabled)FBKeepAliveService.stopAuto(getContext());
    call.resolve(new JSObject().put("ok",true).put("enabled",FBKeepAliveService.autoEnabled));
  }
  /** V1.14.2: is an Android autofill service switched on, and did the WebView accept passkey support. */
  @PluginMethod public void autofillStatus(PluginCall call) {
    boolean sup=false,en=false;
    try { if(Build.VERSION.SDK_INT>=26){ android.view.autofill.AutofillManager am=getContext().getSystemService(android.view.autofill.AutofillManager.class); if(am!=null){sup=am.isAutofillSupported();en=am.hasEnabledAutofillServices();} } } catch(Throwable ignored) {}
    JSObject res=new JSObject().put("supported",sup).put("enabled",en).put("passkey",FBWebPlugin.passkeyMode + ". App name header: " + FBWebPlugin.headerMode + ". Browser hints: " + FBWebPlugin.hintsMode + ". Last page stop: " + FBWebPlugin.lastRenderGone);
    // V1.19.4: what a passkey app (Conduint, Bitwarden) needs to know about Festers: package, signing certificate, and whether Android granted the browser-origin permission.
    // V1.22.6: which autofill service and which passkey (credential) providers Android has switched on. hasEnabledAutofillServices() alone said No
    // when the passkey app was chosen only as a passkey provider; these Settings.Secure values name what is really selected.
    String afs="",crs="",crp="";
    try { String v=Settings.Secure.getString(getContext().getContentResolver(),"autofill_service"); if(v!=null) afs=v; } catch(Throwable ignored) {}
    try { String v=Settings.Secure.getString(getContext().getContentResolver(),"credential_service"); if(v!=null) crs=v; } catch(Throwable ignored) {}
    try { String v=Settings.Secure.getString(getContext().getContentResolver(),"credential_service_primary"); if(v!=null) crp=v; } catch(Throwable ignored) {}
    res.put("afService",afs).put("credService",crs).put("credPrimary",crp);
    if(!afs.isEmpty()&&!afs.equals("null")) res.put("enabled",true);
    res.put("pkg",getContext().getPackageName());
    res.put("cert",certSha256());
    boolean declared=false;
    try {
      android.content.pm.PackageInfo pi=getContext().getPackageManager().getPackageInfo(getContext().getPackageName(),PackageManager.GET_PERMISSIONS);
      if(pi.requestedPermissions!=null) for(String rp:pi.requestedPermissions) if(SET_ORIGIN_PERM.equals(rp)) declared=true;
    } catch(Throwable ignored) {}
    res.put("originDeclared",declared);
    res.put("originGranted",getContext().checkSelfPermission(SET_ORIGIN_PERM)==PackageManager.PERMISSION_GRANTED);
    res.put("sdk",Build.VERSION.SDK_INT);
    // V1.23.6: Conduint. Android hides other apps from Festers unless the manifest has a <queries> block (the CI scripts add it now), and
    // Settings.Secure may hide which passkey app is chosen. So report what can be known three ways: installed, able to be a provider, chosen.
    final String CND="com.fezzy.conduint";
    boolean cndInstalled=false,cndAf=false,cndCr=false; String cndVer="";
    JSArray afCap=new JSArray(), crCap=new JSArray();
    try { android.content.pm.PackageInfo cpi=getContext().getPackageManager().getPackageInfo(CND,0); cndInstalled=true; cndVer=cpi.versionName==null?"":cpi.versionName; } catch(Throwable ignored) {}
    try {
      for(android.content.pm.ResolveInfo ri:getContext().getPackageManager().queryIntentServices(new Intent("android.service.autofill.AutofillService"),0)){
        if(ri.serviceInfo!=null){ afCap.put(ri.serviceInfo.packageName); if(CND.equals(ri.serviceInfo.packageName)) cndAf=true; }
      }
    } catch(Throwable ignored) {}
    try {
      for(android.content.pm.ResolveInfo ri:getContext().getPackageManager().queryIntentServices(new Intent("android.service.credentials.CredentialProviderService"),0)){
        if(ri.serviceInfo!=null){ crCap.put(ri.serviceInfo.packageName); if(CND.equals(ri.serviceInfo.packageName)) cndCr=true; }
      }
    } catch(Throwable ignored) {}
    if(cndAf||cndCr) cndInstalled=true;
    boolean readable=!(afs.isEmpty()&&crs.isEmpty()&&crp.isEmpty());
    boolean amEn=false;
    try { if(Build.VERSION.SDK_INT>=26){ android.view.autofill.AutofillManager am2=getContext().getSystemService(android.view.autofill.AutofillManager.class); if(am2!=null) amEn=am2.hasEnabledAutofillServices(); } } catch(Throwable ignored) {}
    res.put("conduintInstalled",cndInstalled).put("conduintVersion",cndVer)
       .put("conduintAfCapable",cndAf).put("conduintCrCapable",cndCr)
       .put("conduintAfChosen",afs.contains(CND)).put("conduintCrChosen",crs.contains(CND)||crp.contains(CND))
       .put("settingsReadable",readable).put("amEnabled",amEn)
       .put("afCapable",afCap).put("crCapable",crCap);
    call.resolve(res);
  }

  /** V1.23.6: opens Conduint itself, so the person can switch its autofill and passkey options on from there. */
  @PluginMethod public void openConduint(PluginCall call) {
    try {
      Intent i=getContext().getPackageManager().getLaunchIntentForPackage("com.fezzy.conduint");
      if(i==null){ call.resolve(new JSObject().put("ok",false).put("error","Conduint is not installed")); return; }
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
      call.resolve(new JSObject().put("ok",true));
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  /** V1.19.4: Android's permission a browser needs before it may name a web origin in a Credential Manager call (Android 14+). */
  private static final String SET_ORIGIN_PERM="android.permission.CREDENTIAL_MANAGER_SET_ORIGIN";
  /** V1.19.4: SHA-256 of this app's signing certificate as AA:BB:..., the value a passkey app lists to trust Festers. Empty when it cannot be read. */
  private String certSha256() {
    try {
      PackageManager pm=getContext().getPackageManager();
      String pk=getContext().getPackageName();
      byte[] der=null;
      if(Build.VERSION.SDK_INT>=28){
        android.content.pm.SigningInfo si=pm.getPackageInfo(pk,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
        android.content.pm.Signature[] s=si.hasMultipleSigners()?si.getApkContentsSigners():si.getSigningCertificateHistory();
        if(s!=null&&s.length>0) der=s[s.length-1].toByteArray();
      }
      if(der==null) return "";
      byte[] h=java.security.MessageDigest.getInstance("SHA-256").digest(der);
      StringBuilder sb=new StringBuilder();
      for(int i=0;i<h.length;i++){ if(i>0) sb.append(':'); sb.append(String.format("%02X",h[i]&0xFF)); }
      return sb.toString();
    } catch(Throwable e){ return ""; }
  }
  @PluginMethod public void openAutofillSettings(PluginCall call) {
    try {
      Intent i;
      if(Build.VERSION.SDK_INT>=26){ i=new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE); i.setData(Uri.parse("package:android")); }
      else i=new Intent(Settings.ACTION_SETTINGS);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try { getContext().startActivity(i); }
      catch(Throwable first){ Intent j=new Intent(Settings.ACTION_SETTINGS); j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(j); call.resolve(new JSObject().put("ok",true).put("fallback",true)); return; }
      call.resolve(new JSObject().put("ok",true));
    } catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  /** V1.21.2: Android 14+ settings page where the passkey manager (credential provider) is chosen. Falls back to the autofill picker, then to Settings. */
  @PluginMethod public void openPasskeySettings(PluginCall call) {
    try {
      Intent i=null;
      if(Build.VERSION.SDK_INT>=34){ i=new Intent("android.settings.CREDENTIAL_PROVIDER"); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
      if(i!=null){
        try { getContext().startActivity(i); call.resolve(new JSObject().put("ok",true)); return; } catch(Throwable first){ /* fall through */ }
      }
      openAutofillSettings(call);
    } catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  /** V1.23.0: Android's Security settings, where a government or company root certificate is installed (Install a certificate). Festers trusts user-installed authorities. */
  @PluginMethod public void openCertSettings(PluginCall call) {
    try {
      Intent i=new Intent(Settings.ACTION_SECURITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try { getContext().startActivity(i); }
      catch(Throwable first){ Intent j=new Intent(Settings.ACTION_SETTINGS); j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(j); }
      call.resolve(new JSObject().put("ok",true));
    } catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
  /** V1.16.0: live device stats for the dashboard: battery, RAM, network, storage, CPU. Read-only. */
  private long cpuLastApp=0, cpuLastWall=0;
  @PluginMethod public void deviceStats(PluginCall call) {
    JSObject o=new JSObject();
    try { Intent b=getContext().registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      if(b!=null){ int lv=b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1),sc=b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100),st=b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1),pl=b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0);
        o.put("batPct",sc>0&&lv>=0?Math.round(lv*100f/sc):-1);
        o.put("charging",st==android.os.BatteryManager.BATTERY_STATUS_CHARGING||st==android.os.BatteryManager.BATTERY_STATUS_FULL);
        o.put("full",st==android.os.BatteryManager.BATTERY_STATUS_FULL);
        o.put("plug",pl==android.os.BatteryManager.BATTERY_PLUGGED_AC?"AC":pl==android.os.BatteryManager.BATTERY_PLUGGED_USB?"USB":pl==android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS?"Wireless":"");
        o.put("batTempC",b.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,0)/10.0); } } catch(Throwable ignored) {}
    try { android.app.ActivityManager am=(android.app.ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
      android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
      o.put("ramTotal",(double)mi.totalMem);o.put("ramFree",(double)mi.availMem);o.put("ramLow",mi.lowMemory); } catch(Throwable ignored) {}
    try { android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
      android.net.Network n=cm.getActiveNetwork(); android.net.NetworkCapabilities nc=n==null?null:cm.getNetworkCapabilities(n);
      String t="none"; if(nc!=null){ t=nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)?"vpn":nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)?"wifi":nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)?"cellular":nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)?"ethernet":"other";
        o.put("netOnline",nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED));o.put("netDownKbps",nc.getLinkDownstreamBandwidthKbps());o.put("netUpKbps",nc.getLinkUpstreamBandwidthKbps());o.put("netMetered",!nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        o.put("netVpnOn",nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)); } else { o.put("netOnline",false); }
      o.put("netType",t);
      try { android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        android.net.wifi.WifiInfo wi=wm.getConnectionInfo(); if(wi!=null){ o.put("wifiRssi",wi.getRssi());o.put("wifiMbps",wi.getLinkSpeed()); if(Build.VERSION.SDK_INT>=21)o.put("wifiGhz",wi.getFrequency()>4900?5:2.4);
          boolean loc=has(Manifest.permission.ACCESS_FINE_LOCATION); o.put("wifiLocPerm",loc);
          if(loc){ String ss=wi.getSSID(); if(ss!=null){ if(ss.length()>1&&ss.startsWith("\"")&&ss.endsWith("\""))ss=ss.substring(1,ss.length()-1); if(!ss.isEmpty()&&!"<unknown ssid>".equals(ss))o.put("wifiSsid",ss); else o.put("wifiLocOff",true); } } } } catch(Throwable ignored) {} } catch(Throwable ignored) {}
    try { android.os.StatFs sf=new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
      o.put("diskTotal",(double)sf.getTotalBytes());o.put("diskFree",(double)sf.getAvailableBytes()); } catch(Throwable ignored) {}
    try { o.put("cores",Runtime.getRuntime().availableProcessors());o.put("abi",Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"");o.put("cpuModel",Build.HARDWARE);
      double mx=0,cur=0; int n=Runtime.getRuntime().availableProcessors();
      for(int i=0;i<n;i++){ cur=Math.max(cur,readKhz("/sys/devices/system/cpu/cpu"+i+"/cpufreq/scaling_cur_freq")); mx=Math.max(mx,readKhz("/sys/devices/system/cpu/cpu"+i+"/cpufreq/cpuinfo_max_freq")); }
      if(mx>0){o.put("cpuMaxMhz",mx/1000.0);} if(cur>0){o.put("cpuCurMhz",cur/1000.0);}
      long app=android.os.Process.getElapsedCpuTime(),wall=android.os.SystemClock.elapsedRealtime();
      if(cpuLastWall>0&&wall>cpuLastWall){ o.put("appCpuPct",Math.min(100.0,Math.round((app-cpuLastApp)*1000.0/((wall-cpuLastWall)*Math.max(1,n)))/10.0)); }
      cpuLastApp=app;cpuLastWall=wall; } catch(Throwable ignored) {}
    call.resolve(o);
  }
  private double readKhz(String path){ try(java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader(path))){ String l=r.readLine(); return l==null?0:Double.parseDouble(l.trim()); } catch(Throwable t){ return 0; } }
  /** V1.18.0: one quiet status notification ("Protected 63 / VPN / DNS"), updated from the page. Off by default; enabled:false removes it. */
  private static final int STATUS_ID = 4401;
  private static final String STATUS_CH = "fb_status";
  @PluginMethod public void setStatusNote(PluginCall call) {
    try {
      android.app.NotificationManager nm=(android.app.NotificationManager)getContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if(!call.getBoolean("enabled",false)){ nm.cancel(STATUS_ID); call.resolve(new JSObject().put("ok",true).put("shown",false)); return; }
      if(Build.VERSION.SDK_INT>=33&&!has(Manifest.permission.POST_NOTIFICATIONS)){ call.resolve(new JSObject().put("ok",false).put("needsPermission",true)); return; }
      if(Build.VERSION.SDK_INT>=26&&nm.getNotificationChannel(STATUS_CH)==null){
        android.app.NotificationChannel c=new android.app.NotificationChannel(STATUS_CH,"Festers protection status",android.app.NotificationManager.IMPORTANCE_LOW);
        c.setDescription("A quiet line showing your protection score, VPN and DNS state."); c.setShowBadge(false); nm.createNotificationChannel(c);
      }
      Intent open=getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
      android.app.PendingIntent tap=open==null?null:android.app.PendingIntent.getActivity(getContext(),2,open,android.app.PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?android.app.PendingIntent.FLAG_IMMUTABLE:0));
      android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(getContext(),STATUS_CH):new android.app.Notification.Builder(getContext());
      String title=call.getString("title","Festers Browser"), text=call.getString("text","");
      b.setContentTitle(title).setContentText(text).setStyle(new android.app.Notification.BigTextStyle().bigText(text))
       .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false);
      if(Build.VERSION.SDK_INT<26)b.setPriority(android.app.Notification.PRIORITY_LOW);
      if(tap!=null)b.setContentIntent(tap);
      nm.notify(STATUS_ID,b.build());
      call.resolve(new JSObject().put("ok",true).put("shown",true));
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  /** V1.23.8: purple glow around the whole window while the vault is unlocked. Sits above the native page views too. */
  private android.animation.ObjectAnimator glowAnim;
  @PluginMethod public void setVaultGlow(final PluginCall call) {
    final boolean on = call.getBoolean("on", false);
    final Activity a = getActivity();
    if (a == null) { call.resolve(new JSObject().put("ok", false)); return; }
    a.runOnUiThread(() -> {
      try {
        android.view.ViewGroup root = (android.view.ViewGroup) a.getWindow().getDecorView();
        android.view.View old = root.findViewWithTag("fbvaultglow");
        if (on && old == null) {
          android.view.View g = new android.view.View(a);
          g.setTag("fbvaultglow");
          android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
          gd.setColor(android.graphics.Color.TRANSPARENT);
          gd.setStroke(Math.round(3 * a.getResources().getDisplayMetrics().density), 0xFFB66CFF);
          g.setBackground(gd); g.setClickable(false); g.setFocusable(false);
          root.addView(g, new android.widget.FrameLayout.LayoutParams(-1, -1));
          glowAnim = android.animation.ObjectAnimator.ofFloat(g, "alpha", 0.55f, 1f);
          glowAnim.setDuration(1500); glowAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
          glowAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE); glowAnim.start();
        } else if (!on && old != null) {
          if (glowAnim != null) { glowAnim.cancel(); glowAnim = null; }
          root.removeView(old);
        }
        call.resolve(new JSObject().put("ok", true));
      } catch (Throwable e) { call.resolve(new JSObject().put("ok", false).put("error", String.valueOf(e.getMessage()))); }
    });
  }
  /** V1.23.8: launcher badge with the number of trackers blocked today. Android only draws an icon badge for a live notification, so a quiet one is posted. */
  private static final int BADGE_ID = 4402;
  private static final String BADGE_CH = "fb_badge";
  @PluginMethod public void setBadge(PluginCall call) {
    try {
      int n = call.getInt("n", 0);
      android.app.NotificationManager nm=(android.app.NotificationManager)getContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if (n <= 0) { nm.cancel(BADGE_ID); call.resolve(new JSObject().put("ok",true).put("shown",false)); return; }
      if (Build.VERSION.SDK_INT>=33&&!has(Manifest.permission.POST_NOTIFICATIONS)) { call.resolve(new JSObject().put("ok",false).put("needsPermission",true)); return; }
      if (Build.VERSION.SDK_INT>=26&&nm.getNotificationChannel(BADGE_CH)==null) {
        android.app.NotificationChannel c=new android.app.NotificationChannel(BADGE_CH,"Blocked trackers badge",android.app.NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Shows the number of trackers Festers blocked today as a badge on the app icon."); c.setShowBadge(true); nm.createNotificationChannel(c);
      }
      Intent open=getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
      android.app.PendingIntent tap=open==null?null:android.app.PendingIntent.getActivity(getContext(),3,open,android.app.PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?android.app.PendingIntent.FLAG_IMMUTABLE:0));
      android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(getContext(),BADGE_CH):new android.app.Notification.Builder(getContext());
      b.setContentTitle(n+(n==1?" tracker blocked today":" trackers blocked today")).setContentText("Festers Shields · Bojack Security on duty")
       .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setNumber(n).setOnlyAlertOnce(true).setShowWhen(false).setAutoCancel(false);
      if (Build.VERSION.SDK_INT>=26) b.setBadgeIconType(android.app.Notification.BADGE_ICON_SMALL);
      if (Build.VERSION.SDK_INT<26) b.setPriority(android.app.Notification.PRIORITY_LOW);
      if (tap!=null) b.setContentIntent(tap);
      nm.notify(BADGE_ID,b.build());
      call.resolve(new JSObject().put("ok",true).put("shown",true).put("n",n));
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  @PluginMethod public void stopKeepAlive(PluginCall call) {
    try {
      getContext().stopService(new Intent(getContext(),FBKeepAliveService.class));
      if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
      call.resolve(new JSObject().put("ok",true));
    } catch(Throwable e){ call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage()))); }
  }
  @PluginMethod public void setWakeLock(PluginCall call) {
    boolean enable=call.getBoolean("enabled",false);
    try { PowerManager pm=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE); if(enable){if(pm==null)throw new Exception("Wake lock unavailable");if(wakeLock==null||!wakeLock.isHeld())wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"FestersBrowser:MediaPlayback");wakeLock.acquire(4*60*60*1000L);}else if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();call.resolve(new JSObject().put("ok",true).put("enabled",enable)); }
    catch(Throwable e){call.resolve(new JSObject().put("ok",false).put("error",String.valueOf(e.getMessage())));}
  }
}
