import asyncio
import argparse
from bleak import BleakScanner
from bleak.exc import BleakBluetoothNotAvailableError
STEPPED_ON_HEX = "00000000000024000000000000"

def get_weight_from_bytes(data: bytes) -> tuple[float, float]:
    weight_raw = int.from_bytes(data, byteorder="big") 
    weight_kg = weight_raw / 100.0 
    weight_lb = weight_kg * 2.2046226218  
    return weight_kg, weight_lb

class Sniffer:
    def __init__(self, target_mac):
        self.target_mac = target_mac.upper() if target_mac else None

    async def discover_scale(self, timeout: float = 10.0) -> str:
        """
        Scan using a callback until we see STEPPED_ON_HEX in manufacturer_data.
        Returns the MAC address of that device.
        """
        found = asyncio.Future()

        def cb(device, advertisement_data):
            # manufacturer_data is on advertisement_data (stable across Bleak versions)
            mfg = advertisement_data.manufacturer_data or {}
            for _, payload in mfg.items():
                if payload.hex() == STEPPED_ON_HEX and not found.done():
                    found.set_result(device.address)

        try:
            scanner = BleakScanner(cb)
        except BleakBluetoothNotAvailableError:
            exit("[-] Failed to find bluetooth adapter :(")

        async def get_mac_address() -> str | None:
            try:
                await scanner.start()
                try:
                    return await asyncio.wait_for(found, timeout=timeout)
                except asyncio.TimeoutError:
                    return None
            finally:
                await scanner.stop()

        while True:
            try:
                mac_address = await get_mac_address()
                if not mac_address:
                    continue
                print(f"[+] Found scale with MAC Address: {mac_address}")
                return mac_address
            except (KeyboardInterrupt, asyncio.CancelledError):
                exit("[-] CTRL+C detected!")

    def detection_callback(device, advertisement_data):
        if device.address.upper() != self.target_mac:
            return

        mfg = advertisement_data.manufacturer_data
        if not mfg:
            return

        for _, data in mfg.items():
            hex_data = data.hex()
            if hex_data == STEPPED_ON_HEX:
                print("[+] Scale has been stepped on")
                continue

            kg, lb = get_weight_from_bytes(data[0:2])
            print(f"[+] User weighed in at {kg}kgs {lb}lbs")

    async def start(self) -> None:
        if self.target_mac is None:
            print("[+] No MAC provided; attempting scale discovery... xD (go step on it)")
            self.target_mac = (await self.discover_scale()).upper()
            print(f"[+] Using target MAC: {self.target_mac}")

        def detection_callback(device, advertisement_data):
            if device.address.upper() != self.target_mac:
                return

            mfg = advertisement_data.manufacturer_data or {}
            if not mfg:
                return

            for _, data in mfg.items():
                hex_data = data.hex()

                if hex_data == STEPPED_ON_HEX:
                    print("[+] Scale has been stepped on")
                    continue

                if len(data) < 2:
                    return

                kg, lb = get_weight_from_bytes(data[0:2])
                print(f"[+] User weighed in at {kg:.2f} kg ({lb:.2f} lb)")

        scanner = BleakScanner(detection_callback)
        print("[+] Listening for scale updates... (ctrl+C to stop)")

        try:
            await scanner.start()
            while True:
                await asyncio.sleep(1)
        except (KeyboardInterrupt, asyncio.CancelledError):
            print("\n[-] Stopping scanner...")
        finally:
            await scanner.stop()
            print("[+] Scanner stopped cleanly")

def parse_args():
    parser = argparse.ArgumentParser(description="OKOK BLE scale sniffer")
    parser.add_argument(
        "--target-mac", "-t",
        help="Target scale MAC address (optional, auto-discover if omitted)",
        default=None
    )
    return parser.parse_args()


async def main():
    args = parse_args()
    sniffer = Sniffer(target_mac=args.target_mac)
    await sniffer.start()

if __name__ == "__main__":
    asyncio.run(main())
