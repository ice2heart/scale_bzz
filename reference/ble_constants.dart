import 'dart:typed_data';

import 'package:flutter_blue_plus/flutter_blue_plus.dart';

/// BLE service and characteristic UUIDs for Chipsea-based scales.
class BleConstants {
  BleConstants._();

  // ── Chipsea V1 (FFF0 service) ──────────────────────────────────────────

  static final chipseaServiceUuid =
      Guid('0000fff0-0000-1000-8000-00805f9b34fb');

  static final chipseaWriteCharUuid =
      Guid('0000fff1-0000-1000-8000-00805f9b34fb');

  static final chipseaNotifyCharUuid =
      Guid('0000fff4-0000-1000-8000-00805f9b34fb');

  // ── Chipsea V2 (FFB0 service) ──────────────────────────────────────────

  static final chipseaV2ServiceUuid =
      Guid('0000ffb0-0000-1000-8000-00805f9b34fb');

  static final chipseaV2WeightCharUuid =
      Guid('0000ffb2-0000-1000-8000-00805f9b34fb');

  static final chipseaV2BiaCharUuid =
      Guid('0000ffb3-0000-1000-8000-00805f9b34fb');

  // ── Device name prefixes used during scanning ──────────────────────────

  static const List<String> chipseaDeviceNamePrefixes = [
    'Chipsea-BLE',
    'OKOK',
    'QN-Scale',
    'CS Scale',
    'Health Scale',
  ];

  // ── Commands ───────────────────────────────────────────────────────────

  static final Uint8List cmdGetHistory =
      Uint8List.fromList([0xF2, 0x00]);

  static final Uint8List cmdDeleteHistory =
      Uint8List.fromList([0xF2, 0x01]);
}
