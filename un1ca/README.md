# UN1CA files

Icons for the Side key power options (Download mode, Recovery, Restart, Restart One UI), laid out the
way UN1CA mods expect them. Copy the `burnfdskit` folder into `unica/mods/` of your UN1CA tree.

UN1CA's mod scripts copy every non-XML file under `<mod>/SecSettings.apk/res/` into the decoded
SecSettings before it is rebuilt (see `unica/mods/choidujour/customize.sh`), so the PNGs end up in
`SecSettings.apk/res/drawable-xxhdpi/` and can be referenced as `@drawable/tw_ic_do_*`.

The icons come from the original Firefds Kit (Apache License 2.0), see `../LICENSE`.
