# Firefds Kit [Baklava]

Xposed module for Samsung devices running Android 14 to Android 16 (One UI 6 - 8), built on the
modern LSPosed / libxposed API (102).

## Thanks to the original author

A huge thank you to **Shauli Bracha ([Firefds](https://github.com/Firefds))**, the creator of Firefds
Kit, who built and maintained this module for years. The original project is
[Firefds/FirefdsKit](https://github.com/Firefds/FirefdsKit). Without that work this fork would not
exist.

The original repository is no longer being updated, so this is now an independently maintained
continuation by [GuitarHeroStyles](https://github.com/GuitarHeroStyles) that brings the module to
Android 16 / One UI 8. All of the original design and most of the code are Firefds' work, thank you!

## Features

The module has the following features:

- Fake system status to Official
- Custom advanced power menu options:
    - Power off
    - Restart
    - Emergency mode
    - Recovery
    - Download
    - Data mode switch
    - Screenshot
    - Switch User (when multi user is enabled)
    - SystemUI restart
    - Flashlight
    - Screen Recorder (requires Samsung screen recorder app installed)
- Disable restart confirmation
- Enable call recording
- Replace add call button instead of call recording
- Skip tracks with volume buttons
- Enable call recording from menu
- Auto call recording
- Hide VoLTE icon in status bar
- Hide persistent USB connection notification
- Hide persistent charging notification
- Enable block phrases in messages app settings
- Enable native blur on notification panel pull down
- Enable multi user toggle
- Set max user value selector
- Show seconds in status bar clock toggle
- Show clock date on right of clock toggle
- Add date to status bar clock options
- Enable biometrics and fingerprints unlock on reboot toggle
- Add network speed menu to show network speed in the status bar
- Data icon symbol selection:
    - 4G icon: LTE instead of 4G, 4G+ instead of 4G
    - 4G+ icon: 4.5G instead of 4G+, LTE+ instead of 4G+
    - 5G icon: 5G One shaped, 5G, 5G+ shaped
- Show Data usage view in quick panel
- Double tap for sleep
- Hide NFC icon
- Disable Bluetooth toggle popup
- Disable sync toggle popup
- Disable high level brightness poup
- Hide carrier label
- Carrier label size selection
- Disable loud volume warning
- Disable volume control sound
- Disable low battery sound
- Screen timeout settings
- NFC behavior settings
- Auto MTP
- Disable camera temperature check
- Enable camera shutter sound menu
- Disable call number formatting
- Disable SMS to MMS threshold
- Force MMS connect
- Bypass exchange security (currently not working)
- Disable signature check
- Disable secure flag

## Attention

**THERE COULD BE BUGS/CRASHES/BOOTLOOPS**. Please upload the LSPosed module log (search for `FFK`)
when you encounter any issue, it is the only way to find out which hook stopped working.

Tested on:

- Galaxy A52s 5G running One UI 8 (Android 16) with the UN1CA ROM, LSPosed API 102
- Galaxy S21 FE (original project, One UI 6)

## Installation

You need a root solution with an LSPosed compatible framework that supports the modern libxposed API
(102), for example [LSPosed](https://github.com/LSPosed/LSPosed/releases) or
[Vector](https://github.com/JingMatrix/Vector). Modules built with the old API still work on these
frameworks, but this module uses the modern one.

1. Install the Firefds Kit APK.
2. Enable the module in the LSPosed manager and select its scope. The scope is fixed by the module,
   make sure **System Framework** and **Firefds Kit itself** are checked.
3. Reboot.
4. Open Firefds Kit once, so that your settings are copied to the framework.

The status card at the top of the app turns green when the module is active and has all of its
permissions.

## Known Issues

- Some features are removed on purpose. Since GravityBox has been working on Samsung devices for a
  while without much issues, only features that need special Samsung coding were implemented.
- One UI 8 removed or changed some of the code this module hooks. These features no longer work:
    - Official status through `isAlterModel` (the other status checks are still hooked)
    - Quick reply on the secure lock screen
    - Screen recorder while in a call (Samsung SmartCapture)
    - Camera temperature check bypass (the camera app obfuscates its class names on every update)
- Features that depend on Samsung internals may break again with a new One UI release.
- Double tap for sleep not working.

## External Libraries

The project uses the following libraries:

1. [libxposed](https://github.com/libxposed) - the modern Xposed API (102) and its service library
2. https://github.com/rovo89/XposedBridge and
   https://github.com/rovo89/XposedMods/tree/master/XposedLibrary - the legacy API, whose helper
   methods are re-implemented in `sb.firefds.u.firefdskit.xposed` on top of libxposed
3. Samsung framework libraries (from One UI 8) which are used for compile only

## Credits

This module wouldn't have been here without the following people:

- [Shauli Bracha (Firefds)](https://github.com/Firefds) - Creator of Firefds Kit, the original
  author of this module. Thank you!
- The people behind [LSPosed](https://github.com/LSPosed/LSPosed) for their amazing work!
- [RikkaW](https://github.com/RikkaApps) - Creator of Riru Magisk module, which provides a way to
  inject codes into zygote process
- [rovo89](https://github.com/rovo89) - Creator of the original Xposed framework APIs
- [solohsu](https://github.com/solohsu) and [MlgmXyysd](https://github.com/MlgmXyysd) - Creators of
  the EdXposed Magisk module and Installer that made all of this possible
- [C3C0](https://github.com/GravityBox) - Creator of GravityBox Xposed modules, which I learnt a lot
  from
- [Wanam](https://github.com/wanam) - Creator of the original XTouchWiz module, which this module is
  based on.
- [topjohnwu](https://github.com/topjohnwu) - Creator of Magisk
- [AbrahamGC](https://forum.xda-developers.com/member.php?u=7393522) - [For the Extended Power Menu - Pie - Odex framework Smali guide](https://forum.xda-developers.com/showpost.php?p=78910083&postcount=944)
- Big thank you to [m8980](https://forum.xda-developers.com/m/m8980.1614889)
  and [ianmacd](https://forum.xda-developers.com/m/ianmacd.7187684) for testing countless versions
  and sending xposed logs

This is a modded version of Firefds Kit by Firefds:
https://github.com/Firefds/FirefdsKit

## License

Licensed under the Apache License, Version 2.0, the same license as the original project. The
copyright notices of the original author in the source files must be kept.

## Telegram (original project)

Announcements and pre release versions of the original module - https://t.me/firefdskit
