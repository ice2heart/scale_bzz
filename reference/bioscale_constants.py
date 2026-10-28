# ble/constants.py

# ====================================================================
# Conjunto 1 — Protocolo Chipsea clássico (maioria das balanças OKOK)
# ====================================================================
CHIPSEA_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
CHIPSEA_WRITE_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"   # Write — enviar comandos
CHIPSEA_NOTIFY_CHAR_UUID = "0000fff4-0000-1000-8000-00805f9b34fb"  # Notify — receber dados

# ====================================================================
# Conjunto 2 — Protocolo Chipsea mais novo (balanças ICOMON/Fitdays)
# ====================================================================
CHIPSEA_V2_SERVICE_UUID = "0000ffb0-0000-1000-8000-00805f9b34fb"
CHIPSEA_V2_WEIGHT_CHAR_UUID = "0000ffb2-0000-1000-8000-00805f9b34fb"  # Notify — peso em tempo real
CHIPSEA_V2_BIA_CHAR_UUID = "0000ffb3-0000-1000-8000-00805f9b34fb"    # Indicate — dados BIA

# Nome BLE que identifica a balança (pode haver variações)
CHIPSEA_DEVICE_NAME_PREFIXES = ["Chipsea-BLE", "OKOK", "QN-Scale", "CS Scale", "Health Scale"]

# Comandos de escrita (Conjunto 1)
CMD_GET_HISTORY = bytes([0xF2, 0x00])       # Solicitar histórico de medições
CMD_DELETE_HISTORY = bytes([0xF2, 0x01])    # Deletar histórico armazenado na balança
