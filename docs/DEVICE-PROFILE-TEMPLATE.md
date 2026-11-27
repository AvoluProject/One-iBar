# Device firmware profile template

Copy this template when mapping a new One UI firmware. Do not claim support until the values are verified on the device.

```yaml
profile_name: "Samsung model / One UI version"
android_api: 36
firmware_build: "exact build string"
framework: "Vector version / libxposed API"

systemui:
  process_name: "com.android.systemui"
  classloader_verified: false
  status_icon_container_id: ""
  status_icon_container_class: ""

views:
  cellular_signal_id: ""
  cellular_signal_class: ""
  wifi_id: ""
  wifi_class: ""
  battery_id: ""
  battery_class: ""
  network_type_id: ""
  clock_id: ""

hooks:
  icon_refresh_method: ""
  battery_update_method: ""
  wifi_update_method: ""
  mobile_signal_update_method: ""
  quick_settings_expand_method: ""
  quick_settings_collapse_method: ""
  keyguard_unlock_method: ""

layout_measurements_dp:
  status_bar_height: 0
  signal_width: 24
  wifi_width: 23
  battery_width: 34
  glyph_height: 18
  gap: 6
  cutout_safe_left: 0
  cutout_safe_right: 0

verification:
  portrait: false
  landscape: false
  dark_theme: false
  light_theme: false
  dual_sim: false
  low_battery: false
  charging: false
  wifi_disconnect_reconnect: false
  cellular_type_change: false
  quick_settings_expand_collapse: false
  keyguard_lock_unlock: false
  disable_restores_stock_icons: false
  systemui_restart_recovers: false
```
