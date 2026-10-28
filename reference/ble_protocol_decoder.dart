import 'dart:typed_data';

// ─────────────────────────────────────────────────────────────────────────────
// Result types
// ─────────────────────────────────────────────────────────────────────────────

/// Decoded result from Chipsea V1 GATT notifications.
class V1Result {
  final DateTime timestamp;
  final double weightKg;
  final int impedance;
  final String rawHex;

  const V1Result({
    required this.timestamp,
    required this.weightKg,
    required this.impedance,
    required this.rawHex,
  });

  @override
  String toString() =>
      'V1Result(weight=$weightKg kg, impedance=$impedance, '
      'timestamp=$timestamp)';
}

/// Decoded result from Chipsea V2 weight characteristic (FFB2).
class V2WeightResult {
  final double weightKg;
  final bool isStable;
  final String rawHex;

  const V2WeightResult({
    required this.weightKg,
    required this.isStable,
    required this.rawHex,
  });

  @override
  String toString() =>
      'V2WeightResult(weight=$weightKg kg, stable=$isStable)';
}

/// Decoded result from Chipsea V2 BIA characteristic (FFB3).
class V2BiaResult {
  final double weightKg;
  final int impedance;
  final String rawHex;

  const V2BiaResult({
    required this.weightKg,
    required this.impedance,
    required this.rawHex,
  });

  @override
  String toString() =>
      'V2BiaResult(weight=$weightKg kg, impedance=$impedance)';
}

// ─────────────────────────────────────────────────────────────────────────────
// Decoder
// ─────────────────────────────────────────────────────────────────────────────

/// Decodes GATT characteristic payloads from Chipsea V1 and V2 scales.
class BleProtocolDecoder {
  BleProtocolDecoder._();

  /// Convert bytes to a lowercase hex string.
  static String _toHex(Uint8List data) =>
      data.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  // ── Chipsea V1 ──────────────────────────────────────────────────────────

  /// Decode a Chipsea V1 notification payload (≥ 10 bytes).
  ///
  /// Returns `null` for packets shorter than 10 bytes or history-command
  /// echoes (0xF2 0x00).
  static V1Result? decodeChipseaV1(Uint8List data) {
    if (data.length < 10) return null;

    // Skip history command echo
    if (data[0] == 0xF2 && data[1] == 0x00) return null;

    // ── Timestamp ────────────────────────────────────────────────────────
    final b0 = data[0];
    final year = ((b0 & 0xF0) >> 4) + 2017;
    var month = b0 & 0x0F;
    if (month == 0) month = 1;

    var day = data[1];
    var hour = data[2];
    var minute = data[3];
    var second = data[4];

    if (day == 0) day = 1;
    if (hour > 23) hour = 0;
    if (minute > 59) minute = 0;
    if (second > 59) second = 0;

    final timestamp = DateTime(year, month, day, hour, minute, second);

    // ── Weight ───────────────────────────────────────────────────────────
    final b5 = data[5];
    final b6 = data[6];
    final weightRaw = ((b5 & 0x0F) << 8) + b6;
    final weightKg = weightRaw * 0.1;

    // ── Impedance ────────────────────────────────────────────────────────
    final b7 = data[7];
    final b8 = data[8];
    final b9 = data[9];
    final impedance = b7 + (b8 << 8) + (b9 << 16);

    return V1Result(
      timestamp: timestamp,
      weightKg: weightKg,
      impedance: impedance,
      rawHex: _toHex(data),
    );
  }

  // ── Chipsea V2 – Weight characteristic (FFB2) ──────────────────────────

  /// Decode a Chipsea V2 weight notification (≥ 10 bytes).
  static V2WeightResult? decodeChipseaV2Weight(Uint8List data) {
    if (data.length < 10) return null;

    final isStable = data[4] == 0x02;

    final b6 = data[6];
    final b7 = data[7];
    final b8 = data[8];
    final weightRaw = ((b6 & 0x03) << 16) | (b7 << 8) | b8;
    final weightKg = weightRaw / 100.0;

    return V2WeightResult(
      weightKg: weightKg,
      isStable: isStable,
      rawHex: _toHex(data),
    );
  }

  // ── Chipsea V2 – BIA characteristic (FFB3) ────────────────────────────

  /// Decode a Chipsea V2 BIA notification (≥ 10 bytes, marker 0xA3).
  static V2BiaResult? decodeChipseaV2Bia(Uint8List data) {
    if (data.length < 10) return null;
    if (data[3] != 0xA3) return null;

    final b5 = data[5];
    final b6 = data[6];
    final b7 = data[7];
    final weightRaw = ((b5 & 0x03) << 16) | (b6 << 8) | b7;
    final weightKg = weightRaw / 100.0;

    final impedance = (data[8] << 8) | data[9];

    return V2BiaResult(
      weightKg: weightKg,
      impedance: impedance,
      rawHex: _toHex(data),
    );
  }
}
