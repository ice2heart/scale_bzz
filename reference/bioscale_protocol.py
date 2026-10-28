import logging
from datetime import datetime

logger = logging.getLogger(__name__)

def decode_chipsea_v1(data: bytes) -> dict:
    """
    Decodifica registros do protocolo Chipsea V1 (FFF4).
    """
    if len(data) < 10:
        logger.warning(f"V1 decodificação falhou. Tamanho inesperado: {len(data)}")
        return None
        
    if data[0] == 0xF2 and data[1] == 0x00:
        return None # Final dos dados do histórico
        
    try:
        b0 = data[0]
        year = ((b0 & 0xF0) >> 4) + 2017
        month = (b0 & 0x0F)
        # Ajuste de segurança para o mês (1 a 12)
        if month == 0: month = 1

        day = data[1]
        hour = data[2]
        minute = data[3]
        second = data[4]

        # Evita erro na conversão do timestamp (ex: device battery out)
        if day == 0: day = 1
        if hour > 23: hour = 0
        if minute > 59: minute = 0
        if second > 59: second = 0
        
        dt = datetime(year, month, day, hour, minute, second)
        
        b5 = data[5]
        b6 = data[6]
        
        scale_type = b5 & 0xF0
        weight_raw = ((b5 & 0x0F) << 8) + b6
        weight_kg = weight_raw * 0.1
        
        b7 = data[7]
        b8 = data[8]
        b9 = data[9]
        impedance = b7 + (b8 << 8) + (b9 << 16)
        
        return {
            "protocol": "v1",
            "timestamp": dt,
            "weight_kg": weight_kg,
            "impedance": impedance if impedance > 0 else None,
            "is_stable": True, # Registros finalizados são estáveis
            "scale_type": scale_type,
            "raw_hex": data.hex()
        }
    except Exception as e:
        logger.error(f"Erro ao decodificar V1 {data.hex()}: {e}")
        return None

def decode_chipsea_v2_weight(data: bytes) -> dict:
    """
    Decodifica peso em tempo real do protocolo Chipsea V2 (FFB2).
    """
    if len(data) < 10:
        return None
        
    is_stable = (data[4] == 0x02)
    
    b6 = data[6]
    b7 = data[7]
    b8 = data[8]
    
    weight_raw = ((b6 & 0x03) << 16) | (b7 << 8) | b8
    weight_kg = weight_raw / 100.0
    
    return {
        "protocol": "v2_weight",
        "weight_kg": weight_kg,
        "is_stable": is_stable,
        "raw_hex": data.hex()
    }

def decode_chipsea_v2_bia(data: bytes) -> dict:
    """
    Decodifica BIA e peso final do protocolo Chipsea V2 (FFB3).
    """
    if len(data) < 10:
        return None
        
    if data[3] != 0xA3:
        return None
        
    b5 = data[5]
    b6 = data[6]
    b7 = data[7]
    
    weight_raw = ((b5 & 0x03) << 16) | (b6 << 8) | b7
    weight_kg = weight_raw / 100.0
    
    # Impedância costuma vir nos últimos bytes (pode variar por device).
    # Vamos extraí-la como bigint das posições subsequentes de forma flexível:
    # Por exemplo byte 8 e 9.
    impedance = 0
    if len(data) >= 10:
        impedance = (data[8] << 8) | data[9] # Big endian tentativo ou little endian... 
        
    return {
        "protocol": "v2_bia",
        "weight_kg": weight_kg,
        "impedance": impedance if impedance > 0 else None,
        "is_stable": True,
        "raw_hex": data.hex()
    }
