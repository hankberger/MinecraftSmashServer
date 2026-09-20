"""Check the actual server-list response for every admitted client protocol."""
import argparse
import json
import socket
import struct


def varint(value):
    value &= 0xffffffff
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def read_varint(stream):
    value = 0
    for offset in range(0, 35, 7):
        chunk = stream.read(1)
        if not chunk:
            raise EOFError('Server closed the status connection')
        value |= (chunk[0] & 127) << offset
        if chunk[0] < 128:
            return value
    raise ValueError('Invalid VarInt')


def ping(host, port, protocol):
    with socket.create_connection((host, port), timeout=10) as sock:
        address = host.encode('utf-8')
        packet = b'\x00' + varint(protocol) + varint(len(address)) + address + struct.pack('>H', port) + b'\x01'
        sock.sendall(varint(len(packet)) + packet + b'\x01\x00')
        with sock.makefile('rb') as stream:
            size = read_varint(stream)
            if not 1 <= size <= 1024 * 1024:
                raise ValueError('Unreasonable status response')
            if read_varint(stream) != 0:
                raise ValueError('Expected status response')
            return json.loads(stream.read(read_varint(stream)))


def check_versions(host='127.0.0.1', port=25577):
    results = {}
    for protocol in (776, 777, 775, -1):
        version = ping(host, port, protocol)['version']
        assert version['name'] == 'Smash | Java 26.2–26.3', version
        if protocol in (776, 777):
            assert version['protocol'] == protocol, version
        else:
            # ViaVersion may replace a blocked version with the -1 sentinel.
            assert protocol == -1 or version['protocol'] != protocol, version
        results[str(protocol)] = version
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=25577)
    args = parser.parse_args()
    print(json.dumps(check_versions(args.host, args.port), indent=2))
