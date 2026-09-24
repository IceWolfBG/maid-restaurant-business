import socket, threading, sys

LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = 8899
# Force good IPs (resolved via AliDNS), bypass DNS/SNI-level resets while keeping TLS intact.
MAP = {
    "github.com": "20.205.243.166",
}

def relay(src, dst):
    # Forward src -> dst; on clean half-close only shut the write side of dst,
    # leaving the reverse direction intact (full-duplex tunnel).
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass
    except OSError:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

def handle(client):
    remote = None
    try:
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = client.recv(4096)
            if not chunk:
                client.close(); return
            buf += chunk
        line = buf.split(b"\r\n", 1)[0].decode("latin1")
        parts = line.split()
        if len(parts) < 2 or parts[0].upper() != "CONNECT":
            client.sendall(b"HTTP/1.1 405 Method Not Allowed\r\n\r\n")
            client.close(); return
        host, _, port_s = parts[1].partition(":")
        port = int(port_s or "443")
        target_ip = MAP.get(host)
        if not target_ip:
            # generic: resolve normally
            target_ip = socket.gethostbyname(host)
        remote = socket.create_connection((target_ip, port), timeout=30)
        client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        t1 = threading.Thread(target=relay, args=(client, remote), daemon=True)
        t2 = threading.Thread(target=relay, args=(remote, client), daemon=True)
        t1.start(); t2.start()
        t1.join(); t2.join()
    except Exception as e:
        sys.stderr.write("handler error: %r\n" % e)
    finally:
        for s in (client, remote):
            try: s.close()
            except OSError: pass

def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((LISTEN_HOST, LISTEN_PORT))
    srv.listen(64)
    print("proxy listening on %s:%d" % (LISTEN_HOST, LISTEN_PORT), flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()

if __name__ == "__main__":
    main()
