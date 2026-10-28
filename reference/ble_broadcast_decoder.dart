import 'dart:typed_data';

/// Result of decoding an OKOK manufacturer-data broadcast.
class BroadcastResult {
  final double weightKg;
  final double? impedance;
  final bool isStable;
  final bool isFinalBia;
  final String rawHex;
  final int companyId;

  const BroadcastResult({
    required this.weightKg,
    required this.impedance,
    required this.isStable,
    required this.isFinalBia,
    required this.rawHex,
    required this.companyId,
  });

  @override
  String toString() =>
      'BroadcastResult(weight=$weightKg kg, impedance=$impedance, '
      'stable=$isStable, finalBia=$isFinalBia)';
}

/// Decodes OKOK-style BLE manufacturer data broadcasts.
class BleBroadcastDecoder {
  BleBroadcastDecoder._();

  /// Decode a 13-byte OKOK broadcast payload.
  ///
  /// Returns `null` when the data does not match the expected format or
  /// the weight is outside the plausible 5–400 kg range.
  static BroadcastResult? decodeOkokBroadcast(
    int companyId,
    Uint8List byteData,
  ) {
    if (byteData.length != 13) return null;

    // Magic bytes check
    if (byteData[4] != 0x0A || byteData[5] != 0x01) return null;

    // Weight
    final weightRaw = (byteData[0] << 8) | byteData[1];
    final weightKg = weightRaw / 100.0;
    if (weightKg < 5.0 || weightKg > 400.0) return null;

    // Impedance
    final impedanceRaw = (byteData[2] << 8) | byteData[3];
    final impedance = impedanceRaw > 0 ? impedanceRaw / 10.0 : 0.0;

    final isStable = impedance > 0.0 || byteData[6] > 0;
    final isFinalBia = impedance > 0.0;

    final rawHex = byteData
        .map((b) => b.toRadixString(16).padLeft(2, '0'))
        .join();

    return BroadcastResult(
      weightKg: weightKg,
      impedance: isFinalBia ? impedance : null,
      isStable: isStable,
      isFinalBia: isFinalBia,
      rawHex: rawHex,
      companyId: companyId,
    );
  }
}
