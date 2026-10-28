import Combine
import CoreBluetooth

/// Listens for the scale's advertisement frames. OKOK/Chipsea scales broadcast the
/// weight and never accept a GATT connection, so there is nothing to connect to.
final class ScaleClient: NSObject, ObservableObject {
    @Published var weightKg: Double?
    @Published var status = "Step on the scale"
    @Published var lastPacket = ""
    @Published var savedAt: Date?
    @Published var bluetoothBlocked = false

    var onStableWeight: ((Double, Date) -> Void)?

    private var central: CBCentralManager!
    private var lastSaved: (Double, Date)?

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func rescan() {
        weightKg = nil
        lastPacket = ""
        savedAt = nil
        central.stopScan()
        start()
    }

    /// Shows the UI without hardware. Deliberately does not write to Health.
    func demo() {
        weightKg = 68.4
        lastPacket = "DEMO"
        status = "Demo reading — not saved"
    }

    private func start() {
        status = "Step on the scale"
        // every frame matters: the settled reading arrives as a repeated advertisement
        central.scanForPeripherals(withServices: nil,
                                   options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
    }

    private func handle(_ mfg: Data, name: String?) {
        let hex = mfg.map { String(format: "%02X", $0) }.joined(separator: " ")
        guard let (kg, stable) = OkokPacket.decode(mfg) else {
            if let name, Self.looksLikeScale(name) {
                print("UNPARSED \(name) \(hex)") // unknown frame variant, not an idle one
            }
            return
        }
        guard kg > 2, kg < 400 else { return } // idle frame / foot on the edge

        lastPacket = hex
        weightKg = kg
        guard stable else { return }

        // the scale repeats the settled frame for a while; save it once
        if let last = lastSaved,
           Date().timeIntervalSince(last.1) < 20,
           abs(last.0 - kg) < 0.1 { return }

        lastSaved = (kg, Date())
        savedAt = nil
        print("WEIGHT \(name ?? "unnamed") \(kg) kg raw=\(hex)")
        onStableWeight?(kg, Date())
        status = "Settled"
    }

    private static func looksLikeScale(_ name: String) -> Bool {
        let n = name.lowercased()
        return n.hasPrefix("yoda") || n.contains("chipsea") || n.contains("okok") || n == "adv"
    }
}

extension ScaleClient: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn: bluetoothBlocked = false; start()
        case .unauthorized: bluetoothBlocked = true; status = "Bluetooth permission denied"
        case .poweredOff: status = "Turn Bluetooth on"
        default: status = "Bluetooth unavailable"
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover p: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi: NSNumber) {
        guard let mfg = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data else { return }
        handle(mfg, name: p.name)
    }
}
