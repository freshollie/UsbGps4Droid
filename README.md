<img align="right" alt="App icon" src="app-icon.png" height="115px">

# UsbGps4Droid - For U-blox NEO-M8U

 freshollie氏のUsbGps4DroidをU-blox NEO-M8U向けに改造しました。

[Download latest release](../../releases)

## 変更点
・パッケージ名をcom.microntek.*に変更
  MTCManagerによるキル対策

・NMEAを読込む機能を廃止して、UBXを読込んで疑似ロケーションに反映するように変更

・対応しているUBXバイナリは以下の通り  
  UBX-HNR-PVT  
  UBX-NAV-PVT  
  UBX-NAV-ODO  
  UBX-NAV-SVINFO  
  UBX-NAV-SLAS  
  UBX-ESF-STATUS  
 
・以下の設定項目を追加  
  UBX-HNR-PVTの使用可否設定  
  UBX-CFG-HNRの設定  
  UBX-NAV-ODORESETの有効無効  
  疑似ロケーションの速度への反映有無  

・上部には以下の情報を追加  
  測位状態  
  進行方位  
  センサーの状態  
  SLASの状態  
 
・下部のNMEA出力部にはUBX-NAV-SVINFOの情報を出力するように変更  
  グラフが緑の状態の物は測位に使用しています。  
 
・Dasaita製中華ナビ（PX5)に向けてレイアウトを調整しています。  
  他の端末ではレイアウトが崩れるかもしれません。  
  
## 動作確認環境
・Android  
   Nexus7 2013(Android6.0)  
   Dasaita Android Head Unit(Android8.0)  
   
・USBGPS  
  DROTEK Ublox NEO-M8U GPS + LIS3MDL compass (XL)  

## その他
・NEO-M8U側はUBXのみ出力するように変更することを推奨します。  
・UBX-NAV-ODOはデフォルトでは無効になっているのでODOメータの表示が必要な場合は有効化してください。  
・NEO-M8U向けですが、U-bloxのM8シリーズなら使えるはずです。  
・USBの権限確認ダイアログは以下を参考にすると回避可能  
https://stackoverflow.com/questions/13726583/bypass-android-usb-host-permission-confirmation-dialog/30563253#30563253  
中華ナビのOreo機でSystemUI.apkを書き換える場合は、Malysk氏のカスタムロムを適用するとvdex、odexを気にしなくて良くなるので楽です。  
`GPL v3`
