import Foundation

/// OKOK / Chipsea scales broadcast the weight inside the BLE advertisement and never
/// accept a GATT connection. Layouts follow openScale's OkOkHandler:
///   company 0x20CA = V20, 0x11CA = V11, 0xF0FF = VF0, low byte 0xC0 = C0
enum OkokPacket {
    /// - Parameter mfg: the value of `CBAdvertisementDataManufacturerDataKey`,
    ///   which still includes the 2-byte little-endian company id.
    /// - Returns: weight in kg and whether the scale has settled.
    static func decode(_ mfg: Data) -> (kg: Double, stable: Bool)? {
        let b = [UInt8](mfg)
        guard b.count >= 2 else { return nil }
        let company = UInt16(b[0]) | UInt16(b[1]) << 8
        let p = Array(b.dropFirst(2)) // payload without the company id

        switch company {
        case 0x20CA: return v20(p)
        case 0x11CA: return v11(p)
        case 0xF0FF: return vf0(p)
        default: return company & 0xFF == 0xC0 ? c0(p) : nil
        }
    }

    // MARK: - Variants

    private static func v20(_ p: [UInt8]) -> (Double, Bool)? {
        guard p.count == 19 else { return nil }
        guard p[6] & 0x01 != 0 else { return nil } // settled flag
        guard xor([0x20] + p[0..<12]) == p[12] else { return nil }
        let divider = p[6] & 0x04 != 0 ? 100.0 : 10.0
        return (Double(u16be(p[8], p[9])) / divider, true)
    }

    private static func v11(_ p: [UInt8]) -> (Double, Bool)? {
        guard p.count == 23 else { return nil }
        guard xor([0xCA, 0x11] + p[0..<16]) == p[16] else { return nil }
        let props = p[9]
        let divider: Double = switch (props >> 1) & 0x3 {
        case 0: 10
        case 1: 1
        case 2: 100
        default: 10
        }
        let raw = u16be(p[3], p[4])
        let kg: Double = switch (props >> 3) & 0x3 {
        case 0: Double(raw) / divider            // kg
        case 1: Double(raw) / divider / 2        // jin
        case 2: Double(raw) / divider / 2.2046226218 // lb
        default: Double(raw >> 8) * 6.350293 + Double(raw & 0xFF) / divider * 0.453592 // st:lb
        }
        return (kg, true)
    }

    private static func vf0(_ p: [UInt8]) -> (Double, Bool)? {
        guard p.count >= 4 else { return nil }
        return (Double(u16be(p[3], p[2])) / 10, true)
    }

    private static func c0(_ p: [UInt8]) -> (Double, Bool)? {
        guard p.count >= 13 else { return nil }
        let attrib = p[6]
        let divider: Double = switch (attrib >> 1) & 0x3 {
        case 0: 10
        case 1: 1
        case 2: 100
        default: 10
        }
        let raw = u16be(p[0], p[1])
        let kg: Double = switch (attrib >> 3) & 0x3 {
        case 0: Double(raw) / divider
        case 1: Double(raw) / divider / 2
        case 2: Double(raw) / divider / 2.2046226218
        default: Double(p[0]) * 6.350293 + Double(p[1]) / divider * 0.453592
        }
        return (kg, attrib & 0x01 != 0)
    }

    // MARK: - Helpers

    private static func u16be(_ msb: UInt8, _ lsb: UInt8) -> UInt16 {
        UInt16(msb) << 8 | UInt16(lsb)
    }

    private static func xor(_ bytes: [UInt8]) -> UInt8 {
        bytes.reduce(0) { $0 ^ $1 }
    }
}
